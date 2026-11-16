package faygolover.rpstamina.gametest;

import com.mojang.authlib.GameProfile;
import faygolover.rpstamina.Config;
import faygolover.rpstamina.RpStamina;
import faygolover.rpstamina.capability.PlayerStamina;
import faygolover.rpstamina.capability.StaminaProvider;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Края состояния выносливости: запись и чтение, смерть, возврат из Края, перезаход. Страховка перед переводом
 * данных на общий механизм RP Core (план {@code _family/refactor}, задача T0.3): тесты обязаны проходить и до, и после.
 * <p>
 * В NBT по замыслу пишется только четыре поля: значение, измотанность, толерантность, откат «второго дыхания».
 * Растянутое восстановление ({@code pools}), пассивы, стимуляторы и откат допинга ({@code crashTicks}) после
 * перезахода пропадают — это записано как находка в docs/test_findings.md, здесь не проверяется.
 */
@GameTestHolder(RpStamina.MODID)
@PrefixGameTestTemplate(false)
public final class StaminaEdgeTests {
    private StaminaEdgeTests() {}

    static final String T = "platform";
    private static int counter;

    static ServerPlayer player(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), new GameProfile(UUID.randomUUID(), "rpst-edge-" + (counter++)));
        Connection conn = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, p);
        p.setGameMode(GameType.SURVIVAL);
        Vec3 pos = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.moveTo(pos.x, pos.y, pos.z, 0, 0);
        p.setOnGround(true);
        return p;
    }

    static void remove(GameTestHelper h, ServerPlayer p) {
        if (!p.isRemoved()) h.getLevel().getServer().getPlayerList().remove(p);
    }

    static void fill(PlayerStamina d) {
        d.stamina = 321.5f;
        d.exhausted = true;
        d.secondWindUntil = 777L;
        d.tolerance.clear();
        d.tolerance.put("coffee", 1.5f);
        d.tolerance.put("energy", 2.0f);
    }

    static String check(PlayerStamina d, float stamina, boolean exhausted, long wind, int tolerance) {
        if (d.stamina != stamina) return "stamina " + d.stamina + " вместо " + stamina;
        if (d.exhausted != exhausted) return "exhausted " + d.exhausted;
        if (d.secondWindUntil != wind) return "secondWindUntil " + d.secondWindUntil;
        if (d.tolerance.size() != tolerance) return "толерантность: записей " + d.tolerance.size() + " вместо " + tolerance;
        return null;
    }

    /** Записал → прочитал: четыре сохраняемых поля те же. */
    @GameTest(template = T, batch = "stamina_edge_roundtrip")
    public static void nbtRoundTrip(GameTestHelper h) {
        PlayerStamina a = new PlayerStamina();
        fill(a);
        PlayerStamina b = new PlayerStamina();
        b.load(a.save());
        String err = check(b, 321.5f, true, 777L, 2);
        if (err != null) h.fail("После записи и чтения: " + err);
        if (b.tolerance.get("coffee") != 1.5f || b.tolerance.get("energy") != 2.0f) h.fail("Значения толерантности изменились");
        if (!a.save().equals(b.save())) h.fail("Повторная запись даёт другой тег");
        h.succeed();
    }

    /** Старое сохранение без данных мода: читается без ошибок, выносливость «ещё не задана». */
    @GameTest(template = T, batch = "stamina_edge_empty")
    public static void nbtEmptyLoad(GameTestHelper h) {
        PlayerStamina d = new PlayerStamina();
        fill(d);
        d.load(new CompoundTag());
        String err = check(d, -1f, false, 0L, 0);
        if (err != null) h.fail("После чтения пустого тега: " + err);
        h.succeed();
    }

    /**
     * Смерть при reset_on_death = true (по умолчанию): выносливость заново, измотанность и толерантность сняты,
     * откат «второго дыхания» остаётся.
     */
    @GameTest(template = T, batch = "stamina_edge_death")
    public static void deathResets(GameTestHelper h) {
        if (!Config.RESET_ON_DEATH.get()) { h.fail("Тест рассчитан на reset_on_death = true"); return; }
        ServerPlayer p = player(h);
        PlayerStamina d = StaminaProvider.get(p);
        if (d == null) { h.fail("Нет данных выносливости у игрока"); return; }
        fill(d);
        ServerPlayer n = h.getLevel().getServer().getPlayerList().respawn(p, false);
        try {
            PlayerStamina nd = StaminaProvider.get(n);
            if (nd == null) { h.fail("Нет данных выносливости после возрождения"); return; }
            if (nd.stamina == 321.5f) h.fail("Выносливость пережила смерть");
            if (nd.exhausted) h.fail("Измотанность пережила смерть");
            if (!nd.tolerance.isEmpty()) h.fail("Толерантность пережила смерть");
            if (nd.secondWindUntil != 777L) h.fail("Откат «второго дыхания» сброшен: " + nd.secondWindUntil);
        } finally {
            remove(h, n);
        }
        h.succeed();
    }

    /**
     * Возврат из Края (не смерть): значение, измотанность, толерантность и откат сохраняются.
     */
    @GameTest(template = T, batch = "stamina_edge_end")
    public static void endReturnKeepsAll(GameTestHelper h) {
        ServerPlayer p = player(h);
        PlayerStamina d = StaminaProvider.get(p);
        if (d == null) { h.fail("Нет данных выносливости у игрока"); return; }
        fill(d);
        ServerPlayer n = h.getLevel().getServer().getPlayerList().respawn(p, true);
        try {
            PlayerStamina nd = StaminaProvider.get(n);
            String err = nd == null ? "нет данных" : check(nd, 321.5f, true, 777L, 2);
            if (err != null) h.fail("После возврата из Края: " + err);
        } finally {
            remove(h, n);
        }
        h.succeed();
    }

    /** Перезаход: данные игрока записаны и прочитаны, ветка мода в сохранении та же. */
    @GameTest(template = T, batch = "stamina_edge_relog")
    public static void relogKeeps(GameTestHelper h) {
        ServerPlayer p = player(h);
        ServerPlayer q = player(h);
        try {
            PlayerStamina d = StaminaProvider.get(p);
            if (d == null) { h.fail("Нет данных выносливости у игрока"); return; }
            fill(d);
            CompoundTag saved = p.saveWithoutId(new CompoundTag());
            String key = RpStamina.MODID + ":stamina";
            if (!saved.getCompound("ForgeCaps").contains(key)) h.fail("В сохранении игрока нет ветки ForgeCaps." + key);
            q.load(saved);
            PlayerStamina qd = StaminaProvider.get(q);
            if (qd == null || !d.save().equals(qd.save())) h.fail("После перезахода данные выносливости изменились");
        } finally {
            remove(h, p);
            remove(h, q);
        }
        h.succeed();
    }
}

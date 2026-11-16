package faygolover.rpstamina.capability;

import faygolover.rpcore.api.data.PlayerStore;
import faygolover.rpstamina.Config;
import faygolover.rpstamina.RpStamina;
import faygolover.rpstamina.core.StaminaManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.jetbrains.annotations.Nullable;

/**
 * Данные выносливости игрока: capability и доступ к ней. Хранение, копию при возрождении и вызовы на входе ведёт
 * RP Core ({@link PlayerStore}); имя ветки в сохранении — прежнее, {@code rpstamina:stamina}.
 * <p>
 * Не путать с {@code faygolover.rpcore.api.StaminaProvider} — это розетка выносливости RP Core
 * (её реализует {@code integration/CoreStamina}).
 */
public final class StaminaProvider {
    private StaminaProvider() {}

    public static final Capability<PlayerStamina> STAMINA = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation ID = new ResourceLocation(RpStamina.MODID, "stamina");
    /** Заполняется в {@link #register()}. */
    public static PlayerStore<PlayerStamina> STORE;

    /**
     * Описать данные для RP Core. Вызывается из конструктора мода. При смерти выносливость сбрасывается, если так
     * велит настройка {@code reset_on_death}; при возврате из Края переносится всё. На фальшивых игроков не вешается.
     */
    public static void register() {
        STORE = PlayerStore.of(ID, PlayerStamina.class, STAMINA, PlayerStamina::new)
                .save(PlayerStamina::save)
                .load(PlayerStamina::load)
                .clone((old, fresh, ctx) -> fresh.copyFrom(old, ctx.wasDeath() && Config.RESET_ON_DEATH.get()))
                // База максимума — на входе и после возрождения; при смене измерения атрибуты игрока остаются.
                .resync((sp, why) -> {
                    if (why != PlayerStore.Sync.DIMENSION) StaminaManager.applyBaseMax(sp);
                })
                .skipFakePlayers()
                .register();
    }

    /** Данные игрока или null, если capability нет (например, фейковый игрок). */
    @Nullable
    public static PlayerStamina get(Player player) {
        return player.getCapability(STAMINA).resolve().orElse(null);
    }
}

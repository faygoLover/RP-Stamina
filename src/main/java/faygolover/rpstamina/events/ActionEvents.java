package faygolover.rpstamina.events;

import faygolover.rpstamina.Config;
import faygolover.rpstamina.RpStamina;
import faygolover.rpstamina.capability.PlayerStamina;
import faygolover.rpstamina.capability.StaminaProvider;
import faygolover.rpstamina.client.ClientStamina;
import faygolover.rpstamina.core.StaminaManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Игровые события, из которых складывается механика выносливости (шина Forge). */
@Mod.EventBusSubscriber(modid = RpStamina.MODID)
public final class ActionEvents {
    private ActionEvents() {}

    // Данные игрока (capability), копию при возрождении и базу максимума на входе ведёт RP Core:
    // см. capability/StaminaProvider.register().

    /** Броню сменили — вес пересчитается при ближайшей трате на движение. */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot().getType() != EquipmentSlot.Type.ARMOR) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) s.weightDirty = true;
    }

    // ---------- тик ----------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.SERVER || !(event.player instanceof ServerPlayer player)) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s == null) return;
        if (event.phase == TickEvent.Phase.START) StaminaManager.tickStart(player, s);
        else StaminaManager.tickEnd(player, s);
    }

    // ---------- движение ----------

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.onJump(player, s);
    }

    // ---------- добыча ----------

    /** Срабатывает каждый тик добычи; на клиенте — только снижает скорость измотанного игрока (для честной анимации). */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            if (ClientStamina.active && ClientStamina.exhausted) {
                event.setNewSpeed(event.getNewSpeed() * ClientStamina.mineMult);
            }
            return;
        }
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerStamina s = StaminaProvider.get(sp);
        if (s == null || !StaminaManager.isActive(sp)) return;
        if (s.exhausted) event.setNewSpeed(event.getNewSpeed() * Config.EXHAUSTED_MINE_MULT.get().floatValue());
        event.getPosition().ifPresent(pos -> StaminaManager.onMineTick(sp, s, event.getState(), pos));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // Другие моды (защита территории и т.п.) могли отменить разрушение на более раннем приоритете —
        // в таком случае блок не сломан и платить за это не нужно.
        if (event.isCanceled()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.onBlockBroken(player, s, event.getState(), event.getPos());
    }

    // ---------- бой ----------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(AttackEntityEvent event) {
        // Аналогично: если удар отменён (например, защитой территории/PvP-зоной), стамина не тратится.
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.onMeleeHit(player, s);
    }

    /** Измотанный игрок бьёт слабее (только прямой ближний бой, не стрелы и не снаряды). */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null && s.exhausted && StaminaManager.isActive(player)) {
            event.setAmount(event.getAmount() * Config.EXHAUSTED_DAMAGE_MULT.get().floatValue());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.onShieldBlock(player, s, event.getBlockedDamage());
    }

    // ---------- предметы ----------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.onUseStart(player, s, event.getItem());
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.onUseTick(player, s, event.getItem(), event.getDuration());
    }

    /** Съел или выпил долю (RP Core: укус, глоток или целиком) — бодрость и восстановление этой долей. */
    @SubscribeEvent
    public static void onConsume(faygolover.rpcore.api.event.RpConsumeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.fraction() <= 0) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.onConsumeItem(player, s, event.stack(), (float) Math.min(1, event.fraction()));
    }
}

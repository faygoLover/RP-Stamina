package faygolover.rpstamina.events;

import faygolover.rpstamina.Config;
import faygolover.rpstamina.RpStamina;
import faygolover.rpstamina.capability.PlayerStamina;
import faygolover.rpstamina.capability.StaminaProvider;
import faygolover.rpstamina.client.ClientStamina;
import faygolover.rpstamina.core.StaminaManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
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

    // ---------- capability ----------

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player && !(event.getObject() instanceof FakePlayer)) {
            StaminaProvider provider = new StaminaProvider();
            event.addCapability(new ResourceLocation(RpStamina.MODID, "stamina"), provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        PlayerStamina old = StaminaProvider.get(event.getOriginal());
        PlayerStamina fresh = StaminaProvider.get(event.getEntity());
        if (old != null && fresh != null) {
            fresh.copyFrom(old, event.isWasDeath() && Config.RESET_ON_DEATH.get());
        }
        event.getOriginal().invalidateCaps();
    }

    // ---------- база максимума и вес брони (по событиям, без опроса по таймеру) ----------

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) StaminaManager.applyBaseMax(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) StaminaManager.applyBaseMax(player);
    }

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

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerStamina s = StaminaProvider.get(player);
        if (s != null) StaminaManager.onConsumeItem(player, s, event.getItem());
    }
}

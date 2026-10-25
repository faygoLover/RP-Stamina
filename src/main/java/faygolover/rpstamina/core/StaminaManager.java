package faygolover.rpstamina.core;

import faygolover.rpstamina.Config;
import faygolover.rpstamina.attribute.StaminaAttributes;
import faygolover.rpstamina.api.StaminaConsumeEvent;
import faygolover.rpstamina.api.StaminaRegenEvent;
import faygolover.rpstamina.capability.PlayerStamina;
import faygolover.rpstamina.data.StaminaData;
import faygolover.rpstamina.network.NetworkHandler;
import faygolover.rpstamina.network.SyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Вся серверная логика выносливости. Вызывается из обработчиков событий. */
public final class StaminaManager {
    private static final UUID SLOW_UUID = UUID.fromString("b0a7a6f4-5c7e-4c0e-9a3e-3f1d5d2f9a11");
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // Порог отправки пакета синхронизации (см. sync)
    private static final float RATE_TOLERANCE = 0.02f;      // единиц за тик
    private static final float DEVIATION_LIMIT = 0.0025f;   // доля максимума (0.25%)
    private static final int HEARTBEAT_TICKS = 40;          // редкая «подстройка» дрейфа
    private static final float HEARTBEAT_DEVIATION = 0.001f;

    private StaminaManager() {}

    // =====================================================================
    // Вспомогательное
    // =====================================================================

    /**
     * Тег игрока «выносливость считается даже в творческом»: его ставят моды семейства на время нагрузки, которую
     * нельзя накрутить бесплатно (RP Stamina Sports — тренировки), и снимают, когда выносливость восстановилась.
     */
    public static final String FORCE_TAG = "rpstamina.force";

    public static boolean isActive(ServerPlayer p) {
        if (!Config.ENABLED.get()) return false;
        if (p.isSpectator()) return !Config.SKIP_CREATIVE.get();
        return !(Config.SKIP_CREATIVE.get() && p.isCreative()) || p.getTags().contains(FORCE_TAG);
    }

    /**
     * Творческий режим с тегом {@link #FORCE_TAG}: выносливость считается, но тратит её только мод, поставивший тег
     * (трата через API), — бег, прыжки, копание и прочие встроенные траты не идут (находка RP Sports 110).
     */
    public static boolean onlyForced(ServerPlayer p) {
        return Config.SKIP_CREATIVE.get() && p.isCreative() && p.getTags().contains(FORCE_TAG);
    }

    private static double attr(LivingEntity e, Attribute a, double def) {
        AttributeInstance inst = e.getAttribute(a);
        return inst == null ? def : inst.getValue();
    }

    public static float max(ServerPlayer p) {
        return (float) Math.max(1.0, attr(p, StaminaAttributes.MAX_STAMINA.get(), Config.BASE_MAX.get()));
    }

    private static void ensureInit(ServerPlayer p, PlayerStamina s) {
        if (s.stamina < 0f) s.stamina = max(p);
    }

    // =====================================================================
    // Траты
    // =====================================================================

    public static void consume(ServerPlayer p, PlayerStamina s, Source src, double base) {
        if (onlyForced(p)) return;
        consume(p, s, src.id, base, src.category, src.armor, src.continuous);
    }

    /**
     * Общий конвейер трат: cost_multiplier × множитель категории × (вес брони) → событие → списание и пауза.
     * Атрибуты читаются напрямую: значение атрибута уже кэшируется самим Minecraft.
     */
    public static void consume(ServerPlayer p, PlayerStamina s, ResourceLocation id, double base,
                               Source.Category category, boolean armor, boolean continuous) {
        if (base <= 0 || !isActive(p)) return;
        double amount = base * attr(p, StaminaAttributes.COST_MULTIPLIER.get(), 1.0) * categoryMultiplier(p, category);
        if (armor) amount *= burdenMultiplier(p, s);
        StaminaConsumeEvent event = new StaminaConsumeEvent(p, id, (float) amount);
        if (MinecraftForge.EVENT_BUS.post(event) || event.getAmount() <= 0f) return;
        float before = s.stamina;
        spend(p, s, event.getAmount(), continuous);
        // Нагрузка качает Силу и Форму (навыки ведёт RP Perks через RP Core): сотни потраченной выносливости.
        float spent = Math.max(0f, before - s.stamina);
        if (spent > 0f) {
            net.minecraft.resources.ResourceLocation skill = switch (category) {
                case MOVE, SWIM -> faygolover.rpcore.api.RpIds.FITNESS;
                case MINE, MELEE -> faygolover.rpcore.api.RpIds.STRENGTH;
                case OTHER -> id.getPath().startsWith("shield") ? faygolover.rpcore.api.RpIds.STRENGTH : null;
            };
            if (skill != null) faygolover.rpcore.api.RpCoreAPI.practice(p, skill, spent / 100.0);
            // Мод питания (RP Culinary) считает по нагрузке калории и воду.
            faygolover.rpcore.api.RpCoreAPI.exertion(p, spent);
        }
    }

    private static double categoryMultiplier(ServerPlayer p, Source.Category category) {
        return switch (category) {
            case MOVE -> attr(p, StaminaAttributes.MOVE_COST_MULTIPLIER.get(), 1.0);
            case SWIM -> attr(p, StaminaAttributes.SWIM_COST_MULTIPLIER.get(), 1.0);
            case MINE -> attr(p, StaminaAttributes.MINE_COST_MULTIPLIER.get(), 1.0);
            case MELEE -> attr(p, StaminaAttributes.MELEE_COST_MULTIPLIER.get(), 1.0);
            case OTHER -> 1.0;
        };
    }

    /** Множитель расхода на движение из-за веса брони: 1 + clamp((вес - 5) * 0.02, 0, cap) * armor_burden. */
    public static double burdenMultiplier(ServerPlayer p, PlayerStamina s) {
        if (!Config.ARMOR_BURDEN.get()) return 1.0;
        if (s.weightDirty) {
            s.armorWeight = computeArmorWeight(p);
            s.weightDirty = false;
        }
        double penalty = Mth.clamp((s.armorWeight - 5.0) * 0.02, 0.0, Config.ARMOR_BURDEN_CAP.get());
        if (penalty <= 0.0) return 1.0;
        return 1.0 + penalty * attr(p, StaminaAttributes.ARMOR_BURDEN.get(), 1.0);
    }

    private static double computeArmorWeight(ServerPlayer p) {
        double weight = 0.0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = p.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            for (AttributeModifier m : stack.getAttributeModifiers(slot).get(Attributes.ARMOR)) {
                if (m.getOperation() == AttributeModifier.Operation.ADDITION) weight += m.getAmount();
            }
        }
        return weight;
    }

    private static Boolean perksLoaded = null;

    /**
     * Ставит базу max_stamina из конфига. Вызывается при входе, респавне и перезагрузке конфига
     * (а не по таймеру). Если установлен rpperks — база задаётся его [baseline], мы её не трогаем.
     */
    public static void applyBaseMax(ServerPlayer p) {
        if (perksLoaded == null) perksLoaded = ModList.get().isLoaded("rpperks");
        if (perksLoaded) return;
        AttributeInstance inst = p.getAttribute(StaminaAttributes.MAX_STAMINA.get());
        double base = Config.BASE_MAX.get();
        if (inst != null && Math.abs(inst.getBaseValue() - base) > 1.0E-6) inst.setBaseValue(base);
    }

    private static void spend(ServerPlayer p, PlayerStamina s, float amount, boolean continuous) {
        ensureInit(p, s);
        float applied = Math.min(amount, s.stamina);
        s.stamina -= applied;
        if (continuous) s.flow -= applied;
        s.consumedThisTick = true;
        s.regenRate = 0f;
        s.delay = Math.max(s.delay, Config.REGEN_DELAY.get());
        if (s.stamina <= 0f && !s.exhausted && !secondWind(p, s)) enterExhausted(s);
    }

    /** Особенность «Второе дыхание» (RP Perks): раз в час при нуле — сразу часть запаса. */
    private static boolean secondWind(ServerPlayer p, PlayerStamina s) {
        if (!faygolover.rpcore.api.RpCoreAPI.hasTrait(p, faygolover.rpcore.api.RpIds.SECOND_WIND)) return false;
        long now = p.level().getGameTime();
        if (now < s.secondWindUntil) return false;
        s.secondWindUntil = now + Config.SECOND_WIND_COOLDOWN.get() * 1200L;
        float gain = max(p) * Config.SECOND_WIND_FRACTION.get().floatValue();
        s.stamina = Math.min(max(p), s.stamina + gain);
        s.restoredAcc += gain;
        s.delay = 0;
        s.forceSync = true;
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.rpstamina.second_wind")
                .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        return true;
    }

    private static void enterExhausted(PlayerStamina s) {
        s.exhausted = true;
        s.slowTicks = Config.SLOW_TICKS.get();
        s.delay = Math.max(s.delay, Config.EXHAUSTED_DELAY.get());
    }

    public static void addDirect(ServerPlayer p, PlayerStamina s, float amount) {
        ensureInit(p, s);
        float before = s.stamina;
        s.stamina = Mth.clamp(s.stamina + amount, 0f, max(p));
        if (s.stamina > before) s.restoredAcc += s.stamina - before;
        if (s.stamina <= 0f && !s.exhausted) enterExhausted(s);
        s.forceSync = true;
    }

    public static void setDirect(ServerPlayer p, PlayerStamina s, float value) {
        s.stamina = Mth.clamp(value, 0f, max(p));
        if (s.stamina <= 0f && !s.exhausted) enterExhausted(s);
        else if (s.stamina >= max(p) * Config.RESUME_FRACTION.get().floatValue()) s.exhausted = false;
        s.forceSync = true;
    }

    // =====================================================================
    // События действий (вызываются из ActionEvents)
    // =====================================================================

    public static void onJump(ServerPlayer p, PlayerStamina s) {
        if (p.isSprinting()) consume(p, s, Source.SPRINT_JUMP, Config.SPRINT_JUMP_COST.get());
        else consume(p, s, Source.JUMP, Config.JUMP_COST.get());
    }

    private static double hardnessFactor(float hardness) {
        if (!Config.MINE_SCALE_BY_HARDNESS.get()) return 1.0;
        return Mth.clamp(hardness / 1.5, 0.4, 3.0);
    }

    /** Один тик добычи блока (не чаще раза в тик). */
    public static void onMineTick(ServerPlayer p, PlayerStamina s, BlockState state, BlockPos pos) {
        long now = p.level().getGameTime();
        if (s.lastMineTick == now) return;
        s.lastMineTick = now;
        float hardness = state.getDestroySpeed(p.level(), pos);
        if (hardness < 0f) return;
        double cost = Config.MINE_TICK_COST.get() * StaminaData.blockMultiplier(state) * hardnessFactor(hardness);
        consume(p, s, Source.MINE_TICK, cost);
    }

    /** Окончательное разрушение блока. */
    public static void onBlockBroken(ServerPlayer p, PlayerStamina s, BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(p.level(), pos);
        if (hardness < 0f) return;
        double cost = Config.MINE_BREAK_COST.get() * StaminaData.blockMultiplier(state) * hardnessFactor(hardness);
        consume(p, s, Source.MINE_BREAK, cost);
    }

    /** Удар по сущности. */
    public static void onMeleeHit(ServerPlayer p, PlayerStamina s) {
        ItemStack held = p.getMainHandItem();
        StaminaData.Weapon weapon = StaminaData.findWeapon(held);
        if (!StaminaData.isMelee(held, weapon)) return;
        double damage = attr(p, Attributes.ATTACK_DAMAGE, 1.0);
        double base = (Config.MELEE_HIT_COST.get() + Config.MELEE_HIT_PER_DAMAGE.get() * damage)
                * (weapon == null ? 1.0 : weapon.hitMultiplier());
        double scale = p.getAttackStrengthScale(0.5F);
        consume(p, s, Source.MELEE_HIT, base * (0.5 + 0.5 * scale));
    }

    public static void onUseStart(ServerPlayer p, PlayerStamina s, ItemStack stack) {
        StaminaData.UseCost c = StaminaData.findUseCost(stack);
        if (c != null && c.onStart() > 0f) consume(p, s, Source.ITEM_USE, c.onStart());
    }

    public static void onUseTick(ServerPlayer p, PlayerStamina s, ItemStack stack, int remaining) {
        StaminaData.UseCost c = StaminaData.findUseCost(stack);
        if (c == null) return;
        int elapsed = stack.getUseDuration() - remaining;
        float cost = elapsed >= c.holdAfterTicks() ? c.holdPerTick() : c.perTick();
        if (cost > 0f) consume(p, s, Source.ITEM_USE, cost);
    }

    public static void onShieldBlock(ServerPlayer p, PlayerStamina s, float blockedDamage) {
        consume(p, s, Source.SHIELD_BLOCK, blockedDamage * Config.SHIELD_BLOCK_PER_DAMAGE.get());
        if (s.stamina <= 0f) breakShield(p);
    }

    private static void breakShield(ServerPlayer p) {
        ItemStack use = p.getUseItem();
        if (use.isEmpty()) return;
        int cooldown = Config.SHIELD_BREAK_COOLDOWN.get();
        if (cooldown > 0) p.getCooldowns().addCooldown(use.getItem(), cooldown);
        p.stopUsingItem();
        p.level().broadcastEntityEvent(p, (byte) 30);
    }

    /** Предмет употреблён (Finish): восстановление, толерантность, откат. */
    public static void onConsumeItem(ServerPlayer p, PlayerStamina s, ItemStack stack) {
        StaminaData.Consumable c = StaminaData.findConsumable(stack);
        if (c == null) c = coreCaffeine(stack);
        if (c == null || !isActive(p)) return;

        float efficiency = 1f;
        if (c.group() != null) {
            float t = s.tolerance.getOrDefault(c.group(), 0f);
            efficiency = 1f / (1f + t);
            StaminaData.Group g = StaminaData.group(c.group());
            float gain = g == null ? 0.5f : g.gain();
            float cap = g == null ? 4f : g.max();
            s.tolerance.put(c.group(), Math.min(cap, t + gain));
        }
        if (c.instant() > 0f) {
            s.pools.add(new PlayerStamina.Pool(c.instant() * efficiency / c.instantTicks(), c.instantTicks()));
        }
        boolean boost = c.regenMultiplier() > 1f && c.regenTicks() > 0;
        if (boost) {
            // Привыкание ослабляет прибавку, а не сам откат.
            s.boosts.add(new PlayerStamina.Boost(1f + (c.regenMultiplier() - 1f) * efficiency, c.regenTicks(), c.crash()));
        }
        if (c.passivePerTick() > 0f && c.durationTicks() > 0) {
            s.passives.add(new PlayerStamina.Passive(c.passivePerTick() * efficiency, c.durationTicks(), boost ? null : c.crash()));
        } else if (c.crash() != null && !boost) {
            startCrash(s, c.crash());
        }
        if (c.cooldownTicks() > 0) p.getCooldowns().addCooldown(stack.getItem(), c.cooldownTicks());
    }

    /**
     * Кофеин из общей базы RP Core, если своего правила нет: бодрость и откат по силе (1 — чашка кофе: реген ×1.3
     * на 10 мин, потом откат). Чай слабее и короче, «следы» (кола, шоколад) — почти ничего.
     */
    @javax.annotation.Nullable
    private static StaminaData.Consumable coreCaffeine(ItemStack stack) {
        faygolover.rpcore.api.Consumables.Entry e = faygolover.rpcore.api.Consumables.forItem(stack);
        if (e == null || e.caffeine() <= 0) return null;
        float k = (float) Math.min(1.5, e.caffeine());
        int ticks = Math.round(12000 * Math.min(1f, k));
        PlayerStamina.Crash crash = new PlayerStamina.Crash(Math.round(6000 * k), Math.max(0.7f, 1f - 0.2f * k), 0);
        return new StaminaData.Consumable(null, 0f, 1, 0f, 0, "caffeine", 0, crash, 1f + 0.3f * k, ticks);
    }

    private static void startCrash(PlayerStamina s, PlayerStamina.Crash crash) {
        // Уже идущий откат не смягчается новым: берём худший множитель и самую долгую длительность
        s.crashMult = s.crashTicks > 0 ? Math.min(s.crashMult, crash.regenMultiplier()) : crash.regenMultiplier();
        s.crashTicks = Math.max(s.crashTicks, crash.durationTicks());
        s.delay = Math.max(s.delay, crash.extraDelayTicks());
    }

    // =====================================================================
    // Тик
    // =====================================================================

    public static void tickStart(ServerPlayer p, PlayerStamina s) {
        if (!isActive(p)) return;
        FoodData food = p.getFoodData();
        s.exhStart = food.getExhaustionLevel();
        s.hpStart = p.getHealth();
    }

    public static void tickEnd(ServerPlayer p, PlayerStamina s) {
        if (!isActive(p)) {
            inactiveTick(p, s);
            return;
        }

        // Ванильные траты на действия не должны съедать насыщение (кроме лечения и эффекта Голод)
        FoodData food = p.getFoodData();
        if (Config.DISABLE_VANILLA_EXHAUSTION.get()) {
            float delta = food.getExhaustionLevel() - s.exhStart;
            if (delta > 0f && p.getHealth() <= s.hpStart && !p.hasEffect(MobEffects.HUNGER)) {
                food.addExhaustion(-delta);
            }
        }

        float max = max(p);
        s.max = max;
        ensureInit(p, s);
        if (s.stamina > max) s.stamina = max;

        // Движение
        double x = p.getX(), y = p.getY(), z = p.getZ();
        double dx = 0, dy = 0, dz = 0;
        if (s.hasLastPos) {
            dx = x - s.lastX;
            dy = y - s.lastY;
            dz = z - s.lastZ;
            if (dx * dx + dy * dy + dz * dz > 100.0) dx = dy = dz = 0; // телепорт
        }
        s.lastX = x;
        s.lastY = y;
        s.lastZ = z;
        s.hasLastPos = true;
        double h2 = dx * dx + dz * dz;
        boolean movedH = h2 > 0.00002;
        boolean standing = p.onGround() && !movedH && Math.abs(dy) < 0.01 && !p.isInWater();

        // Траты на движение
        if (p.isSprinting()) {
            if (s.exhausted) p.setSprinting(false);
            else if (movedH) consume(p, s, Source.SPRINT, Config.SPRINT_COST.get());
        }
        if (p.isSwimming()) {
            consume(p, s, Source.SWIM_SPRINT, Config.SWIM_SPRINT_COST.get());
        } else if (p.isInWater() && !p.onGround() && (movedH || dy > 0.02)) {
            consume(p, s, Source.SWIM, Config.SWIM_COST.get());
        }
        if (p.onClimbable() && !p.onGround() && dy > 0.02) {
            consume(p, s, Source.CLIMB, Config.LADDER_COST.get());
        }
        if (p.isBlocking()) {
            consume(p, s, Source.SHIELD_HOLD, Config.SHIELD_HOLD_COST.get());
            if (s.stamina <= 0f) breakShield(p);
        }

        tickTolerance(s);
        tickPassiveHunger(food);
        if (p.tickCount % 100 == 0) applyAge(p);
        tickItems(s, max, food);
        tickRegen(p, s, food, max, standing);

        // Выход из измотанности
        if (s.exhausted && s.stamina >= max * Config.RESUME_FRACTION.get().floatValue()) {
            s.exhausted = false;
        }

        // Замедление после падения до нуля
        boolean slow = s.slowTicks > 0;
        if (slow) s.slowTicks--;
        applySlow(p, slow);

        s.consumedThisTick = false;
        // Скорость плавных изменений за этот тик: клиент будет экстраполировать по ней между пакетами
        s.rate = s.flow;
        s.flow = 0f;
        // Сколько восстановилось — раз в секунду в RP Core (мод питания берёт за это калории).
        if (p.tickCount % 20 == 0 && s.restoredAcc > 0f) {
            faygolover.rpcore.api.RpCoreAPI.staminaRestored(p, s.restoredAcc);
            s.restoredAcc = 0f;
        }
        sync(p, s, true);
    }

    private static final UUID AGE_MAX_UUID = UUID.fromString("c3d4e5f6-0718-4a29-b3c4-d5e6f7081921");
    private static final UUID AGE_REGEN_UUID = UUID.fromString("c3d4e5f6-0718-4a29-b3c4-d5e6f7081922");

    /** Возраст из анкеты тела (RP Core): запас и восстановление меняются вместе. */
    private static void applyAge(ServerPlayer p) {
        double f = Config.AGE_AFFECTS.get() ? faygolover.rpcore.api.BodyNorms.ageStamina(faygolover.rpcore.api.RpCoreAPI.profile(p).age()) : 1.0;
        setModifier(p, StaminaAttributes.MAX_STAMINA.get(), AGE_MAX_UUID, "rpstamina age", f - 1.0);
        setModifier(p, StaminaAttributes.REGEN_MULTIPLIER.get(), AGE_REGEN_UUID, "rpstamina age", f - 1.0);
    }

    private static void setModifier(ServerPlayer p, Attribute a, UUID id, String name, double amount) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(id);
        if (old != null && Math.abs(old.getAmount() - amount) < 1e-9) return;
        if (old != null) inst.removeModifier(id);
        if (Math.abs(amount) > 1e-9) inst.addTransientModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_BASE));
    }

    /** Без мода питания голод тратится и просто от жизни (ванильная трата на действия выключена). */
    private static void tickPassiveHunger(FoodData food) {
        if (faygolover.rpcore.api.RpCoreAPI.nutritionHandles(faygolover.rpcore.api.RpIds.FOOD_HUNGER)) return;
        double perMinute = Config.PASSIVE_FOOD_EXHAUSTION.get();
        if (perMinute > 0) food.addExhaustion((float) (perMinute / 1200.0));
    }

    private static void tickTolerance(PlayerStamina s) {
        if (s.tolerance.isEmpty()) return;
        Iterator<Map.Entry<String, Float>> it = s.tolerance.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Float> e = it.next();
            StaminaData.Group g = StaminaData.group(e.getKey());
            float decay = g == null ? 0.0003f : g.decayPerTick();
            float v = e.getValue() - decay;
            if (v <= 0f) it.remove();
            else e.setValue(v);
        }
    }

    /** Восстановление от предметов: пулы «мгновенного» и пассивные эффекты. Тратит голод как и обычное. */
    private static void tickItems(PlayerStamina s, float max, FoodData food) {
        float bonus = 0f;
        if (!s.pools.isEmpty()) {
            Iterator<PlayerStamina.Pool> it = s.pools.iterator();
            while (it.hasNext()) {
                PlayerStamina.Pool pool = it.next();
                bonus += pool.perTick;
                if (--pool.ticks <= 0) it.remove();
            }
        }
        if (!s.passives.isEmpty()) {
            Iterator<PlayerStamina.Passive> it = s.passives.iterator();
            while (it.hasNext()) {
                PlayerStamina.Passive a = it.next();
                bonus += a.perTick;
                if (--a.ticks <= 0) {
                    it.remove();
                    if (a.crash != null) startCrash(s, a.crash);
                }
            }
        }
        if (!s.boosts.isEmpty()) {
            Iterator<PlayerStamina.Boost> it = s.boosts.iterator();
            while (it.hasNext()) {
                PlayerStamina.Boost b = it.next();
                if (--b.ticks <= 0) {
                    it.remove();
                    if (b.crash != null) startCrash(s, b.crash);
                }
            }
        }
        if (s.crashTicks > 0 && --s.crashTicks == 0) s.crashMult = 1f;
        if (bonus > 0f) {
            float before = s.stamina;
            s.stamina = Math.min(max, s.stamina + bonus);
            float gained = s.stamina - before;
            s.flow += gained;
            s.restoredAcc += gained;
            chargeFood(food, gained);
        }
    }

    /** Восстановление выносливости расходует голод/насыщение через ванильную усталость (exhaustion). */
    private static void chargeFood(FoodData food, float restored) {
        if (restored <= 0f || !Config.REGEN_COSTS_FOOD.get()) return;
        // Голод ведёт мод питания (RP Culinary): он берёт калории за восстановленное (событие RP Core), ванильная сытость не тратится.
        if (faygolover.rpcore.api.RpCoreAPI.nutritionHandles(faygolover.rpcore.api.RpIds.FOOD_HUNGER)) return;
        double perHundred = Config.FOOD_COST_PER_100.get();
        if (perHundred > 0.0) food.addExhaustion((float) (restored * perHundred / 100.0));
    }

    /** Естественное восстановление: пауза → плавное нарастание скорости → зависимость от голода. */
    private static void tickRegen(ServerPlayer p, PlayerStamina s, FoodData food, float max, boolean standing) {
        if (s.consumedThisTick) {
            s.regenRate = 0f;
            return;
        }
        if (s.delay > 0) {
            s.delay--;
            s.regenRate = 0f;
            return;
        }
        if (s.stamina >= max) return;

        double target;
        if (p.isSleeping()) target = Config.REGEN_SLEEP.get();
        else if (p.isPassenger()) target = Config.REGEN_SIT.get()
                * (faygolover.rpcore.api.RpCoreAPI.hasTrait(p, faygolover.rpcore.api.RpIds.QUICK_REST) ? Config.QUICK_REST_MULT.get() : 1.0);
        else if (standing) target = p.isCrouching() ? Math.max(Config.REGEN_CROUCH.get(), Config.REGEN_STAND.get()) : Config.REGEN_STAND.get();
        else target = Config.REGEN_WALK.get();

        float t = (float) target;
        if (s.regenRate > t) {
            s.regenRate = t; // снижение мгновенное
        } else {
            s.regenRate = Math.min(t, s.regenRate + t / Math.max(1, Config.REGEN_RAMP_TICKS.get()));
        }

        // Голод и жажда мешают восстановлению (берётся худшее). Еда сама по себе его не ускоряет — только стимуляторы.
        int stage;
        var nutrition = faygolover.rpcore.api.RpCoreAPI.nutrition();
        if (nutrition != null && nutrition.handles(faygolover.rpcore.api.RpIds.FOOD_HUNGER)) {
            stage = nutrition.hungerStage(p);
            if (nutrition.handles(faygolover.rpcore.api.RpIds.FOOD_THIRST)) stage = Math.max(stage, nutrition.thirstStage(p));
        } else {
            int level = food.getFoodLevel();
            stage = level <= 0 ? 3 : level < Config.LOW_FOOD_THRESHOLD.get() ? 2 : level < Config.HUNGRY_THRESHOLD.get() ? 1 : 0;
        }
        float foodMult = switch (stage) {
            case 0 -> 1f;
            case 1 -> Config.HUNGRY_MULT.get().floatValue();
            case 2 -> Config.LOW_FOOD_MULT.get().floatValue();
            default -> Config.ZERO_FOOD_MULT.get().floatValue();
        };
        float boost = 1f;
        for (PlayerStamina.Boost b : s.boosts) boost *= b.multiplier;

        float amount = s.regenRate
                * (float) attr(p, StaminaAttributes.REGEN_MULTIPLIER.get(), 1.0)
                * foodMult
                * boost
                * (s.crashTicks > 0 ? s.crashMult : 1f);
        if (amount <= 0f) return;

        StaminaRegenEvent event = new StaminaRegenEvent(p, amount);
        if (MinecraftForge.EVENT_BUS.post(event) || event.getAmount() <= 0f) return;

        float actual = Math.min(event.getAmount(), max - s.stamina);
        s.stamina += actual;
        s.flow += actual;
        s.restoredAcc += actual;
        chargeFood(food, actual);
    }

    private static void applySlow(ServerPlayer p, boolean slow) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(SLOW_UUID) != null;
        if (slow && !has) {
            speed.addTransientModifier(new AttributeModifier(SLOW_UUID, "rpstamina exhausted",
                    -Config.SLOW_PERCENT.get(), AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!slow && has) {
            speed.removeModifier(SLOW_UUID);
        }
    }

    private static void inactiveTick(ServerPlayer p, PlayerStamina s) {
        float max = max(p);
        s.max = max;
        s.stamina = max;
        s.exhausted = false;
        s.slowTicks = 0;
        s.regenRate = 0f;
        s.delay = 0;
        s.consumedThisTick = false;
        s.flow = 0f;
        s.rate = 0f;
        applySlow(p, false);
        sync(p, s, false);
    }

    // =====================================================================
    // Время RP (/rptime sleep|skip, RP Core)
    // =====================================================================

    /**
     * Прошло {@code ticks} тиков без игры: привыкание к предметам спадает, действие предметов и откат кончаются.
     * Во сне выносливость полная; бодрствуя — восстанавливается как стоя.
     */
    public static void elapse(ServerPlayer p, PlayerStamina s, long ticks, boolean sleeping) {
        if (ticks <= 0 || !isActive(p)) return;
        ensureInit(p, s);
        Iterator<Map.Entry<String, Float>> tol = s.tolerance.entrySet().iterator();
        while (tol.hasNext()) {
            Map.Entry<String, Float> e = tol.next();
            StaminaData.Group g = StaminaData.group(e.getKey());
            float v = (float) (e.getValue() - (g == null ? 0.0003f : g.decayPerTick()) * ticks);
            if (v <= 0f) tol.remove();
            else e.setValue(v);
        }
        s.pools.clear();
        for (PlayerStamina.Passive a : s.passives) {
            // Действие кончилось — откат успел пройти на остаток времени.
            if (a.crash == null || a.ticks > ticks) continue;
            long left = a.crash.durationTicks() - (ticks - a.ticks);
            if (left > 0) startCrash(s, new PlayerStamina.Crash((int) left, a.crash.regenMultiplier(), 0));
        }
        s.passives.removeIf(a -> a.ticks <= ticks);
        for (PlayerStamina.Passive a : s.passives) a.ticks -= (int) ticks;
        for (PlayerStamina.Boost b : s.boosts) {
            if (b.crash == null || b.ticks > ticks) continue;
            long left = b.crash.durationTicks() - (ticks - b.ticks);
            if (left > 0) startCrash(s, new PlayerStamina.Crash((int) left, b.crash.regenMultiplier(), 0));
        }
        s.boosts.removeIf(b -> b.ticks <= ticks);
        for (PlayerStamina.Boost b : s.boosts) b.ticks -= (int) ticks;
        if (s.crashTicks > 0) {
            s.crashTicks = (int) Math.max(0, s.crashTicks - ticks);
            if (s.crashTicks == 0) s.crashMult = 1f;
        }
        s.delay = 0;
        s.slowTicks = 0;
        s.regenRate = 0f;
        float max = max(p);
        float before = s.stamina;
        s.stamina = sleeping ? max : (float) Math.min(max, s.stamina + Config.REGEN_STAND.get() * ticks);
        if (s.stamina > before) s.restoredAcc += s.stamina - before;
        if (s.stamina >= max * Config.RESUME_FRACTION.get().floatValue()) s.exhausted = false;
        s.forceSync = true;
    }

    // =====================================================================
    // Синхронизация с клиентом
    // =====================================================================

    private static void sync(ServerPlayer p, PlayerStamina s, boolean active) {
        int flags = 0;
        if (s.exhausted) flags |= SyncPacket.FLAG_EXHAUSTED;
        if (active) flags |= SyncPacket.FLAG_ACTIVE;
        if (Config.DISABLE_VANILLA_SPRINT_LIMIT.get()) flags |= SyncPacket.FLAG_NO_FOOD_SPRINT_LIMIT;
        byte b = (byte) flags;

        // Клиент сам продвигает значение каждый тик: last + rate * тиков. Повторяем его расчёт
        // и шлём пакет только когда реальное значение разошлось с этим предсказанием.
        int since = p.tickCount - s.lastSyncTick;
        float predicted = Mth.clamp(s.lastSentStamina + s.lastSentRate * since, 0f, s.max);
        float deviation = Math.abs(s.stamina - predicted);
        boolean send = s.forceSync
                || b != s.lastFlags
                || s.max != s.lastSentMax
                || Math.abs(s.rate - s.lastSentRate) > RATE_TOLERANCE
                || deviation > s.max * DEVIATION_LIMIT
                || (since >= HEARTBEAT_TICKS && deviation > s.max * HEARTBEAT_DEVIATION);
        if (!send) return;

        s.forceSync = false;
        s.lastFlags = b;
        s.lastSentStamina = s.stamina;
        s.lastSentMax = s.max;
        s.lastSentRate = s.rate;
        s.lastSyncTick = p.tickCount;
        byte mineMultPercent = (byte) Math.round(Config.EXHAUSTED_MINE_MULT.get() * 100.0);
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new SyncPacket(s.stamina, s.max, s.rate, b, mineMultPercent));
    }
}

package faygolover.rpstamina;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Серверный конфиг механики. Все траты — в единицах стамины (максимум по умолчанию 1000).
 * Значения «за тик» — 20 тиков = 1 секунда.
 */
public final class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ---- general ----
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue SKIP_CREATIVE;
    public static final ForgeConfigSpec.BooleanValue RESET_ON_DEATH;
    public static final ForgeConfigSpec.DoubleValue BASE_MAX;

    // ---- costs ----
    public static final ForgeConfigSpec.DoubleValue SPRINT_COST;
    public static final ForgeConfigSpec.DoubleValue JUMP_COST;
    public static final ForgeConfigSpec.DoubleValue SPRINT_JUMP_COST;
    public static final ForgeConfigSpec.DoubleValue SWIM_COST;
    public static final ForgeConfigSpec.DoubleValue SWIM_SPRINT_COST;
    public static final ForgeConfigSpec.DoubleValue LADDER_COST;
    public static final ForgeConfigSpec.DoubleValue MINE_TICK_COST;
    public static final ForgeConfigSpec.DoubleValue MINE_BREAK_COST;
    public static final ForgeConfigSpec.BooleanValue MINE_SCALE_BY_HARDNESS;
    public static final ForgeConfigSpec.DoubleValue MELEE_HIT_COST;
    public static final ForgeConfigSpec.DoubleValue MELEE_HIT_PER_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SHIELD_HOLD_COST;
    public static final ForgeConfigSpec.DoubleValue SHIELD_BLOCK_PER_DAMAGE;
    public static final ForgeConfigSpec.IntValue SHIELD_BREAK_COOLDOWN;

    // ---- regen ----
    public static final ForgeConfigSpec.IntValue REGEN_DELAY;
    public static final ForgeConfigSpec.IntValue EXHAUSTED_DELAY;
    public static final ForgeConfigSpec.IntValue REGEN_RAMP_TICKS;
    public static final ForgeConfigSpec.DoubleValue REGEN_WALK;
    public static final ForgeConfigSpec.DoubleValue REGEN_STAND;
    public static final ForgeConfigSpec.DoubleValue REGEN_CROUCH;
    public static final ForgeConfigSpec.DoubleValue REGEN_SIT;
    public static final ForgeConfigSpec.DoubleValue REGEN_SLEEP;
    public static final ForgeConfigSpec.DoubleValue QUICK_REST_MULT;
    public static final ForgeConfigSpec.DoubleValue SECOND_WIND_FRACTION;
    public static final ForgeConfigSpec.IntValue SECOND_WIND_COOLDOWN;
    public static final ForgeConfigSpec.BooleanValue AGE_AFFECTS;

    // ---- hunger ----
    public static final ForgeConfigSpec.IntValue HUNGRY_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue HUNGRY_MULT;
    public static final ForgeConfigSpec.DoubleValue PASSIVE_FOOD_EXHAUSTION;
    public static final ForgeConfigSpec.IntValue LOW_FOOD_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue LOW_FOOD_MULT;
    public static final ForgeConfigSpec.DoubleValue ZERO_FOOD_MULT;
    public static final ForgeConfigSpec.BooleanValue REGEN_COSTS_FOOD;
    public static final ForgeConfigSpec.DoubleValue FOOD_COST_PER_100;
    public static final ForgeConfigSpec.BooleanValue DISABLE_VANILLA_SPRINT_LIMIT;
    public static final ForgeConfigSpec.BooleanValue DISABLE_VANILLA_EXHAUSTION;

    // ---- exhausted ----
    public static final ForgeConfigSpec.DoubleValue RESUME_FRACTION;
    public static final ForgeConfigSpec.IntValue SLOW_TICKS;
    public static final ForgeConfigSpec.DoubleValue SLOW_PERCENT;
    public static final ForgeConfigSpec.DoubleValue EXHAUSTED_MINE_MULT;
    public static final ForgeConfigSpec.DoubleValue EXHAUSTED_DAMAGE_MULT;

    // ---- armor ----
    public static final ForgeConfigSpec.BooleanValue ARMOR_BURDEN;
    public static final ForgeConfigSpec.DoubleValue ARMOR_BURDEN_CAP;

    private static ForgeConfigSpec.DoubleValue d(String key, double def, double min, double max, String comment) {
        return B.comment(comment).defineInRange(key, def, min, max);
    }

    private static ForgeConfigSpec.IntValue i(String key, int def, int min, int max, String comment) {
        return B.comment(comment).defineInRange(key, def, min, max);
    }

    private static ForgeConfigSpec.BooleanValue bool(String key, boolean def, String comment) {
        return B.comment(comment).define(key, def);
    }

    static {
        B.push("general");
        ENABLED = bool("enabled", true, "Включить механику выносливости.");
        SKIP_CREATIVE = bool("skip_creative_and_spectator", true, "Не применять выносливость в креативе и режиме наблюдателя.");
        RESET_ON_DEATH = bool("reset_on_death", true, "После смерти выносливость полная.");
        BASE_MAX = d("base_max_stamina", 1000.0, 1.0, 100000.0,
                "Базовый максимум выносливости. Перки/эффекты меняют его через атрибут rpstamina:max_stamina.");
        B.pop();

        B.push("costs");
        SPRINT_COST = d("sprint_per_tick", 0.8, 0, 1000, "Бег, за тик (при 1000 максимума ≈ минута непрерывного бега).");
        JUMP_COST = d("jump", 12.0, 0, 1000, "Прыжок.");
        SPRINT_JUMP_COST = d("sprint_jump", 20.0, 0, 1000, "Прыжок во время бега.");
        SWIM_COST = d("swim_per_tick", 0.3, 0, 1000, "Плавание (движение в воде), за тик.");
        SWIM_SPRINT_COST = d("swim_sprint_per_tick", 0.7, 0, 1000, "Быстрое плавание (поза плавания), за тик.");
        LADDER_COST = d("climb_per_tick", 0.4, 0, 1000, "Подъём по лестнице/лианам (только вверх), за тик.");
        MINE_TICK_COST = d("mine_per_tick", 0.25, 0, 1000, "Добыча блока: за каждый тик процесса.");
        MINE_BREAK_COST = d("mine_break", 3.0, 0, 1000, "Добыча блока: за окончательное разрушение.");
        MINE_SCALE_BY_HARDNESS = bool("mine_scale_by_hardness", true,
                "Масштабировать траты добычи по твёрдости блока (земля дешевле, обсидиан дороже). Дополнительно можно менять в датапаке (blocks).");
        MELEE_HIT_COST = d("melee_hit", 6.0, 0, 1000, "Удар в ближнем бою: базовая цена.");
        MELEE_HIT_PER_DAMAGE = d("melee_hit_per_damage", 1.5, 0, 1000, "Удар в ближнем бою: доплата за единицу урона оружия.");
        SHIELD_HOLD_COST = d("shield_hold_per_tick", 0.05, 0, 1000, "Щит поднят, за тик.");
        SHIELD_BLOCK_PER_DAMAGE = d("shield_block_per_damage", 4.0, 0, 1000, "Щит: цена за единицу заблокированного урона.");
        SHIELD_BREAK_COOLDOWN = i("shield_break_cooldown_ticks", 60, 0, 1200,
                "Если выносливость закончилась при блоке — щит опускается и уходит на такой откат.");
        B.pop();

        B.push("regen");
        REGEN_DELAY = i("delay_ticks", 60, 0, 1200, "Пауза перед восстановлением после обычной траты.");
        EXHAUSTED_DELAY = i("exhausted_delay_ticks", 160, 0, 2400, "Пауза перед восстановлением, если выносливость упала до нуля.");
        REGEN_RAMP_TICKS = i("ramp_ticks", 100, 1, 2400,
                "За сколько тиков скорость восстановления нарастает от нуля до целевой. Снижается скорость мгновенно.");
        REGEN_WALK = d("walk_per_tick", 0.04, 0, 100, "Скорость восстановления при ходьбе/действиях (без трат), за тик.");
        REGEN_STAND = d("stand_per_tick", 0.12, 0, 100, "Стоит на месте (можно поворачивать камеру).");
        REGEN_CROUCH = d("crouch_per_tick", 0.20, 0, 100, "Стоит на месте и присел. Поставь равным stand_per_tick, чтобы отключить бонус.");
        REGEN_SIT = d("sit_per_tick", 0.30, 0, 100, "Сидит на транспорте/сидении (и на тренажёре).");
        QUICK_REST_MULT = d("quick_rest_sit_multiplier", 2.0, 1, 10, "Особенность «Быстрый отдых»: во сколько раз быстрее восстанавливается сидя.");
        SECOND_WIND_FRACTION = d("second_wind_fraction", 0.5, 0, 1, "Особенность «Второе дыхание»: какую долю запаса вернуть, когда выносливость упала до нуля.");
        SECOND_WIND_COOLDOWN = i("second_wind_cooldown_minutes", 60, 1, 1440, "«Второе дыхание»: не чаще раза в N минут.");
        AGE_AFFECTS = bool("age_affects_stamina", true, "Возраст из анкеты тела (RP Core) меняет запас и восстановление: до 18 ×0,9, 31–45 ×0,95, 46–60 ×0,85, 61+ ×0,7.");
        REGEN_SLEEP = d("sleep_per_tick", 0.50, 0, 100, "Лежит в кровати.");
        B.pop();

        B.push("hunger");
        HUNGRY_THRESHOLD = i("hungry_food_threshold", 10, 0, 20,
                "Без RP Culinary: ниже этого уровня голода (в очках, 2 очка = 1 иконка) — «голоден», восстановление медленнее.");
        HUNGRY_MULT = d("hungry_multiplier", 0.75, 0, 1,
                "Голоден (с RP Culinary — стадия «голоден» или «хочет пить»): множитель восстановления. Еда сама по себе восстановление не ускоряет.");
        LOW_FOOD_THRESHOLD = i("low_food_threshold", 4, 0, 20, "Без RP Culinary: ниже этого уровня голода — «очень голоден».");
        LOW_FOOD_MULT = d("low_food_multiplier", 0.5, 0, 1, "Очень голоден или очень хочет пить: множитель восстановления.");
        ZERO_FOOD_MULT = d("zero_food_multiplier", 0.25, 0, 1, "Голодает или обезвожен: множитель восстановления.");
        PASSIVE_FOOD_EXHAUSTION = d("passive_food_exhaustion_per_minute", 0.8, 0, 100,
                "Без RP Culinary: голод тратится и просто от жизни — столько ванильной «усталости» в минуту (4.0 = одно очко голода). 0 — выключить.");
        REGEN_COSTS_FOOD = bool("regen_costs_food", true,
                "Восстановление выносливости тратит голод/насыщение: и естественное, и от предметов (считается по фактически восстановленному). Выключи вместе с disable_vanilla_action_exhaustion, чтобы получить обычную ванильную механику голода.");
        FOOD_COST_PER_100 = d("regen_food_cost_per_100", 1.0, 0, 100,
                "Сколько ванильной «усталости» (exhaustion) уходит на каждые 100 восстановленной выносливости (4.0 = одно очко голода/насыщения). Действует, если regen_costs_food включён.");
        DISABLE_VANILLA_SPRINT_LIMIT = bool("disable_vanilla_sprint_hunger_limit", true,
                "Убрать ванильный запрет бега при голоде <= 6. Работает через клиент (нужен мод на клиенте).");
        DISABLE_VANILLA_EXHAUSTION = bool("disable_vanilla_action_exhaustion", true,
                "Действия (бег, прыжки, добыча, удары...) больше не тратят ванильное насыщение: голод расходует только восстановление стамины.");
        B.pop();

        B.push("exhausted");
        RESUME_FRACTION = d("resume_fraction", 0.10, 0, 1,
                "После падения до нуля бег недоступен, пока выносливость не поднимется до этой доли максимума.");
        SLOW_TICKS = i("slow_ticks", 100, 0, 2400, "Как долго после падения до нуля длится замедление.");
        SLOW_PERCENT = d("slow_percent", 0.30, 0, 0.95, "Величина замедления скорости (0.3 = -30%).");
        EXHAUSTED_MINE_MULT = d("mine_speed_multiplier", 0.5, 0.05, 1, "Множитель скорости добычи, пока игрок измотан.");
        EXHAUSTED_DAMAGE_MULT = d("melee_damage_multiplier", 0.5, 0.05, 1, "Множитель урона в ближнем бою, пока игрок измотан.");
        B.pop();

        B.push("armor");
        ARMOR_BURDEN = bool("burden_enabled", true,
                "Тяжёлая броня увеличивает расход выносливости на движение (бег, прыжки, плавание, лестницы). Вес считается по защите надетых частей.");
        ARMOR_BURDEN_CAP = d("burden_cap", 0.5, 0, 5, "Максимальная надбавка к расходу (0.5 = не более +50%), даже для очень тяжёлой модовой брони.");
        B.pop();

        SPEC = B.build();
    }

    private Config() {}
}

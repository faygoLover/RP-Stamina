package faygolover.rpstamina.core;

import faygolover.rpstamina.RpStamina;
import net.minecraft.resources.ResourceLocation;

/**
 * Источники трат (id попадает в StaminaConsumeEvent).
 * category — какой атрибут-множитель применяется; armor — учитывать ли вес брони;
 * continuous — плавный расход каждый тик (входит в «скорость» для предсказания на клиенте),
 * иначе — разовая трата, которую клиент узнаёт из пакета синхронизации.
 */
public enum Source {
    SPRINT("sprint", Category.MOVE, true, true),
    JUMP("jump", Category.MOVE, true, false),
    SPRINT_JUMP("sprint_jump", Category.MOVE, true, false),
    CLIMB("climb", Category.MOVE, true, true),
    SWIM("swim", Category.SWIM, true, true),
    SWIM_SPRINT("swim_sprint", Category.SWIM, true, true),
    MINE_TICK("mine_tick", Category.MINE, false, true),
    MINE_BREAK("mine_break", Category.MINE, false, false),
    MELEE_HIT("melee_hit", Category.MELEE, false, false),
    ITEM_USE("item_use", Category.OTHER, false, false),
    SHIELD_HOLD("shield_hold", Category.OTHER, false, true),
    SHIELD_BLOCK("shield_block", Category.OTHER, false, false);

    public enum Category { MOVE, SWIM, MINE, MELEE, OTHER }

    public final ResourceLocation id;
    public final Category category;
    public final boolean armor;
    public final boolean continuous;

    Source(String path, Category category, boolean armor, boolean continuous) {
        this.id = new ResourceLocation(RpStamina.MODID, path);
        this.category = category;
        this.armor = armor;
        this.continuous = continuous;
    }
}

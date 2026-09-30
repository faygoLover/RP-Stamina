package faygolover.rpstamina.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Состояние выносливости игрока (только сервер). В NBT сохраняются текущее значение,
 * флаг измотанности и толерантность к группам предметов; остальное — временное.
 */
public class PlayerStamina {
    /** Отрицательное значение = ещё не инициализировано (будет заполнено до максимума). */
    public float stamina = -1f;
    public float max = 1000f;
    public boolean exhausted = false;

    // ---- восстановление ----
    public int delay = 0;
    public float regenRate = 0f;
    public boolean consumedThisTick = false;
    public int slowTicks = 0;

    // ---- предметы ----
    public final List<Pool> pools = new ArrayList<>();
    public final List<Passive> passives = new ArrayList<>();
    public int crashTicks = 0;
    public float crashMult = 1f;
    public final Map<String, Float> tolerance = new HashMap<>();

    // ---- измерения/кэши ----
    public boolean hasLastPos = false;
    public double lastX, lastY, lastZ;
    public float exhStart;
    public float hpStart;
    public long lastMineTick = -1;
    /** Суммарная защита надетой брони; пересчитывается только после смены экипировки. */
    public double armorWeight = 0.0;
    public boolean weightDirty = true;

    // ---- скорость изменения для предсказания на клиенте ----
    /** Плавные (непрерывные) изменения за текущий тик: расход бега/добычи и восстановление. */
    public float flow = 0f;
    /** Скорость за прошлый тик (единиц за тик), уходит клиенту. */
    public float rate = 0f;

    // ---- синхронизация ----
    public boolean forceSync = true;
    public float lastSentStamina = -1f;
    public float lastSentMax = -1f;
    public float lastSentRate = 0f;
    public byte lastFlags = -1;
    public int lastSyncTick = -100;

    /** Мгновенное восстановление, растянутое на несколько тиков. */
    public static final class Pool {
        public final float perTick;
        public int ticks;

        public Pool(float perTick, int ticks) {
            this.perTick = perTick;
            this.ticks = ticks;
        }
    }

    /** Пассивная регенерация от предмета на время его действия. */
    public static final class Passive {
        public final float perTick;
        public int ticks;
        public final Crash crash;

        public Passive(float perTick, int ticks, Crash crash) {
            this.perTick = perTick;
            this.ticks = ticks;
            this.crash = crash;
        }
    }

    /** Откат после допинга (опционально, задаётся в датапаке). */
    public record Crash(int durationTicks, float regenMultiplier, int extraDelayTicks) {}

    /** Копирование при клонировании игрока (смерть / возврат из Энда). */
    public void copyFrom(PlayerStamina old, boolean death) {
        if (death) {
            this.stamina = -1f;
            this.exhausted = false;
        } else {
            this.stamina = old.stamina;
            this.exhausted = old.exhausted;
            this.tolerance.putAll(old.tolerance);
        }
        this.forceSync = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("stamina", stamina);
        tag.putBoolean("exhausted", exhausted);
        CompoundTag tol = new CompoundTag();
        tolerance.forEach(tol::putFloat);
        tag.put("tolerance", tol);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.stamina = tag.contains("stamina") ? tag.getFloat("stamina") : -1f;
        this.exhausted = tag.getBoolean("exhausted");
        this.tolerance.clear();
        CompoundTag tol = tag.getCompound("tolerance");
        for (String key : tol.getAllKeys()) {
            if (tol.contains(key, Tag.TAG_ANY_NUMERIC)) tolerance.put(key, tol.getFloat(key));
        }
    }
}

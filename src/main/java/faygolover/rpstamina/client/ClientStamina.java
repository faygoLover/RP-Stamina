package faygolover.rpstamina.client;

/**
 * Клиентская копия состояния. Между пакетами значение продвигается по скорости (tick()),
 * а при отрисовке интерполируется между прошлым и текущим тиком (lerp()).
 * Класс намеренно «чистый» — без обращений к клиентским классам Minecraft, поэтому
 * на него безопасно ссылаться из общего кода.
 */
public final class ClientStamina {
    public static float stamina = 0f;
    /** Значение на прошлом тике — для плавной интерполяции. */
    public static float previous = 0f;
    public static float max = 1000f;
    /** Единиц за тик: плавный расход (отрицательная) или восстановление (положительная). */
    public static float rate = 0f;
    public static boolean exhausted = false;
    public static boolean active = false;
    public static boolean noFoodSprintLimit = false;
    public static float mineMult = 0.5f;

    private ClientStamina() {}

    public static void update(float stamina, float max, float rate, boolean exhausted, boolean active,
                              boolean noFoodSprintLimit, float mineMult) {
        boolean becameActive = !ClientStamina.active && active;
        ClientStamina.stamina = stamina;
        ClientStamina.max = max;
        ClientStamina.rate = rate;
        ClientStamina.exhausted = exhausted;
        ClientStamina.active = active;
        ClientStamina.noFoodSprintLimit = noFoodSprintLimit;
        ClientStamina.mineMult = mineMult;
        if (becameActive) previous = stamina;
    }

    public static void reset() {
        stamina = 0f;
        previous = 0f;
        rate = 0f;
        exhausted = false;
        active = false;
        noFoodSprintLimit = false;
    }

    /** Раз в клиентский тик: сохраняем прошлое значение и продвигаем текущее по скорости. */
    public static void tick() {
        previous = stamina;
        if (active && rate != 0f) {
            stamina = Math.max(0f, Math.min(max, stamina + rate));
        }
    }

    /** Значение для отрисовки: плавно между прошлым и текущим тиком. */
    public static float lerp(float partialTick) {
        return previous + (stamina - previous) * partialTick;
    }
}

package faygolover.rpstamina.data;

import faygolover.rpstamina.capability.PlayerStamina;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Загруженные данные датапака (только сервер). Списки неизменяемы и подменяются целиком при перезагрузке. */
public final class StaminaData {
    private StaminaData() {}

    /**
     * Восстанавливающий предмет. Еда сама по себе выносливость не восстанавливает (решение 13.10.2026): сюда — вода
     * (слабо) и стимуляторы ({@code regenMultiplier} — множитель восстановления на {@code regenTicks}, потом откат).
     */
    public record Consumable(ItemMatcher matcher, float instant, int instantTicks, float passivePerTick,
                             int durationTicks, @Nullable String group, int cooldownTicks,
                             @Nullable PlayerStamina.Crash crash, float regenMultiplier, int regenTicks) {}

    public record UseCost(ItemMatcher matcher, float onStart, float perTick, int holdAfterTicks, float holdPerTick) {}

    public record Weapon(ItemMatcher matcher, float hitMultiplier, boolean ignore) {}

    public record BlockDef(BlockMatcher matcher, float multiplier) {}

    public record Group(float gain, float decayPerTick, float max) {}

    private static volatile List<Consumable> consumables = List.of();
    private static volatile List<UseCost> useCosts = List.of();
    private static volatile List<Weapon> weapons = List.of();
    private static volatile List<BlockDef> blocks = List.of();
    private static volatile Map<String, Group> groups = Map.of();

    public static void setConsumables(List<Consumable> v) { consumables = List.copyOf(v); }
    public static void setUseCosts(List<UseCost> v) { useCosts = List.copyOf(v); }
    public static void setWeapons(List<Weapon> v) { weapons = List.copyOf(v); }
    public static void setBlocks(List<BlockDef> v) { blocks = List.copyOf(v); }
    public static void setGroups(Map<String, Group> v) { groups = Map.copyOf(v); }

    public static int consumableCount() { return consumables.size(); }

    @Nullable
    public static Consumable findConsumable(ItemStack stack) {
        for (Consumable c : consumables) if (c.matcher().test(stack)) return c;
        return null;
    }

    @Nullable
    public static UseCost findUseCost(ItemStack stack) {
        for (UseCost c : useCosts) if (c.matcher().test(stack)) return c;
        return null;
    }

    @Nullable
    public static Weapon findWeapon(ItemStack stack) {
        for (Weapon w : weapons) if (w.matcher().test(stack)) return w;
        return null;
    }

    @Nullable
    public static Group group(String name) {
        return groups.get(name);
    }

    public static Map<String, Group> allGroups() {
        return groups;
    }

    public static float blockMultiplier(BlockState state) {
        for (BlockDef b : blocks) if (b.matcher().test(state)) return b.multiplier();
        return 1f;
    }

    /**
     * Считается ли предмет «оружием ближнего боя». Пустая рука — да. Предметы без атрибута урона
     * (лук, огнестрел из других модов, еда, блоки) — нет, чтобы их клики не тратили стамину,
     * если только предмет явно не указан в weapons.
     */
    public static boolean isMelee(ItemStack stack, @Nullable Weapon weapon) {
        if (stack.isEmpty()) return true;
        if (weapon != null) return !weapon.ignore();
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof DiggerItem || stack.getItem() instanceof TridentItem) {
            return true;
        }
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }
}

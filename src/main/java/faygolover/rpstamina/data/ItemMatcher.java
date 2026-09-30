package faygolover.rpstamina.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Совпадение предмета: по id или по тегу (#namespace:tag), опционально с частичным сравнением NBT. */
public record ItemMatcher(@Nullable Item item, @Nullable TagKey<Item> tag, @Nullable CompoundTag nbt) {
    public boolean test(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (item != null && !stack.is(item)) return false;
        if (tag != null && !stack.is(tag)) return false;
        if (nbt != null) {
            CompoundTag t = stack.getTag();
            if (t == null || !NbtUtils.compareNbt(nbt, t, true)) return false;
        }
        return true;
    }
}

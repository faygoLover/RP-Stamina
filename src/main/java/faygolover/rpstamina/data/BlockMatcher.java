package faygolover.rpstamina.data;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Совпадение блока: по id или по тегу (#namespace:tag). */
public record BlockMatcher(@Nullable Block block, @Nullable TagKey<Block> tag) {
    public boolean test(BlockState state) {
        if (block != null) return state.is(block);
        return tag != null && state.is(tag);
    }
}

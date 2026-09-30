package faygolover.rpstamina.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaminaProvider implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<PlayerStamina> STAMINA = CapabilityManager.get(new CapabilityToken<>() {});

    private final PlayerStamina data = new PlayerStamina();
    private final LazyOptional<PlayerStamina> optional = LazyOptional.of(() -> data);

    /** Данные игрока или null, если capability нет (например, фейковый игрок). */
    @Nullable
    public static PlayerStamina get(Player player) {
        return player.getCapability(STAMINA).resolve().orElse(null);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == STAMINA ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.save();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.load(nbt);
    }

    public void invalidate() {
        optional.invalidate();
    }
}

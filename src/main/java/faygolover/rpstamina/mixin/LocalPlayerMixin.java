package faygolover.rpstamina.mixin;

import faygolover.rpstamina.client.ClientStamina;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Можно ли бежать (ревью 11.10, п. 9): вместо ванильного «голод больше 6» решает выносливость — измотан нельзя,
 * а голод не мешает, если так в конфиге сервера ({@code disable_vanilla_sprint_hunger_limit}). Ванильная проверка
 * зовётся и при старте бега, и пока бежишь — измотанный останавливается. Сытость при этом никто не подменяет:
 * ею владеет мод питания (правило RP Core). Верхом и в полёте — как в ванили.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("HEAD"), cancellable = true)
    private void rpstamina$canSprint(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (!ClientStamina.active || self.isPassenger() || self.getAbilities().mayfly) return;
        if (ClientStamina.exhausted) cir.setReturnValue(false);
        else if (ClientStamina.noFoodSprintLimit) cir.setReturnValue(true);
    }
}

package faygolover.rpstamina.client;

import faygolover.rpstamina.RpStamina;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Клиентская логика (шина Forge): продвижение полоски между пакетами. Можно ли бежать — решает вставка
 * {@link faygolover.rpstamina.mixin.LocalPlayerMixin}; редактор HUD — общий, {@code /rphud} (RP Core).
 */
@Mod.EventBusSubscriber(modid = RpStamina.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // Продвигаем значение по скорости, пришедшей с сервера (пауза в одиночной игре не считается)
        if (mc.player != null && !mc.isPaused()) ClientStamina.tick();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStamina.reset();
    }
}

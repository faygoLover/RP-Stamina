package faygolover.rpstamina.client;

import faygolover.rpstamina.RpStamina;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Клиентские события мод-шины: регистрация оверлея. */
@Mod.EventBusSubscriber(modid = RpStamina.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    /** Полоска — элемент общего HUD RP Core (один редактор для всех модов семейства). */
    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> faygolover.rpcore.client.hud.HudRegistry.register(new StaminaHud.Element()));
    }
}

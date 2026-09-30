package faygolover.rpstamina.events;

import faygolover.rpstamina.Config;
import faygolover.rpstamina.RpStamina;
import faygolover.rpstamina.core.StaminaManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** События мод-шины: перечитывание конфига на работающем сервере. */
@Mod.EventBusSubscriber(modid = RpStamina.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LifecycleEvents {
    private LifecycleEvents() {}

    /** Конфиг изменили на лету: базу максимума переприменяем сразу у всех игроков онлайн. */
    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != Config.SPEC) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> server.getPlayerList().getPlayers().forEach(StaminaManager::applyBaseMax));
        }
    }
}

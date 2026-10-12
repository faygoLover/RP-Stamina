package faygolover.rpstamina.events;

import faygolover.rpcore.api.event.RpTimeSkipEvent;
import faygolover.rpstamina.RpStamina;
import faygolover.rpstamina.capability.PlayerStamina;
import faygolover.rpstamina.capability.StaminaProvider;
import faygolover.rpstamina.core.StaminaManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Время RP (RP Core, {@code /rptime}). «Стоп» выносливость не трогает: бег — здесь и сейчас, а не время тела.
 * Прокрутка «всего» — выносливость восстанавливается (во сне — полностью), предметы и откаты отходят.
 */
@Mod.EventBusSubscriber(modid = RpStamina.MODID)
public final class TimeEvents {
    private TimeEvents() {}

    @SubscribeEvent
    public static void onTimeSkip(RpTimeSkipEvent e) {
        if (e.scope() != RpTimeSkipEvent.Scope.ALL || !(e.getEntity() instanceof ServerPlayer p)) return;
        PlayerStamina s = StaminaProvider.get(p);
        if (s != null) StaminaManager.elapse(p, s, e.seconds() * 20, e.sleeping());
    }
}

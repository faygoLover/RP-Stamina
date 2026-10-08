package faygolover.rpstamina.client;

import faygolover.rpstamina.RpStamina;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.Commands;
import net.minecraft.world.food.FoodData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Клиентская логика (шина Forge): команда редактора, снятие ванильного лимита спринта. */
@Mod.EventBusSubscriber(modid = RpStamina.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean openEditor = false;
    private static int savedFood = -1;

    private ClientEvents() {}

    /** /staminahud — открыть редактор положения полоски. */
    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("staminahud").executes(c -> {
            openEditor = true;
            return 1;
        }));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // Продвигаем значение по скорости, пришедшей с сервера (пауза в одиночной игре не считается)
        if (mc.player != null && !mc.isPaused()) ClientStamina.tick();
        if (!openEditor) return;
        // Чат закрывается после выполнения команды — открываем редактор уже после него
        if (mc.player != null && !(mc.screen instanceof ChatScreen)) {
            openEditor = false;
            mc.setScreen(new faygolover.rpcore.client.hud.HudEditorScreen());
        }
    }

    /**
     * Ванильный запрет бега «голод <= 6» проверяется клиентом по уровню голода. На время тика игрока
     * подменяем уровень: 20 — бег разрешён, 0 — бег запрещён (пока игрок измотан). После тика возвращаем как было.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side != LogicalSide.CLIENT) return;
        Minecraft mc = Minecraft.getInstance();
        if (event.player != mc.player) return;
        FoodData food = event.player.getFoodData();

        if (event.phase == TickEvent.Phase.START) {
            if (ClientStamina.active && ClientStamina.noFoodSprintLimit) {
                savedFood = food.getFoodLevel();
                food.setFoodLevel(ClientStamina.exhausted ? 0 : 20);
            } else {
                savedFood = -1;
            }
        } else if (savedFood >= 0) {
            food.setFoodLevel(savedFood);
            savedFood = -1;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStamina.reset();
        savedFood = -1;
    }
}

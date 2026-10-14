package faygolover.rpstamina.client;

import com.mojang.blaze3d.vertex.PoseStack;
import faygolover.rpstamina.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Минималистичная полоска выносливости. Рисуется в общем HUD RP Core ({@link Element}); двигать — в общем
 * редакторе ({@code /rphud}). Первое положение берётся из старого клиентского конфига.
 */
public final class StaminaHud {
    private static float fade = 0f;

    private StaminaHud() {}

    /** Элемент общего HUD RP Core. */
    public static final class Element implements faygolover.rpcore.client.hud.HudElement {
        private static final net.minecraft.resources.ResourceLocation ID = new net.minecraft.resources.ResourceLocation(faygolover.rpstamina.RpStamina.MODID, "bar");

        @Override
        public net.minecraft.resources.ResourceLocation id() {
            return ID;
        }

        @Override
        public net.minecraft.network.chat.Component title() {
            return net.minecraft.network.chat.Component.translatable("hud.rpstamina.bar");
        }

        @Override
        public int width() {
            return ClientConfig.WIDTH.get();
        }

        @Override
        public int height() {
            return ClientConfig.HEIGHT.get();
        }

        @Override
        public faygolover.rpcore.client.hud.Anchor defaultAnchor() {
            return faygolover.rpcore.client.hud.Anchor.parse(ClientConfig.ANCHOR.get().name(), faygolover.rpcore.client.hud.Anchor.BOTTOM_CENTER);
        }

        @Override
        public int defaultX() {
            return ClientConfig.OFFSET_X.get();
        }

        @Override
        public int defaultY() {
            return ClientConfig.OFFSET_Y.get();
        }

        @Override
        public boolean defaultVisible() {
            return ClientConfig.ENABLED.get();
        }

        @Override
        public void render(GuiGraphics g, float partialTick, boolean preview) {
            if (preview) {
                drawBar(g, 0, 0, width(), height(), 0.7f, false, 1f);
                return;
            }
            StaminaHud.render(g, partialTick);
        }
    }

    /** Полоска в локальных координатах элемента (0,0 — левый верхний угол). */
    static void render(GuiGraphics g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientStamina.active || mc.player == null) return;

        float value = ClientStamina.lerp(partialTick);
        float frac = ClientStamina.max > 0f ? Mth.clamp(value / ClientStamina.max, 0f, 1f) : 0f;
        boolean full = frac >= 0.995f && !ClientStamina.exhausted;
        float target = (ClientConfig.HIDE_WHEN_FULL.get() && full) ? 0f : 1f;
        fade += (target - fade) * 0.08f;
        if (fade < 0.03f) return;

        drawBar(g, 0, 0, ClientConfig.WIDTH.get(), ClientConfig.HEIGHT.get(), frac, ClientStamina.exhausted, fade);
    }

    public static void drawBar(GuiGraphics g, int x, int y, int w, int h, float frac, boolean exhausted, float alpha) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, withAlpha(0xFF0A0A0A, 0.75f * alpha));
        g.fill(x, y, x + w, y + h, withAlpha(0xFF2E2E2E, alpha));

        // Ширина заливки дробная: рисуем единичный прямоугольник, растянутый матрицей, поэтому край
        // двигается по реальным пикселям экрана, а не рывками по пикселям GUI.
        float fillWidth = w * frac;
        if (fillWidth <= 0.01f) return;

        int color;
        float a = alpha;
        if (exhausted) {
            color = ClientConfig.parseColor(ClientConfig.COLOR_EXHAUSTED.get(), 0xFFC0392B);
            if ((System.currentTimeMillis() / 300L) % 2L == 0L) a *= 0.55f;
        } else if (frac < 0.25f) {
            color = ClientConfig.parseColor(ClientConfig.COLOR_LOW.get(), 0xFFE07A24);
        } else {
            color = ClientConfig.parseColor(ClientConfig.COLOR_NORMAL.get(), 0xFFD9B23A);
        }

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0);
        pose.scale(fillWidth, 1.0f, 1.0f);
        g.fill(0, 0, 1, h, withAlpha(color, a));
        pose.popPose();
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Mth.clamp((int) (((argb >>> 24) & 0xFF) * alpha), 0, 255);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}

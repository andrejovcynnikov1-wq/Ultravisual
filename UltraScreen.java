package com.example.esp;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** HUD-меню: затемнённый экран, название по центру и FPS рядом (обновляется каждый кадр). */
public class UltraScreen extends Screen {

    public UltraScreen() {
        super(Text.literal("ULTRA VISUAL"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // затемнение/размытие фона

        Text title = Text.literal("ULTRA VISUAL").formatted(Formatting.BOLD, Formatting.AQUA);
        int fps = MinecraftClient.getInstance().getCurrentFps();
        Formatting fc = fps >= 100 ? Formatting.GREEN : fps >= 50 ? Formatting.YELLOW : Formatting.RED;
        Text fpsText = Text.literal(fps + " FPS").formatted(Formatting.BOLD, fc);

        int gap = 14;
        int tw = textRenderer.getWidth(title);
        int fw = textRenderer.getWidth(fpsText);
        int total = tw + gap + fw;

        int padX = 16, padY = 10;
        int x = (width - total) / 2;
        int y = height / 2 - 4;

        // тёмная серая панель
        ctx.fill(x - padX, y - padY, x + total + padX, y + 8 + padY, 0xC0202020);
        // акцентная линия сверху
        ctx.fill(x - padX, y - padY, x + total + padX, y - padY + 2, 0xFF00E5FF);

        ctx.drawTextWithShadow(textRenderer, title, x, y, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, fpsText, x + tw + gap, y, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

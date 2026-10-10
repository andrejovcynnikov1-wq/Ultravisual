package com.example.esp;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class EspMod implements ClientModInitializer {

    static final double RANGE = 400.0;

    // Пайплайны без теста глубины = видно сквозь блоки
    static final RenderPipeline ESP_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("ultravisual", "pipeline/esp"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build()
    );

    static final RenderPipeline HAT_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of("ultravisual", "pipeline/hat"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build()
    );

    // Слои создаются лениво при первом кадре (RenderLayer.of открыт через access widener)
    static RenderLayer espLayer;
    static RenderLayer hatLayer;

    static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ultravisual.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of("ultravisual", "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new UltraScreen());
            }
        });

        WorldRenderEvents.END_MAIN.register(this::render);
    }

    private static void initLayers() {
        if (espLayer == null) {
            espLayer = RenderLayer.of("ultravisual_box", RenderSetup.builder(ESP_PIPELINE).build());
            hatLayer = RenderLayer.of("ultravisual_hat", RenderSetup.builder(HAT_PIPELINE).build());
        }
    }

    private void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack ms = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();
        if (ms == null || consumers == null) return;

        initLayers();

        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        float td = mc.getRenderTickCounter().getTickProgress(false);

        PlayerEntity target = findTarget(mc, td);

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (mc.player.squaredDistanceTo(p) > RANGE * RANGE) continue;

            double x = MathHelper.lerp(td, p.lastRenderX, p.getX()) - cam.x;
            double y = MathHelper.lerp(td, p.lastRenderY, p.getY()) - cam.y;
            double z = MathHelper.lerp(td, p.lastRenderZ, p.getZ()) - cam.z;

            float w = p.getWidth() / 2f, h = p.getHeight();
            boolean isTarget = p == target;

            drawBox(ms, consumers.getBuffer(espLayer),
                    x - w, y, z - w, x + w, y + h, z + w,
                    isTarget ? 1f : 0.2f, isTarget ? 0.2f : 0.8f, isTarget ? 0.2f : 1f, 0.35f);

            if (isTarget) drawChinaHat(ms, consumers.getBuffer(hatLayer), x, y + h + 0.05, z);
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(espLayer);
            imm.draw(hatLayer);
        }
    }

    /** Игрок, ближе всего к направлению взгляда */
    private PlayerEntity findTarget(MinecraftClient mc, float td) {
        Vec3d eye = mc.player.getCameraPosVec(td);
        Vec3d look = mc.player.getRotationVec(td).normalize();
        PlayerEntity best = null;
        double bestDot = 0.98;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || mc.player.squaredDistanceTo(p) > RANGE * RANGE) continue;
            Vec3d center = new Vec3d(p.getX(), p.getY() + p.getHeight() / 2, p.getZ());
            Vec3d dir = center.subtract(eye).normalize();
            double dot = look.dotProduct(dir);
            if (dot > bestDot) { bestDot = dot; best = p; }
        }
        return best;
    }

    /** Китайская шляпа: радужный конус */
    private void drawChinaHat(MatrixStack ms, VertexConsumer vc, double x, double y, double z) {
        Matrix4f m = ms.peek().getPositionMatrix();
        double radius = 0.55, height = 0.3;
        int seg = 48;
        float time = (System.currentTimeMillis() % 4000) / 4000f;

        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2 * i / seg;
            double a2 = Math.PI * 2 * (i + 1) / seg;
            Color c1 = Color.getHSBColor((time + (float) i / seg) % 1f, 0.8f, 1f);
            Color c2 = Color.getHSBColor((time + (float) (i + 1) / seg) % 1f, 0.8f, 1f);

            vc.vertex(m, (float) x, (float) (y + height), (float) z).color(255, 255, 255, 230);
            vc.vertex(m, (float) (x + Math.cos(a1) * radius), (float) y, (float) (z + Math.sin(a1) * radius))
              .color(c1.getRed(), c1.getGreen(), c1.getBlue(), 150);
            vc.vertex(m, (float) (x + Math.cos(a2) * radius), (float) y, (float) (z + Math.sin(a2) * radius))
              .color(c2.getRed(), c2.getGreen(), c2.getBlue(), 150);
        }
    }

    private void drawBox(MatrixStack ms, VertexConsumer vc,
                         double x1d, double y1d, double z1d, double x2d, double y2d, double z2d,
                         float r, float g, float b, float a) {
        Matrix4f m = ms.peek().getPositionMatrix();
        float x1 = (float) x1d, y1 = (float) y1d, z1 = (float) z1d;
        float x2 = (float) x2d, y2 = (float) y2d, z2 = (float) z2d;
        int R = (int) (r * 255), G = (int) (g * 255), B = (int) (b * 255), A = (int) (a * 255);

        q(vc,m, x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, R,G,B,A);
        q(vc,m, x1,y2,z1, x1,y2,z2, x2,y2,z2, x2,y2,z1, R,G,B,A);
        q(vc,m, x1,y1,z1, x1,y2,z1, x2,y2,z1, x2,y1,z1, R,G,B,A);
        q(vc,m, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, R,G,B,A);
        q(vc,m, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, R,G,B,A);
        q(vc,m, x2,y1,z1, x2,y2,z1, x2,y2,z2, x2,y1,z2, R,G,B,A);
    }

    private void q(VertexConsumer vc, Matrix4f m,
                   float ax,float ay,float az, float bx,float by,float bz,
                   float cx,float cy,float cz, float dx,float dy,float dz,
                   int r,int g,int b,int a) {
        vc.vertex(m,ax,ay,az).color(r,g,b,a);
        vc.vertex(m,bx,by,bz).color(r,g,b,a);
        vc.vertex(m,cx,cy,cz).color(r,g,b,a);
        vc.vertex(m,dx,dy,dz).color(r,g,b,a);
    }
}

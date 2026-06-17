package xyz.srgnis.bodyhealthsystem.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.entity.BodyHitHighlightTarget;
import xyz.srgnis.bodyhealthsystem.util.BodyHitboxes;

public final class BodyHitHighlightRenderer {
    private BodyHitHighlightRenderer() {}

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof BodyHitHighlightTarget target)) continue;
            if (!(entity instanceof LivingEntity living)) continue;
            Identifier partId = target.getHighlightedPart();
            if (partId == null) continue;

            Box box = BodyHitboxes.build(living).get(partId);
            if (box == null) continue;

            float[] color = colorForPart(partId);
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
            WorldRenderer.drawBox(
                    matrices, lines,
                    box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ,
                    color[0], color[1], color[2], 1.0f
            );
            VertexConsumer quads = consumers.getBuffer(RenderLayer.getDebugQuads());
            WorldRenderer.drawBox(
                    matrices, quads,
                    box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ,
                    color[0], color[1], color[2], 0.25f
            );
            matrices.pop();
        }

        consumers.draw();
    }

    private static float[] colorForPart(Identifier partId) {
        if (PlayerBodyParts.HEAD.equals(partId)) return new float[] {1.0f, 0.9f, 0.2f};
        if (PlayerBodyParts.TORSO.equals(partId)) return new float[] {1.0f, 0.25f, 0.25f};
        if (PlayerBodyParts.LEFT_ARM.equals(partId) || PlayerBodyParts.RIGHT_ARM.equals(partId)) {
            return new float[] {0.3f, 0.6f, 1.0f};
        }
        if (PlayerBodyParts.LEFT_LEG.equals(partId) || PlayerBodyParts.RIGHT_LEG.equals(partId)) {
            return new float[] {1.0f, 0.55f, 0.1f};
        }
        if (PlayerBodyParts.LEFT_FOOT.equals(partId) || PlayerBodyParts.RIGHT_FOOT.equals(partId)) {
            return new float[] {0.75f, 0.35f, 1.0f};
        }
        return new float[] {0.2f, 1.0f, 0.4f};
    }
}

package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.util.ProjectileHitTracker;

/**
 * TACZ compatibility mixin for TaCZ: Refabricated projectile hit detection.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public class TACZProjectileHitMixin {
    @Inject(
        method = "onHitEntity(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;)V",
        at = @At("HEAD"),
        require = 0,
        remap = false
    )
    private void bhs$recordTACZHit(Object result, Vec3d startVec, Vec3d endVec, CallbackInfo ci) {
        if (!(result instanceof EntityHitResult entityHitResult)) return;
        Entity hitEntity = entityHitResult.getEntity();
        if (!(hitEntity instanceof PlayerEntity player)) return;
        if (player.getWorld().isClient) return;

        Entity self = (Entity) (Object) this;

        Vec3d hitPos = entityHitResult.getPos();
        Vec3d projPos = self.getPos();
        Box box = player.getBoundingBox();

        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;

        double distHit = horizontalDistance(hitPos, centerX, centerZ);
        double distProj = horizontalDistance(projPos, centerX, centerZ);

        Vec3d best = (distProj > distHit) ? projPos : hitPos;

        double py = clamp(best.y, box.minY, box.maxY);
        Vec3d adjustedHit = new Vec3d(best.x, py, best.z);

        Vec3d origin = new Vec3d(centerX, py, centerZ);
        Vec3d offset = adjustedHit.subtract(origin);

        double height = Math.max(box.maxY - box.minY, 1.0E-3);
        double halfWidth = Math.max(player.getWidth() * 0.5, 1.0E-3);

        double yawRad = Math.toRadians(player.getBodyYaw());
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad)).normalize();
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x).normalize();

        double localX = offset.dotProduct(right);
        double localZ = offset.dotProduct(forward);

        double xNorm = clamp(localX / halfWidth, -1.0, 1.0);

        double yRaw = clamp((py - box.minY) / height, 0.0, 1.0);
        double headStart = clamp((player.getEyeY() - box.minY) / height, 0.0, 1.0);
        headStart = Math.min(headStart, 0.99);
        final double HEAD_BAND_START = 0.88;
        double yNorm;
        if (yRaw <= headStart) {
            yNorm = (headStart > 1.0E-6) ? (yRaw / headStart) * HEAD_BAND_START : 0.0;
        } else {
            yNorm = HEAD_BAND_START + ((yRaw - headStart) / (1.0 - headStart)) * (1.0 - HEAD_BAND_START);
        }
        yNorm = clamp(yNorm, 0.0, 1.0);

        double zNorm = clamp(localZ / halfWidth, -1.0, 1.0);

        ProjectileHitTracker.record(player, xNorm, yNorm, zNorm);
    }

    private static double horizontalDistance(Vec3d v, double cx, double cz) {
        double dx = v.x - cx;
        double dz = v.z - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

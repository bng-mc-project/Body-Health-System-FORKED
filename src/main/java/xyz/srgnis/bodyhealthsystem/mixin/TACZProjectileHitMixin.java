package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.util.BodyHitboxes;
import xyz.srgnis.bodyhealthsystem.util.ProjectileHitTracker;

/**
 * TACZ compatibility mixin for TaCZ: Refabricated projectile hit detection.
 *
 * TACZ's onHitEntity passes the TacHitResult which wraps an EntityHitResult.
 * We use the startVec/endVec (bullet trajectory) for our own hitbox detection.
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
        if (startVec == null || endVec == null) return;
        
        // Extract the hit entity from TacHitResult via reflection
        Entity hitEntity = null;
        try {
            java.lang.reflect.Method getEntityMethod = result.getClass().getMethod("getEntity");
            Object entityObj = getEntityMethod.invoke(result);
            if (entityObj instanceof Entity) {
                hitEntity = (Entity) entityObj;
            }
        } catch (Exception e) {
            BHSMain.LOGGER.warn("[BHS][TACZ] Could not extract entity from TacHitResult: {}", e.getMessage());
            return;
        }
        
        if (!(hitEntity instanceof PlayerEntity player)) return;
        if (player.getWorld().isClient) return;

        // Use the bullet trajectory (startVec -> endVec) to determine hit body part
        // endVec is the collision position from EntityHitResult
        Vec3d from = startVec;
        Vec3d to = endVec;
        if (to == null) to = from;

        Identifier part = BodyHitboxes.pickAtPoint(player, to);
        boolean fallbackUsed = false;
        if (part == null) {
            fallbackUsed = true;
            part = BodyHitboxes.pick(player, from, to);
        }
        if (part != null) {
            ProjectileHitTracker.recordPart(player, part);
        }

        BHSMain.LOGGER.info(
                "[BHS][ProjectileHit] type=tacz target={} hitPos=({},{},{}) start=({},{},{}) end=({},{},{}) part={} fallback={}",
                player.getName().getString(),
                String.format("%.3f", to.x), String.format("%.3f", to.y), String.format("%.3f", to.z),
                String.format("%.3f", from.x), String.format("%.3f", from.y), String.format("%.3f", from.z),
                String.format("%.3f", to.x), String.format("%.3f", to.y), String.format("%.3f", to.z),
                part != null ? part.toString() : "null",
                fallbackUsed
        );
    }
}
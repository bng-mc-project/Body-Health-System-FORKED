package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.util.BodyHitboxes;
import xyz.srgnis.bodyhealthsystem.util.ProjectileHitTracker;

@Mixin(PersistentProjectileEntity.class)
public class ProjectileHitMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void bhs$recordHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (entityHitResult == null) return;
        Entity hitEntity = entityHitResult.getEntity();
        if (!(hitEntity instanceof PlayerEntity player)) return;
        if (player.getWorld().isClient) return;

        Entity self = (Entity)(Object)this;
        Vec3d hitPos = entityHitResult.getPos();
        Vec3d velocity = self.getVelocity();
        Vec3d from = hitPos;
        if (velocity.lengthSquared() > 1.0E-6) {
            from = hitPos.subtract(velocity.normalize().multiply(1.25));
        }
        Vec3d to = hitPos;

        Identifier part = BodyHitboxes.pickAtPoint(player, hitPos);
        boolean fallbackUsed = false;
        if (part == null) {
            fallbackUsed = true;
            part = BodyHitboxes.pick(player, from, to);
        }
        if (part != null) {
            ProjectileHitTracker.recordPart(player, part);
        }

        BHSMain.LOGGER.info(
                "[BHS][ProjectileHit] type=vanilla target={} hitPos=({},{},{}) from=({},{},{}) to=({},{},{}) part={} fallback={}",
                player.getName().getString(),
                String.format("%.3f", hitPos.x), String.format("%.3f", hitPos.y), String.format("%.3f", hitPos.z),
                String.format("%.3f", from.x), String.format("%.3f", from.y), String.format("%.3f", from.z),
                String.format("%.3f", to.x), String.format("%.3f", to.y), String.format("%.3f", to.z),
                part != null ? part.toString() : "null",
                fallbackUsed
        );
    }
}

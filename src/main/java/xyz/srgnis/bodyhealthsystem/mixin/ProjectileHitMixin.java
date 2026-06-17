package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.entity.BodyHitHighlightTarget;
import xyz.srgnis.bodyhealthsystem.util.BodyProjectileHits;

@Mixin(PersistentProjectileEntity.class)
public class ProjectileHitMixin {

    @Inject(method = "onEntityHit", at = @At("HEAD"))
    private void bhs$recordHit(EntityHitResult entityHitResult, CallbackInfo ci) {
        if (entityHitResult == null) return;
        Entity hitEntity = entityHitResult.getEntity();
        if (!(hitEntity instanceof LivingEntity living)) return;
        if (!(living instanceof BodyHitHighlightTarget) && !(living instanceof net.minecraft.entity.player.PlayerEntity)) return;
        if (living.getWorld().isClient) return;

        PersistentProjectileEntity projectile = (PersistentProjectileEntity) (Object) this;
        Vec3d to = projectile.getPos();
        Vec3d prev = new Vec3d(projectile.prevX, projectile.prevY, projectile.prevZ);
        Vec3d from = prev.squaredDistanceTo(to) > 1.0E-6 ? prev : bhs$rayStart(projectile, to);
        Vec3d impact = BodyProjectileHits.resolveImpactPoint(living, from, to);

        Identifier part = BodyProjectileHits.resolvePart(living, impact, from, to, false);
        BodyProjectileHits.recordHit(living, part, "vanilla", impact);
    }

    private static Vec3d bhs$rayStart(PersistentProjectileEntity projectile, Vec3d hitPos) {
        Entity owner = projectile.getOwner();
        if (owner instanceof LivingEntity shooter) {
            return shooter.getEyePos();
        }
        if (owner != null) {
            return owner.getPos();
        }

        Vec3d velocity = projectile.getVelocity();
        if (velocity.lengthSquared() > 1.0E-6) {
            return hitPos.subtract(velocity.normalize().multiply(2.0));
        }

        Vec3d previous = new Vec3d(projectile.prevX, projectile.prevY, projectile.prevZ);
        if (previous.squaredDistanceTo(hitPos) > 1.0E-6) {
            return previous;
        }

        return hitPos.add(0.0, 0.5, 0.0);
    }
}

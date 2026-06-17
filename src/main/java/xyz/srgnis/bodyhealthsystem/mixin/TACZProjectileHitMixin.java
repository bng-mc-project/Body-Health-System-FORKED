package xyz.srgnis.bodyhealthsystem.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.srgnis.bodyhealthsystem.compat.TaczReflection;
import xyz.srgnis.bodyhealthsystem.entity.BodyHitHighlightTarget;
import xyz.srgnis.bodyhealthsystem.util.BodyProjectileHits;

/**
 * TACZ bullet hit hook. Uses {@link Coerce} + reflection so hybrid servers (Arclight/Kilt)
 * do not crash on {@code TacHitResult.getEntity()}.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public class TACZProjectileHitMixin {

    @Inject(
            method = "onHitEntity(Lcom/tacz/guns/util/TacHitResult;Lnet/minecraft/class_243;Lnet/minecraft/class_243;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void bhs$recordTACZHit(@Coerce Object result, @Coerce Object startVec, @Coerce Object endVec, CallbackInfo ci) {
        if (result == null || startVec == null) {
            return;
        }

        try {
            Entity hitEntity = TaczReflection.getHitEntity(result);
            if (!(hitEntity instanceof LivingEntity living)) {
                return;
            }
            if (!(living instanceof BodyHitHighlightTarget) && !(living instanceof PlayerEntity)) {
                return;
            }
            if (living.getWorld().isClient) {
                return;
            }

            Vec3d from = TaczReflection.asVec3d(startVec);
            Vec3d to = endVec != null ? TaczReflection.asVec3d(endVec) : TaczReflection.getHitPos(result);
            if (from == null || to == null) {
                return;
            }

            Vec3d impact = BodyProjectileHits.resolveImpactPoint(living, from, to);
            Identifier part = BodyProjectileHits.resolvePart(
                    living, impact, from, to, TaczReflection.isHeadshot(result));
            BodyProjectileHits.recordHit(living, part, "tacz", impact);
        } catch (ReflectiveOperationException e) {
            // Avoid crashing the server tick if TACZ mappings differ further on this platform.
        }
    }
}

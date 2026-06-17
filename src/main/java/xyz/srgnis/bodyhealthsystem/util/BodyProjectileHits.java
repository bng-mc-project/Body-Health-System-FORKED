package xyz.srgnis.bodyhealthsystem.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;
import xyz.srgnis.bodyhealthsystem.entity.BodyHitHighlightTarget;
import xyz.srgnis.bodyhealthsystem.util.BhsDebugLog;

import java.util.HashMap;
import java.util.Map;

public final class BodyProjectileHits {
    private BodyProjectileHits() {}

    private static final Map<Integer, Integer> TACZ_DEDUPE_AGE = new HashMap<>();

    public static Vec3d resolveImpactPoint(LivingEntity target, Vec3d from, Vec3d to) {
        if (from == null || to == null) {
            return to;
        }
        return BodyHitboxes.computeImpactPoint(target, from, to);
    }

    public static Identifier resolvePart(LivingEntity target, Vec3d hitPos, Vec3d from, Vec3d to, boolean headshot) {
        if (target == null) return null;
        if (headshot) return PlayerBodyParts.HEAD;

        Vec3d point = hitPos;
        if (from != null && to != null) {
            Vec3d computed = BodyHitboxes.computeImpactPoint(target, from, to);
            if (computed != null) {
                point = computed;
            }
        }

        Identifier part = null;
        if (from != null && to != null) {
            part = BodyHitboxes.pickLocal(target, point);
        }
        if (part == null && from != null && to != null) {
            part = BodyHitboxes.pick(target, from, to);
        }
        if (part == null && point != null) {
            part = BodyHitboxes.pickAtPoint(target, point);
        }
        return part;
    }

    public static void recordHit(Entity hitEntity, Identifier part, String projectileType, Vec3d hitPos) {
        if (hitEntity == null || part == null) return;

        if ("tacz".equals(projectileType)) {
            int entityId = hitEntity.getId();
            int age = hitEntity.age;
            Integer previous = TACZ_DEDUPE_AGE.get(entityId);
            if (previous != null && previous == age) {
                return;
            }
            TACZ_DEDUPE_AGE.put(entityId, age);
        }

        if (hitEntity instanceof BodyHitHighlightTarget highlightTarget) {
            highlightTarget.setHighlightedPart(part);
        }
        if (hitEntity instanceof PlayerEntity player) {
            ProjectileHitTracker.recordPart(player, part);
        }

        BhsDebugLog.info(
                "[BHS][ProjectileHit] type={} target={} hitPos=({},{},{}) part={}",
                projectileType,
                hitEntity.getName().getString(),
                hitPos != null ? String.format("%.3f", hitPos.x) : "null",
                hitPos != null ? String.format("%.3f", hitPos.y) : "null",
                hitPos != null ? String.format("%.3f", hitPos.z) : "null",
                part
        );
    }
}

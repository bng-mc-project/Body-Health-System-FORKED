package xyz.srgnis.bodyhealthsystem.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import xyz.srgnis.bodyhealthsystem.body.player.PlayerBodyParts;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds axis-aligned hitboxes for each named body part based on
 * the player's current bounding box and eye height.
 *
 * Layout (all Y values relative to box.minY, height = box.maxY - box.minY):
 *
 *   HEAD     : top 12% of height              (eyeY..top)
 *   TORSO    : 45%–88% of height, centre 60%  (upper body minus arms)
 *   L/R ARM  : 50%–88% of height, outer 40%   (lateral strips)
 *   L/R LEG  : 18%–50% of height              (split down centre in player-local X)
 *   L/R FOOT : bottom 18%                     (split down centre in player-local X)
 *
 * "Left" and "Right" are from the player's own perspective.
 * In world-space the split axis is perpendicular to the player's facing direction.
 */
public final class BodyHitboxes {

    private BodyHitboxes() {}

    /**
     * Returns an ordered map of body-part identifier → world-space AABB.
     * Iteration order is HEAD first so that the head box is tried before torso.
     */
    public static Map<Identifier, Box> build(PlayerEntity player) {
        Box full = player.getBoundingBox();
        double minX = full.minX;
        double minY = full.minY;
        double minZ = full.minZ;
        double maxX = full.maxX;
        double maxY = full.maxY;
        double maxZ = full.maxZ;

        double height = maxY - minY;
        double cx = (minX + maxX) * 0.5;
        double cz = (minZ + maxZ) * 0.5;

        // Y thresholds (absolute)
        double footTop  = minY + height * 0.18;
        double legTop   = minY + height * 0.50;
        double armStart = minY + height * 0.50;
        double armTop   = minY + height * 0.88;
        double torsoTop = minY + height * 0.88;
        double headBot  = minY + height * 0.88;

        double halfW = (maxX - minX) * 0.5;

        // All limb AABB boxes cover the full player XZ width.
        // Left/right distinction is resolved by dot-product in resolveSide(), not by AABB geometry.
        Map<Identifier, Box> boxes = new LinkedHashMap<>();

        // HEAD (full width)
        boxes.put(PlayerBodyParts.HEAD,  new Box(minX, headBot, minZ, maxX, maxY, maxZ));

        // TORSO: narrow the X/Z box to inner 60% so arms are preferred at the edges
        double torsoInset = halfW * 0.40;
        // Approximate centre strip in world space by shrinking the box
        // (not rotation-aware, but good enough for a hit-test since arms are checked first)
        boxes.put(PlayerBodyParts.TORSO,
                new Box(cx - (halfW - torsoInset), legTop, cz - (halfW - torsoInset),
                        cx + (halfW - torsoInset), torsoTop, cz + (halfW - torsoInset)));

        // ARMS — full-width boxes for the arm Y band (left/right resolved later)
        boxes.put(PlayerBodyParts.LEFT_ARM,  new Box(minX, armStart, minZ, maxX, armTop, maxZ));
        boxes.put(PlayerBodyParts.RIGHT_ARM, new Box(minX, armStart, minZ, maxX, armTop, maxZ));

        // LEGS — full-width boxes for the leg Y band
        boxes.put(PlayerBodyParts.LEFT_LEG,  new Box(minX, footTop, minZ, maxX, legTop, maxZ));
        boxes.put(PlayerBodyParts.RIGHT_LEG, new Box(minX, footTop, minZ, maxX, legTop, maxZ));

        // FEET
        boxes.put(PlayerBodyParts.LEFT_FOOT,  new Box(minX, minY, minZ, maxX, footTop, maxZ));
        boxes.put(PlayerBodyParts.RIGHT_FOOT, new Box(minX, minY, minZ, maxX, footTop, maxZ));

        return boxes;
    }

    /**
     * Casts a ray from {@code from} toward {@code to} and returns the identifier of the
     * first body-part box it intersects, or {@code null} if none.
     *
     * For paired parts (arms, legs, feet) the left/right choice is resolved by the
     * player-local right-vector dot product of the hit point.
     *
     * When the ray origin is inside the player bounding box (t=0 for multiple boxes),
     * the hit point Y coordinate is used to disambiguate the correct body part band
     * instead of relying on map iteration order.
     *
     * @param player  the target player
     * @param from    ray origin (e.g., previous projectile position)
     * @param to      ray end   (e.g., current projectile / hit position)
     */
    public static Identifier pick(PlayerEntity player, Vec3d from, Vec3d to) {
        Map<Identifier, Box> boxes = build(player);

        double yawRad = Math.toRadians(player.getYaw());
        // right vector (player-local, horizontal)
        double rtX = Math.cos(yawRad);
        double rtZ = Math.sin(yawRad);

        double cx = (player.getBoundingBox().minX + player.getBoundingBox().maxX) * 0.5;
        double cz = (player.getBoundingBox().minZ + player.getBoundingBox().maxZ) * 0.5;

        Identifier best = null;
        double bestT = Double.MAX_VALUE;

        for (Map.Entry<Identifier, Box> e : boxes.entrySet()) {
            Identifier id = e.getKey();
            Box box = e.getValue();
            double t = rayBoxIntersect(from, to, box);
            if (t < 0 || t > bestT) continue;

            // For paired parts, determine which side using the hit point dot-product
            Identifier resolved = resolveSide(id, from, to, t, cx, cz, rtX, rtZ, player);
            if (resolved != null) {
                bestT = t;
                best = resolved;
            }
        }

        // If the ray origin was inside the bounding box, multiple boxes return t=0
        // and the first in iteration order (HEAD) wins incorrectly.
        // Use the hit point Y coordinate to pick the correct body-part band.
        if (best != null && bestT <= 0.0) {
            Identifier yPart = resolvePartFromY(to.y, player);
            if (yPart != null) {
                // Re-resolve left/right side using the hit point
                Identifier sideResolved = resolveSideAtPoint(yPart, to, cx, cz, rtX, rtZ, player);
                if (sideResolved != null) {
                    best = sideResolved;
                }
            }
        }

        return best;
    }

    /**
     * Resolves the hit part from an exact impact point.
     * This is more stable than segment ray tests for some projectile implementations.
     *
     * Uses strict containment first, then falls back to Y-band matching and
     * nearest-box distance to handle boundary edge cases.
     */
    public static Identifier pickAtPoint(PlayerEntity player, Vec3d point) {
        if (player == null || point == null) return null;

        Map<Identifier, Box> boxes = build(player);
        double yawRad = Math.toRadians(player.getYaw());
        double rtX = Math.cos(yawRad);
        double rtZ = Math.sin(yawRad);
        double cx = (player.getBoundingBox().minX + player.getBoundingBox().maxX) * 0.5;
        double cz = (player.getBoundingBox().minZ + player.getBoundingBox().maxZ) * 0.5;

        // Priority order: lower limbs first to avoid false "head" on edge cases.
        Identifier[] order = new Identifier[] {
                PlayerBodyParts.LEFT_FOOT, PlayerBodyParts.RIGHT_FOOT,
                PlayerBodyParts.LEFT_LEG, PlayerBodyParts.RIGHT_LEG,
                PlayerBodyParts.LEFT_ARM, PlayerBodyParts.RIGHT_ARM,
                PlayerBodyParts.TORSO,
                PlayerBodyParts.HEAD
        };

        // Phase 1: strict containment (original logic)
        for (Identifier id : order) {
            Box box = boxes.get(id);
            if (box == null || !box.contains(point)) continue;

            Identifier resolved = resolveSideAtPoint(id, point, cx, cz, rtX, rtZ, player);
            if (resolved != null) return resolved;
        }

        // Phase 2: Y-band fallback — determine body part from Y coordinate alone.
        // This handles boundary cases where box.contains() fails due to strict inequalities.
        Identifier yPart = resolvePartFromY(point.y, player);
        if (yPart != null) {
            Identifier resolved = resolveSideAtPoint(yPart, point, cx, cz, rtX, rtZ, player);
            if (resolved != null) return resolved;
        }

        // Phase 3: nearest-box fallback — find the closest box by squared distance
        Identifier nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Identifier id : order) {
            Box box = boxes.get(id);
            if (box == null) continue;
            double dist = distSqToBox(point, box);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = id;
            }
        }
        if (nearest != null) {
            Identifier resolved = resolveSideAtPoint(nearest, point, cx, cz, rtX, rtZ, player);
            if (resolved != null) return resolved;
            return nearest;
        }

        return null;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Determines which body-part Y-band the given Y coordinate falls into.
     * Returns null if the Y is outside the player's bounding box entirely.
     */
    private static Identifier resolvePartFromY(double y, PlayerEntity player) {
        Box full = player.getBoundingBox();
        double minY = full.minY;
        double maxY = full.maxY;
        double height = maxY - minY;
        if (height <= 0) return null;

        double footTop  = minY + height * 0.18;
        double legTop   = minY + height * 0.50;
        double armStart = minY + height * 0.50;
        double armTop   = minY + height * 0.88;
        double headBot  = minY + height * 0.88;

        if (y >= headBot) return PlayerBodyParts.HEAD;
        if (y >= armStart && y < armTop) {
            // Arms and torso share this Y band; default to torso,
            // side resolution will handle arm vs torso distinction
            return PlayerBodyParts.TORSO;
        }
        if (y >= footTop && y < legTop) {
            // Leg band — return LEFT_LEG as placeholder, side resolution picks left/right
            return PlayerBodyParts.LEFT_LEG;
        }
        if (y < footTop) {
            // Foot band
            return PlayerBodyParts.LEFT_FOOT;
        }
        return null;
    }

    /**
     * Resolves left/right side for a body part at a given point, using the
     * player-local right-vector dot product. For arm parts, also checks
     * whether the hit is in the inner torso strip.
     *
     * Returns null for arm parts when the hit is in the centre (torso) strip.
     */
    private static Identifier resolveSideAtPoint(
            Identifier id, Vec3d point,
            double cx, double cz,
            double rtX, double rtZ,
            PlayerEntity player) {

        if (id.equals(PlayerBodyParts.LEFT_ARM) || id.equals(PlayerBodyParts.RIGHT_ARM)) {
            double dot = (point.x - cx) * rtX + (point.z - cz) * rtZ;
            double halfW = (player.getBoundingBox().maxX - player.getBoundingBox().minX) * 0.5;
            if (Math.abs(dot) < halfW * 0.40) {
                // Centre hit belongs to torso, not arm
                return null;
            }
            return dot >= 0 ? PlayerBodyParts.RIGHT_ARM : PlayerBodyParts.LEFT_ARM;
        }

        if (id.equals(PlayerBodyParts.LEFT_LEG) || id.equals(PlayerBodyParts.RIGHT_LEG)) {
            double dot = (point.x - cx) * rtX + (point.z - cz) * rtZ;
            return dot >= 0 ? PlayerBodyParts.RIGHT_LEG : PlayerBodyParts.LEFT_LEG;
        }

        if (id.equals(PlayerBodyParts.LEFT_FOOT) || id.equals(PlayerBodyParts.RIGHT_FOOT)) {
            double dot = (point.x - cx) * rtX + (point.z - cz) * rtZ;
            return dot >= 0 ? PlayerBodyParts.RIGHT_FOOT : PlayerBodyParts.LEFT_FOOT;
        }

        // HEAD, TORSO — no side resolution needed
        return id;
    }

    /**
     * Squared distance from a point to the nearest surface of an AABB.
     * Returns 0 if the point is inside the box.
     */
    private static double distSqToBox(Vec3d point, Box box) {
        double dx = Math.max(0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Slab-method ray/AABB intersection. Returns parametric t ∈ [0,1] of the first
     * hit, or -1 if no intersection in that segment.
     */
    private static double rayBoxIntersect(Vec3d from, Vec3d to, Box box) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double tmin = 0.0, tmax = 1.0;

        // X slab
        if (Math.abs(dx) < 1e-9) {
            if (from.x < box.minX || from.x > box.maxX) return -1;
        } else {
            double t1 = (box.minX - from.x) / dx;
            double t2 = (box.maxX - from.x) / dx;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }

        // Y slab
        if (Math.abs(dy) < 1e-9) {
            if (from.y < box.minY || from.y > box.maxY) return -1;
        } else {
            double t1 = (box.minY - from.y) / dy;
            double t2 = (box.maxY - from.y) / dy;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }

        // Z slab
        if (Math.abs(dz) < 1e-9) {
            if (from.z < box.minZ || from.z > box.maxZ) return -1;
        } else {
            double t1 = (box.minZ - from.z) / dz;
            double t2 = (box.maxZ - from.z) / dz;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return -1;
        }

        return tmin;
    }

    /**
     * For paired parts (LEFT / RIGHT variants) resolves which side was actually hit.
     * Returns the correct identifier, or the original id if not a paired part.
     * Returns null if the hit point is in the inner torso strip (arm boxes only),
     * so that the TORSO box entry takes precedence for centre hits.
     */
    private static Identifier resolveSide(
            Identifier id,
            Vec3d from, Vec3d to, double t,
            double cx, double cz,
            double rtX, double rtZ,
            PlayerEntity player) {

        boolean isPaired = id.equals(PlayerBodyParts.LEFT_ARM)
                || id.equals(PlayerBodyParts.RIGHT_ARM)
                || id.equals(PlayerBodyParts.LEFT_LEG)
                || id.equals(PlayerBodyParts.RIGHT_LEG)
                || id.equals(PlayerBodyParts.LEFT_FOOT)
                || id.equals(PlayerBodyParts.RIGHT_FOOT);

        if (!isPaired) return id;  // HEAD, TORSO: no side resolution needed

        // Hit point
        Vec3d hit = new Vec3d(
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t);

        // Dot product of (hitPoint - centre) with right vector
        double dot = (hit.x - cx) * rtX + (hit.z - cz) * rtZ;
        // dot > 0  → right side of player  → RIGHT_*
        // dot < 0  → left  side of player  → LEFT_*
        boolean isRightSide = dot >= 0;

        if (id.equals(PlayerBodyParts.LEFT_ARM)  ||  id.equals(PlayerBodyParts.RIGHT_ARM)) {
            // Arm zone: also check that the hit is in the outer 40% laterally
            double halfW = (player.getBoundingBox().maxX - player.getBoundingBox().minX) * 0.5;
            if (Math.abs(dot) < halfW * 0.40) {
                // Hit the inner torso strip — don't claim as arm; return null to skip
                return null;
            }
            return isRightSide ? PlayerBodyParts.RIGHT_ARM : PlayerBodyParts.LEFT_ARM;
        }

        if (id.equals(PlayerBodyParts.LEFT_LEG)  ||  id.equals(PlayerBodyParts.RIGHT_LEG)) {
            return isRightSide ? PlayerBodyParts.RIGHT_LEG : PlayerBodyParts.LEFT_LEG;
        }

        if (id.equals(PlayerBodyParts.LEFT_FOOT) ||  id.equals(PlayerBodyParts.RIGHT_FOOT)) {
            return isRightSide ? PlayerBodyParts.RIGHT_FOOT : PlayerBodyParts.LEFT_FOOT;
        }

        return id;
    }
}
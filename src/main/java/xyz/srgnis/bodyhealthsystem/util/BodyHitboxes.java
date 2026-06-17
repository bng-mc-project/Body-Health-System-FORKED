package xyz.srgnis.bodyhealthsystem.util;

import net.minecraft.entity.LivingEntity;
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
 * Layout (Y relative to box.minY, height = box.maxY - box.minY):
 *
 *   HEAD     : neckY .. top          (no overlap with torso)
 *   TORSO    : 50% .. neckY          (centre 60% width)
 *   L/R ARM  : 50% .. neckY          (outer 40% lateral)
 *   L/R LEG  : 18% .. 50%
 *   L/R FOOT : bottom 18%
 *
 * neckY is derived from {@link LivingEntity#getEyeY()} with a floor at 78% height
 * so face hits are not classified as torso.
 */
public final class BodyHitboxes {

    private BodyHitboxes() {}

    /** Tiny gap so torso/arm and head AABBs never share a face. */
    private static final double BOX_GAP = 1.0E-4;

    private record Layout(
            double footTop,
            double legTop,
            double neckY,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            double cx, double cz,
            double halfW
    ) {
        static Layout of(LivingEntity entity) {
            Box full = entity.getBoundingBox();
            double minX = full.minX;
            double minY = full.minY;
            double minZ = full.minZ;
            double maxX = full.maxX;
            double maxY = full.maxY;
            double maxZ = full.maxZ;

            double height = maxY - minY;
            double cx = (minX + maxX) * 0.5;
            double cz = (minZ + maxZ) * 0.5;
            double halfW = (maxX - minX) * 0.5;

            double footTop = minY + height * 0.18;
            double legTop = minY + height * 0.50;

            // Neck line: below eyes, but at least 78% up the body (face is not the top 12%).
            double neckFromEyes = entity.getEyeY() - height * 0.10;
            double neckFromFrac = minY + height * 0.78;
            double neckY = Math.max(neckFromEyes, neckFromFrac);
            neckY = Math.max(neckY, legTop + 0.05);
            neckY = Math.min(neckY, maxY - height * 0.06);

            return new Layout(footTop, legTop, neckY, minX, minY, minZ, maxX, maxY, maxZ, cx, cz, halfW);
        }

        double torsoTop() {
            return neckY - BOX_GAP;
        }

        double armTop() {
            return neckY - BOX_GAP;
        }

        boolean isHeadY(double y) {
            return y >= neckY;
        }
    }

    /**
     * Returns an ordered map of body-part identifier → world-space AABB.
     * Iteration order is HEAD first so that the head box is tried before torso.
     */
    public static Map<Identifier, Box> build(LivingEntity entity) {
        Layout layout = Layout.of(entity);

        Map<Identifier, Box> boxes = new LinkedHashMap<>();

        // HEAD — full width, strictly above torso/arm band
        boxes.put(PlayerBodyParts.HEAD, new Box(
                layout.minX, layout.neckY, layout.minZ,
                layout.maxX, layout.maxY, layout.maxZ));

        double torsoInset = layout.halfW * 0.40;
        boxes.put(PlayerBodyParts.TORSO, new Box(
                layout.cx - (layout.halfW - torsoInset), layout.legTop, layout.cz - (layout.halfW - torsoInset),
                layout.cx + (layout.halfW - torsoInset), layout.torsoTop(), layout.cz + (layout.halfW - torsoInset)));

        boxes.put(PlayerBodyParts.LEFT_ARM, new Box(
                layout.minX, layout.legTop, layout.minZ, layout.maxX, layout.armTop(), layout.maxZ));
        boxes.put(PlayerBodyParts.RIGHT_ARM, new Box(
                layout.minX, layout.legTop, layout.minZ, layout.maxX, layout.armTop(), layout.maxZ));

        boxes.put(PlayerBodyParts.LEFT_LEG, new Box(
                layout.minX, layout.footTop, layout.minZ, layout.maxX, layout.legTop, layout.maxZ));
        boxes.put(PlayerBodyParts.RIGHT_LEG, new Box(
                layout.minX, layout.footTop, layout.minZ, layout.maxX, layout.legTop, layout.maxZ));

        boxes.put(PlayerBodyParts.LEFT_FOOT, new Box(
                layout.minX, layout.minY, layout.minZ, layout.maxX, layout.footTop, layout.maxZ));
        boxes.put(PlayerBodyParts.RIGHT_FOOT, new Box(
                layout.minX, layout.minY, layout.minZ, layout.maxX, layout.footTop, layout.maxZ));

        return boxes;
    }

    /**
     * Returns the point where the segment {@code [from, to]} enters the entity bounding box.
     * If {@code to} is already inside the box it is returned directly.
     */
    public static Vec3d computeImpactPoint(LivingEntity entity, Vec3d from, Vec3d to) {
        if (entity == null || from == null || to == null) {
            return to;
        }

        Box box = entity.getBoundingBox().expand(0.05);
        if (box.contains(to)) {
            return to;
        }
        if (box.contains(from)) {
            return from;
        }

        double t = rayBoxIntersect(from, to, box);
        if (t < 0) {
            return to;
        }

        return new Vec3d(
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t
        );
    }

    /**
     * Resolves a body part from a world-space hit point using the entity's local
     * yaw and height fractions. More reliable than axis-aligned AABB tests when
     * the target is rotated.
     */
    public static Identifier pickLocal(LivingEntity entity, Vec3d point) {
        if (entity == null || point == null) return null;

        Box full = entity.getBoundingBox();
        if (!full.expand(0.05).contains(point)) {
            return null;
        }

        Layout layout = Layout.of(entity);
        double height = full.maxY - full.minY;
        if (height <= 1.0E-6) return null;

        double yawRad = Math.toRadians(entity.getYaw());
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double dx = point.x - entity.getX();
        double dz = point.z - entity.getZ();
        double localRight = dx * cos + dz * sin;
        double halfW = entity.getWidth() * 0.5;
        if (halfW <= 1.0E-6) return null;

        double lateralFrac = Math.abs(localRight) / halfW;
        boolean isRight = localRight >= 0.0;

        if (layout.isHeadY(point.y)) {
            return PlayerBodyParts.HEAD;
        }
        if (point.y >= layout.legTop) {
            if (lateralFrac >= 0.40) {
                return isRight ? PlayerBodyParts.RIGHT_ARM : PlayerBodyParts.LEFT_ARM;
            }
            return PlayerBodyParts.TORSO;
        }
        if (point.y >= layout.footTop) {
            return isRight ? PlayerBodyParts.RIGHT_LEG : PlayerBodyParts.LEFT_LEG;
        }
        return isRight ? PlayerBodyParts.RIGHT_FOOT : PlayerBodyParts.LEFT_FOOT;
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
    public static Identifier pick(LivingEntity entity, Vec3d from, Vec3d to) {
        Layout layout = Layout.of(entity);
        Map<Identifier, Box> boxes = build(entity);

        double yawRad = Math.toRadians(entity.getYaw());
        double rtX = Math.cos(yawRad);
        double rtZ = Math.sin(yawRad);

        double cx = layout.cx;
        double cz = layout.cz;

        Identifier best = null;
        double bestT = Double.MAX_VALUE;

        for (Map.Entry<Identifier, Box> e : boxes.entrySet()) {
            Identifier id = e.getKey();
            Box box = e.getValue();
            double t = rayBoxIntersect(from, to, box);
            if (t < 0 || t > bestT) continue;

            Identifier resolved = resolveSide(id, from, to, t, cx, cz, rtX, rtZ, entity);
            if (resolved != null) {
                bestT = t;
                best = resolved;
            }
        }

        if (best == null) {
            return null;
        }

        Vec3d hitPoint = new Vec3d(
                from.x + (to.x - from.x) * bestT,
                from.y + (to.y - from.y) * bestT,
                from.z + (to.z - from.z) * bestT);

        if (layout.isHeadY(hitPoint.y)) {
            return PlayerBodyParts.HEAD;
        }

        if (bestT <= 0.0) {
            Identifier yPart = resolvePartFromY(hitPoint.y, entity, layout);
            if (yPart != null) {
                Identifier sideResolved = resolveSideAtPoint(yPart, hitPoint, cx, cz, rtX, rtZ, entity);
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
    public static Identifier pickAtPoint(LivingEntity entity, Vec3d point) {
        if (entity == null || point == null) return null;

        Layout layout = Layout.of(entity);
        if (layout.isHeadY(point.y)) {
            return PlayerBodyParts.HEAD;
        }

        Map<Identifier, Box> boxes = build(entity);
        double yawRad = Math.toRadians(entity.getYaw());
        double rtX = Math.cos(yawRad);
        double rtZ = Math.sin(yawRad);
        double cx = layout.cx;
        double cz = layout.cz;

        Identifier[] order = new Identifier[] {
                PlayerBodyParts.LEFT_FOOT, PlayerBodyParts.RIGHT_FOOT,
                PlayerBodyParts.LEFT_LEG, PlayerBodyParts.RIGHT_LEG,
                PlayerBodyParts.LEFT_ARM, PlayerBodyParts.RIGHT_ARM,
                PlayerBodyParts.TORSO
        };

        for (Identifier id : order) {
            Box box = boxes.get(id);
            if (box == null || !box.contains(point)) continue;

            Identifier resolved = resolveSideAtPoint(id, point, cx, cz, rtX, rtZ, entity);
            if (resolved != null) return resolved;
        }

        Identifier yPart = resolvePartFromY(point.y, entity, layout);
        if (yPart != null) {
            Identifier resolved = resolveSideAtPoint(yPart, point, cx, cz, rtX, rtZ, entity);
            if (resolved != null) return resolved;
        }

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
            Identifier resolved = resolveSideAtPoint(nearest, point, cx, cz, rtX, rtZ, entity);
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
    private static Identifier resolvePartFromY(double y, LivingEntity entity, Layout layout) {
        if (layout.isHeadY(y)) {
            return PlayerBodyParts.HEAD;
        }
        if (y >= layout.legTop && y < layout.neckY) {
            return PlayerBodyParts.TORSO;
        }
        if (y >= layout.footTop && y < layout.legTop) {
            return PlayerBodyParts.LEFT_LEG;
        }
        if (y < layout.footTop) {
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
            LivingEntity entity) {

        if (id.equals(PlayerBodyParts.LEFT_ARM) || id.equals(PlayerBodyParts.RIGHT_ARM)) {
            double dot = (point.x - cx) * rtX + (point.z - cz) * rtZ;
            double halfW = (entity.getBoundingBox().maxX - entity.getBoundingBox().minX) * 0.5;
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
            LivingEntity entity) {

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
            double halfW = (entity.getBoundingBox().maxX - entity.getBoundingBox().minX) * 0.5;
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
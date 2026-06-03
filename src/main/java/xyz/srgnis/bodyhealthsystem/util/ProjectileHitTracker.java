package xyz.srgnis.bodyhealthsystem.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProjectileHitTracker {

    private static final class PartHit {
        private final Identifier partId;
        private final int tick;

        private PartHit(Identifier partId, int tick) {
            this.partId = partId;
            this.tick = tick;
        }
    }

    // Stores the resolved body-part identifier with the server tick when it was recorded.
    private static final Map<UUID, PartHit> LAST_PART = new ConcurrentHashMap<>();

    // Legacy normalised-coords map kept for any external callers that may still use it
    private static final Map<UUID, Vec3d> LAST_HIT = new ConcurrentHashMap<>();

    private ProjectileHitTracker() {}

    // ---- New API (hitbox-based) ----

    public static void recordPart(PlayerEntity player, Identifier partId) {
        if (player == null || partId == null) return;
        LAST_PART.put(player.getUuid(), new PartHit(partId, player.age));
    }

    public static Identifier getLastPart(PlayerEntity player) {
        if (player == null) return null;
        PartHit hit = LAST_PART.get(player.getUuid());
        return hit != null ? hit.partId : null;
    }

    /**
     * Consumes and returns the last recorded part if it is recent enough.
     * Prevents stale hits from being reused and allows non-standard damage sources
     * (e.g. custom gun mods) to still bind to a projectile hit event.
     */
    public static Identifier consumeRecentPart(PlayerEntity player, int maxAgeTicks) {
        if (player == null) return null;
        PartHit hit = LAST_PART.remove(player.getUuid());
        if (hit == null) return null;
        int age = player.age - hit.tick;
        if (age < 0 || age > maxAgeTicks) return null;
        return hit.partId;
    }

    public static void clearPart(PlayerEntity player) {
        if (player == null) return;
        LAST_PART.remove(player.getUuid());
    }

    // ---- Legacy API (normalised coords) ----

    public static void record(PlayerEntity player, double xNorm, double yNorm, double zNorm) {
        if (player == null) return;
        LAST_HIT.put(player.getUuid(), new Vec3d(xNorm, yNorm, zNorm));
    }

    public static Vec3d getLastHit(PlayerEntity player) {
        if (player == null) return null;
        return LAST_HIT.get(player.getUuid());
    }

    public static void clear(PlayerEntity player) {
        if (player == null) return;
        LAST_HIT.remove(player.getUuid());
        LAST_PART.remove(player.getUuid());
    }
}

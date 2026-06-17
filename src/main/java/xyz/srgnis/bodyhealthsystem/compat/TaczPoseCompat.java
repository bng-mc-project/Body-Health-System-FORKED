package xyz.srgnis.bodyhealthsystem.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Detects when TACZ is controlling player pose (prone / crawl).
 */
public final class TaczPoseCompat {
    private TaczPoseCompat() {}

    public static boolean hasForcedPose(PlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("tacz") || player == null) {
            return false;
        }
        try {
            Object pose = player.getClass().getMethod("tacz$getForcedPose").invoke(player);
            return pose != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}

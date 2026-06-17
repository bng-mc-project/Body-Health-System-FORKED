package xyz.srgnis.bodyhealthsystem.util;

import org.slf4j.Logger;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.config.Config;

public final class BhsDebugLog {
    private static final Logger LOGGER = BHSMain.LOGGER;

    private BhsDebugLog() {}

    public static boolean projectileHitsEnabled() {
        return Config.debugProjectileHitboxes;
    }

    public static void info(String message, Object... args) {
        if (!projectileHitsEnabled()) return;
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        if (!projectileHitsEnabled()) return;
        LOGGER.warn(message, args);
    }
}

package xyz.srgnis.bodyhealthsystem.compat;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Reflection helpers for TACZ classes on hybrid servers where Yarn/intermediary names differ.
 */
public final class TaczReflection {
    private TaczReflection() {}

    public static Entity getHitEntity(Object result) throws ReflectiveOperationException {
        if (result instanceof EntityHitResult hitResult) {
            return hitResult.getEntity();
        }
        return (Entity) invokeFirst(result, "getEntity", "method_17782");
    }

    public static Vec3d getHitPos(Object result) throws ReflectiveOperationException {
        if (result instanceof EntityHitResult hitResult) {
            return hitResult.getPos();
        }
        Object pos = invokeFirst(result, "getPos", "method_17784", "getLocation");
        return asVec3d(pos);
    }

    public static boolean isHeadshot(Object result) {
        try {
            return (boolean) invokeFirst(result, "isHeadshot", "isHeadShot");
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static Vec3d asVec3d(Object value) throws ReflectiveOperationException {
        if (value == null) {
            return null;
        }
        if (value instanceof Vec3d vec) {
            return vec;
        }
        return new Vec3d(
                coord(value, "getX", "method_10216", "x"),
                coord(value, "getY", "method_10214", "y"),
                coord(value, "getZ", "method_10215", "z")
        );
    }

    public static Object invokeFirst(Object target, String... methodNames) throws ReflectiveOperationException {
        ReflectiveOperationException last = null;
        for (String name : methodNames) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (NoSuchMethodException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new NoSuchMethodException("No method found on " + target.getClass().getName());
    }

    private static double coord(Object target, String... methodNames) throws ReflectiveOperationException {
        Object value = invokeFirst(target, methodNames);
        return ((Number) value).doubleValue();
    }
}

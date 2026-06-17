package xyz.srgnis.bodyhealthsystem.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.entity.BodyHitHighlightTarget;
import xyz.srgnis.bodyhealthsystem.util.BodyProjectileHits;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * TACZ hit detection via {@code EntityHurtByGunEvent.PRE/POST}.
 * Uses {@link LambdaMetafactory} because JDK {@code Proxy} listeners are not reliably
 * registered with Fabric's array-backed events.
 *
 * @see <a href="https://github.com/Sh1roCu/TACZ-Refabricated">TACZ-Refabricated</a>
 */
public final class TaczCompat {
    private TaczCompat() {}

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("tacz")) {
            return;
        }

        try {
            Class<?> eventClass = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent");
            registerPhase(eventClass, "PRE", "PreCallBack", "Pre");
            registerPhase(eventClass, "POST", "PostCallBack", "Post");
            BHSMain.LOGGER.info("[BHS] Registered TACZ gun hit listeners (PRE + POST)");
        } catch (Throwable e) {
            BHSMain.LOGGER.error("[BHS] Failed to register TACZ compatibility listener", e);
        }
    }

    private static void registerPhase(
            Class<?> eventClass,
            String phaseField,
            String callbackSimpleName,
            String eventSimpleName
    ) throws Throwable {
        Object fabricEvent = eventClass.getField(phaseField).get(null);
        Class<?> callbackClass = Class.forName(
                eventClass.getName() + "$" + callbackSimpleName);
        Class<?> gunEventClass = Class.forName(eventClass.getName() + "$" + eventSimpleName);

        Object listener = createListener(callbackClass, gunEventClass);
        fabricEvent.getClass()
                .getMethod("register", callbackClass)
                .invoke(fabricEvent, listener);
    }

    private static Object createListener(Class<?> callbackClass, Class<?> gunEventClass) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodType samMethodType = MethodType.methodType(void.class, gunEventClass);
        MethodHandle implMethod = lookup.findStatic(
                TaczCompat.class,
                "handleGunHit",
                MethodType.methodType(void.class, Object.class)
        );

        CallSite site = LambdaMetafactory.metafactory(
                lookup,
                "post",
                MethodType.methodType(callbackClass),
                samMethodType,
                implMethod,
                samMethodType
        );
        return site.getTarget().invoke();
    }

    /** Called via generated {@code PreCallBack}/{@code PostCallBack} lambda. */
    public static void handleGunHit(Object event) {
        try {
            Object logicalSide = TaczReflection.invokeFirst(event, "getLogicalSide");
            if (!(boolean) TaczReflection.invokeFirst(logicalSide, "isServer")) {
                return;
            }

            Entity hurtEntity = (Entity) TaczReflection.invokeFirst(event, "getHurtEntity");
            if (!(hurtEntity instanceof LivingEntity living)) {
                return;
            }
            if (!(living instanceof BodyHitHighlightTarget) && !(living instanceof PlayerEntity)) {
                return;
            }

            Entity bullet = (Entity) TaczReflection.invokeFirst(event, "getBullet");
            Entity attacker = (Entity) TaczReflection.invokeFirst(event, "getAttacker");
            boolean headshot = (boolean) TaczReflection.invokeFirst(event, "isHeadShot", "isHeadshot");

            Vec3d to = bullet != null ? bullet.getPos() : living.getPos();
            Vec3d from = attacker != null
                    ? attacker.getEyePos()
                    : (bullet != null
                            ? new Vec3d(bullet.prevX, bullet.prevY, bullet.prevZ)
                            : to);

            Vec3d impact = BodyProjectileHits.resolveImpactPoint(living, from, to);
            Identifier part = BodyProjectileHits.resolvePart(living, impact, from, to, headshot);
            BodyProjectileHits.recordHit(living, part, "tacz", impact);
        } catch (ReflectiveOperationException e) {
            BHSMain.LOGGER.error("[BHS] Failed to handle TACZ gun hit event", e);
        }
    }
}

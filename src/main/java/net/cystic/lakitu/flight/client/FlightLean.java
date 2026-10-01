package net.cystic.lakitu.flight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.cystic.lakitu.flight.FlightPose;
import net.cystic.lakitu.flight.FlyingMount;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a {@link FlightPose}'s lean and dip. The mount (or flying mob) leans in its own model space; its rider gets the
 * very same turn in world space around the same point, so the two move as one (FlyingMountRiderMixin).
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
public final class FlightLean {
    /** A rider's lean for this frame, by their render state (1.21.2+: extracted, then drawn). Weak: states are per frame. */
    private static final Map<Object, float[]> RIDERS = new WeakHashMap<>();

    private FlightLean() {
    }

    /**
     * In a GeckoLib entity's model space, after the renderer has turned it to face its yaw: lifted by {@code bob},
     * leaning around a point {@code pivotY} above its feet. Model space there has +X on its right and -Z in front, so
     * leaning right is a negative Z turn and dipping the front a negative X turn.
     */
    public static void inModel(PoseStack poseStack, float bob, float pivotY, float lean, float tilt) {
        poseStack.translate(0.0F, bob + pivotY, 0.0F);
        rotate(poseStack, Axis.ZP, -lean);
        rotate(poseStack, Axis.XP, -tilt);
        poseStack.translate(0.0F, -pivotY, 0.0F);
    }

    /**
     * The same turn in world axes, around {@code pivot} (relative to the pose's origin): for a rider, whose pose starts
     * at their own position. {@code yaw} is the mount's yaw on screen; turning by {@code 180 - yaw} gives its model space
     * (as GeckoLib and vanilla do), lean and dip happen there, and turning back returns to world axes.
     */
    public static void inWorld(PoseStack poseStack, Vec3 pivot, float yaw, float lean, float tilt) {
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        rotate(poseStack, Axis.YP, 180.0F - yaw);
        rotate(poseStack, Axis.ZP, -lean);
        rotate(poseStack, Axis.XP, -tilt);
        rotate(poseStack, Axis.YP, yaw - 180.0F);
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /** {pivot x, y, z from the rider, mount yaw, lean, tilt} for a rider of a leaning mount this frame, else null. */
    public static float[] riderLean(LivingEntity rider, float partialTick) {
        if (!(rider.getVehicle() instanceof FlyingMount mount))
            return null;
        FlightPose pose = mount.pose();
        float lean = pose.lean(partialTick);
        float tilt = pose.tilt(partialTick);
        if (lean == 0.0F && tilt == 0.0F)
            return null;
        // The mount's lean point, as its renderer places it (bob trailing a tick), from the rider's position.
        Vec3 pivot = mount.getPosition(partialTick).subtract(rider.getPosition(partialTick))
                .add(0.0, mount.bob(mount.tickCount + partialTick - 1.0F) + mount.leanPivot(), 0.0);
        return new float[] {(float) pivot.x, (float) pivot.y, (float) pivot.z, pose.yaw(partialTick), lean, tilt};
    }

    /** Applies a lean from {@link #riderLean}; null (not riding a leaning mount) does nothing. */
    public static void applyRiderLean(PoseStack poseStack, float[] lean) {
        if (lean != null)
            inWorld(poseStack, new Vec3(lean[0], lean[1], lean[2]), lean[3], lean[4], lean[5]);
    }

    public static void storeRiderLean(Object renderState, float[] lean) {
        if (lean == null)
            RIDERS.remove(renderState);
        else
            RIDERS.put(renderState, lean);
    }

    public static float[] storedRiderLean(Object renderState) {
        return RIDERS.get(renderState);
    }

    private static void rotate(PoseStack poseStack, Axis axis, float degrees) {
        //#if MC >= 26.3
        poseStack.rotateDegrees(axis, degrees);
        //#else
        poseStack.mulPose(axis.rotationDegrees(degrees));
        //#endif
    }
}

package net.cystic.lakitu.flight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.constant.dataticket.DataTicket;

/**
 * Draws a {@link net.cystic.lakitu.flight.FlightPose}'s lean and dip. The mount (or flying mob) leans in its own
 * model space; its rider gets the very same turn in world space around the same point, so the two move as one.
 *
 * <p>Framework module file (flying-mount): improve it in the minecraft-multiloader-mods skill
 * (assets/modules/flying-mount) and sync it into every mod that has it.
 */
public final class FlightLean {
    /** On a rider's render state: {pivot x, y, z from the rider, mount yaw, lean, tilt} (FlyingMountRiderMixin). */
    public static final DataTicket<float[]> RIDER = DataTicket.create("lakitu_rider_lean", float[].class);

    private FlightLean() {
    }

    /**
     * In a GeckoLib entity's model space, after the renderer has turned it to face its yaw ({@code super.adjustRenderPose}):
     * lifted by {@code bob}, leaning around a point {@code pivotY} above its feet. Model space there has +X on its right
     * and -Z in front, so leaning right is a negative Z turn and dipping the front a negative X turn.
     */
    public static void inModel(PoseStack poseStack, float bob, float pivotY, float lean, float tilt) {
        poseStack.translate(0.0F, bob + pivotY, 0.0F);
        poseStack.rotateDegrees(Axis.ZP, -lean);
        poseStack.rotateDegrees(Axis.XP, -tilt);
        poseStack.translate(0.0F, -pivotY, 0.0F);
    }

    /**
     * The same turn in world axes, around {@code pivot} (relative to the pose's origin): for a rider, whose pose
     * starts at their own position. {@code yaw} is the mount's yaw on screen; turning by {@code 180 - yaw} gives its
     * model space (as GeckoLib and vanilla do), lean and dip happen there, and turning back returns to world axes.
     */
    public static void inWorld(PoseStack poseStack, Vec3 pivot, float yaw, float lean, float tilt) {
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        poseStack.rotateDegrees(Axis.YP, 180.0F - yaw);
        poseStack.rotateDegrees(Axis.ZP, -lean);
        poseStack.rotateDegrees(Axis.XP, -tilt);
        poseStack.rotateDegrees(Axis.YP, yaw - 180.0F);
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
    }
}

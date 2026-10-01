package net.cystic.lakitu.entity;

import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ghast-style flying for the Lakitu, kept here rather than borrowed from vanilla's Ghast: its move control and goals
 * were private before 1.21.6 and have changed shape since, so one copy (the classic ghast logic) works on every
 * version. The mob drifts in pushes toward a wanted spot, floats about at random when idle, and faces where it's going
 * (or its target).
 */
final class LakituMovement {
    private LakituMovement() {
    }

    /** Every few ticks, pushes toward the wanted spot at the mob's flying speed if the way is clear. */
    static class FloatMoveControl extends MoveControl {
        private int floatDuration;

        FloatMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (this.operation != MoveControl.Operation.MOVE_TO || this.floatDuration-- > 0)
                return;
            this.floatDuration += this.mob.getRandom().nextInt(5) + 2;
            Vec3 travel = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
            double distance = travel.length();
            travel = travel.normalize();
            if (this.canReach(travel, Mth.ceil(distance)))
                this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(travel.scale(this.mob.getAttributeValue(Attributes.FLYING_SPEED) * 5.0 / 3.0)));
            else
                this.operation = MoveControl.Operation.WAIT;
        }

        private boolean canReach(Vec3 direction, int steps) {
            AABB box = this.mob.getBoundingBox();
            for (int i = 1; i < steps; i++) {
                box = box.move(direction);
                if (!this.mob.level().noCollision(this.mob, box))
                    return false;
            }
            return true;
        }
    }

    /** Picks a random spot up to 16 blocks away whenever it has nowhere to go (or got there, or it's far off). */
    static class FloatAroundGoal extends Goal {
        private final Mob mob;

        FloatAroundGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MoveControl move = this.mob.getMoveControl();
            if (!move.hasWanted())
                return true;
            double dx = move.getWantedX() - this.mob.getX();
            double dy = move.getWantedY() - this.mob.getY();
            double dz = move.getWantedZ() - this.mob.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            return d < 1.0 || d > 3600.0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource random = this.mob.getRandom();
            double x = this.mob.getX() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double y = this.mob.getY() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double z = this.mob.getZ() + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            this.mob.getMoveControl().setWantedPosition(x, y, z, 1.0);
        }
    }

    /** Faces its target when it has one within 64 blocks, otherwise the way it's drifting. */
    static class FaceGoal extends Goal {
        private final Mob mob;

        FaceGoal(Mob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                Vec3 movement = this.mob.getDeltaMovement();
                this.mob.setYRot(-((float) Mth.atan2(movement.x, movement.z)) * Mth.RAD_TO_DEG);
            } else if (target.distanceToSqr(this.mob) < 4096.0) {
                this.mob.setYRot(-((float) Mth.atan2(target.getX() - this.mob.getX(), target.getZ() - this.mob.getZ())) * Mth.RAD_TO_DEG);
            } else {
                return;
            }
            this.mob.yBodyRot = this.mob.getYRot();
        }
    }

    /** Vanilla's flying travel (as a ghast's): push by the input, move, then slow down (more in water and lava). */
    static void travel(Mob mob, Vec3 input) {
        double drag = mob.isInWater() ? 0.8 : mob.isInLava() ? 0.5 : 0.91;
        mob.moveRelative(0.02F, input);
        mob.move(net.minecraft.world.entity.MoverType.SELF, mob.getDeltaMovement());
        mob.setDeltaMovement(mob.getDeltaMovement().scale(drag));
    }
}

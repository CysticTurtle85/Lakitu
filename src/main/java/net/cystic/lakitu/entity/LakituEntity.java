package net.cystic.lakitu.entity;

import java.util.EnumSet;
import java.util.UUID;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.LakituConfig;
import net.cystic.lakitu.LakituSounds;
import net.cystic.lakitu.RainCloud;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A Lakitu on its cloud: one entity, one joined model. Floats around like a ghast and throws spiny eggs at
 * players, except players riding a Lakitu Cloud, whom it leaves alone unless they hit it. Like the mount, rain or
 * water turns its cloud into a grey, slower rain cloud, and lava burns it. It drops spiny eggs, and killed by a
 * player it sometimes drops a Lakitu Cloud.
 */
public class LakituEntity extends Mob implements Enemy, RangedAttackMob, GeoEntity {
    /** Rained on or in water (for a moment after, too): grey and slower. Set by the server. */
    private static final EntityDataAccessor<Boolean> DATA_RAIN_CLOUD = SynchedEntityData.defineId(LakituEntity.class, EntityDataSerializers.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation THROW = RawAnimation.begin().thenPlay("throw");
    /** From the start of the "throw" animation to the frame where the egg leaves the hand (0.5 s). */
    private static final int THROW_RELEASE_TICKS = 10;
    /**
     * Where the egg leaves the hand, in blocks from the Lakitu's feet: the held egg's centre at the throw animation's
     * release frame (art/lakitu.bbmodel posed by the animation: 0.7 right, 1.49 up, 0.48 forward), less half the
     * thrown egg's height since an entity's position is its bottom.
     */
    private static final double RELEASE_RIGHT = 0.7, RELEASE_UP = 1.29, RELEASE_FORWARD = 0.48;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** A cloud rider who hit this Lakitu, and so may be targeted despite riding. */
    private @Nullable UUID provokedBy;
    /** Ticks until the egg of the throw in progress leaves the hand (server), and at whom. */
    private int releaseIn;
    private @Nullable LivingEntity throwTarget;
    private int wetTicks;

    public LakituEntity(EntityType<? extends LakituEntity> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.moveControl = new Ghast.GhastMoveControl<>(this, false, () -> false);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(LakituConfig.values.lakituMaxHealth);
        this.setHealth(this.getMaxHealth());
    }

    public static EntityType.Builder<LakituEntity> configure(EntityType.Builder<LakituEntity> builder) {
        // Cloud 1.25 blocks across, hair tips at 2 blocks, goggles at 1.45 (line of sight; eggs leave from the hand).
        return builder.sized(1.25F, 2.0F).eyeHeight(1.45F).notInPeaceful().clientTrackingRange(10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.FLYING_SPEED, 0.06);
    }

    /** Natural spawns: at mountain height or above, under open sky, and only a share of the attempts. */
    public static boolean checkLakituSpawnRules(EntityType<LakituEntity> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        LakituConfig config = LakituConfig.values;
        return level.getDifficulty() != Difficulty.PEACEFUL
                && pos.getY() >= config.lakituSpawnMinY
                && random.nextDouble() < config.lakituSpawnChance
                && level.canSeeSky(pos)
                && checkMobSpawnRules(type, level, reason, pos, random);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_RAIN_CLOUD, false);
    }

    /** Rained on or in water, or was a moment ago: its cloud is grey and it flies slower. */
    public boolean isRainCloud() {
        return this.entityData.get(DATA_RAIN_CLOUD);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new HoverNearTargetGoal(this));
        this.goalSelector.addGoal(6, new Ghast.RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(7, new Ghast.GhastLookGoal(this));
        this.goalSelector.addGoal(7, new ThrowSpinyEggGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                (target, level) -> !LakituCloudEntity.isRiding(target)));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = this.getTarget();
        if (target == null)
            this.provokedBy = null;
        else if (LakituCloudEntity.isRiding(target) && !target.getUUID().equals(this.provokedBy))
            this.setTarget(null);

        if (this.releaseIn > 0 && --this.releaseIn == 0 && this.throwTarget != null) {
            this.performRangedAttack(this.throwTarget, 1.0F);
            this.throwTarget = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Here rather than in the AI step, so a Lakitu without AI turns grey too.
        if (!this.level().isClientSide() && this.isAlive()) {
            this.wetTicks = RainCloud.wetTicks(this.wetTicks, this.isInWaterOrRain());
            if (this.isRainCloud() != this.wetTicks > 0)
                this.setRainCloud(this.wetTicks > 0);
        }
    }

    /** A rain cloud flies at {@code rainCloudSpeed} of its speed: a transient flying-speed modifier, never saved. */
    private void setRainCloud(boolean rainCloud) {
        this.entityData.set(DATA_RAIN_CLOUD, rainCloud);
        AttributeInstance flyingSpeed = this.getAttribute(Attributes.FLYING_SPEED);
        flyingSpeed.removeModifier(RainCloud.SLOWDOWN);
        if (rainCloud)
            flyingSpeed.addTransientModifier(new AttributeModifier(RainCloud.SLOWDOWN, LakituConfig.values.rainCloudSpeed - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && source.getEntity() instanceof Player attacker && !attacker.isCreative() && !attacker.isSpectator()
                && (!LakituCloudEntity.isRiding(attacker) || LakituConfig.values.lakituRetaliatesAgainstRiders)) {
            this.provokedBy = attacker.getUUID();
            this.setTarget(attacker);
        }
        return hurt;
    }

    /** Server: winds up a throw at the target. The animation plays on clients; the egg leaves the hand on cue. */
    void startThrow(LivingEntity target) {
        this.triggerAnim("attack", "throw");
        this.releaseIn = THROW_RELEASE_TICKS;
        this.throwTarget = target;
    }

    /** Client: whether a throw is playing, i.e. whether the held egg is drawn. */
    public boolean isThrowing() {
        AnimationController<?> attack = this.geoCache.getManagerForId(this.getId()).getAnimationControllers().get("attack");
        return attack != null && attack.isPlayingTriggeredAnimation();
    }

    private Vec3 releasePoint() {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw);
        double cos = Mth.cos(yaw);
        // Forward is (-sin, cos) and the Lakitu's right is (-cos, -sin), as for any mob facing yaw.
        return this.position().add(-sin * RELEASE_FORWARD - cos * RELEASE_RIGHT, RELEASE_UP, cos * RELEASE_FORWARD - sin * RELEASE_RIGHT);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(this.level() instanceof ServerLevel level))
            return;
        SpinyEggEntity egg = new SpinyEggEntity(level, this);
        Vec3 from = this.releasePoint();
        egg.setPos(from);
        double dx = target.getX() - from.x;
        double dz = target.getZ() - from.z;
        double arc = Math.sqrt(dx * dx + dz * dz) * 0.2;
        Projectile.spawnProjectile(egg, level, ItemStack.EMPTY,
                projectile -> projectile.shoot(dx, target.getEyeY() - 1.1 + arc - projectile.getY(), dz, 1.6F, 4.0F));
        this.playSound(LakituSounds.SPINY_EGG_THROW, 1.0F, 0.9F + this.getRandom().nextFloat() * 0.2F);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        LakituConfig config = LakituConfig.values;
        int looting = source.getEntity() instanceof LivingEntity killer
                ? EnchantmentHelper.getEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), killer)
                : 0;
        // Spiny eggs whoever killed it: 2-4, +1 per Looting level.
        int eggs = Mth.nextInt(this.getRandom(), config.lakituSpinyEggDropMin, Math.max(config.lakituSpinyEggDropMin, config.lakituSpinyEggDropMax))
                + config.lakituSpinyEggDropPerLooting * looting;
        if (eggs > 0)
            this.spawnAtLocation(level, new ItemStack(Lakitu.spinyEggItem.get(), eggs));
        if (killedByPlayer && this.getRandom().nextDouble() < config.lakituCloudDropChance + config.lakituCloudDropChancePerLooting * looting)
            this.spawnAtLocation(level, new ItemStack(Lakitu.cloudItem.get()));
    }

    // --- Flying like a ghast --------------------------------------------------------------------

    @Override
    public void travel(Vec3 input) {
        this.travelFlying(input, 0.02F);
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return LakituSounds.LAKITU_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return LakituSounds.LAKITU_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return LakituSounds.LAKITU_AMBIENT;
    }

    // --- GeckoLib -----------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<LakituEntity>("float", test -> test.setAndContinue(IDLE)));
        // Right arm, hand and held egg only, so it plays over the bob.
        controllers.add(new AnimationController<LakituEntity>("attack", test -> PlayState.STOP).triggerableAnim("throw", THROW));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // --- Goals ----------------------------------------------------------------------------------

    /** While it has a target, floats to random spots a few blocks above and around it, within throwing range. */
    private static class HoverNearTargetGoal extends Goal {
        private final LakituEntity lakitu;

        HoverNearTargetGoal(LakituEntity lakitu) {
            this.lakitu = lakitu;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.lakitu.getTarget();
            if (target == null || !target.isAlive())
                return false;
            MoveControl moveControl = this.lakitu.getMoveControl();
            if (!moveControl.hasWanted())
                return true;
            double toWanted = this.lakitu.distanceToSqr(moveControl.getWantedX(), moveControl.getWantedY(), moveControl.getWantedZ());
            double wantedToTarget = target.distanceToSqr(moveControl.getWantedX(), moveControl.getWantedY(), moveControl.getWantedZ());
            return toWanted < 1.0 || wantedToTarget > 144.0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            LivingEntity target = this.lakitu.getTarget();
            RandomSource random = this.lakitu.getRandom();
            double x = target.getX() + (random.nextDouble() * 2.0 - 1.0) * 6.0;
            double y = target.getY() + 5.0 + random.nextDouble() * 4.0;
            double z = target.getZ() + (random.nextDouble() * 2.0 - 1.0) * 6.0;
            this.lakitu.getMoveControl().setWantedPosition(x, y, z, 1.0);
        }
    }

    /** Throws a spiny egg at the target every few seconds while it's in range and visible (wind-up included). */
    private static class ThrowSpinyEggGoal extends Goal {
        private final LakituEntity lakitu;
        private int cooldown;

        ThrowSpinyEggGoal(LakituEntity lakitu) {
            this.lakitu = lakitu;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.lakitu.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            this.cooldown = LakituConfig.ticks(LakituConfig.values.lakituThrowIntervalSeconds) / 2;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.lakitu.getTarget();
            if (target == null || --this.cooldown > 0)
                return;
            LakituConfig config = LakituConfig.values;
            if (this.lakitu.distanceToSqr(target) <= config.lakituThrowRange * config.lakituThrowRange && this.lakitu.hasLineOfSight(target)) {
                this.lakitu.startThrow(target);
                this.cooldown = LakituConfig.ticks(config.lakituThrowIntervalSeconds);
            }
        }
    }
}

package net.cystic.lakitu.entity;

import java.util.EnumSet;
import java.util.UUID;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.LakituConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A Lakitu on its cloud: one entity, one joined model. Floats around like a ghast and throws spiny eggs at
 * players, except players riding a Lakitu Cloud, whom it leaves alone unless they hit it.
 * Rain and lava hurt it like the mount. Killed by a player, it sometimes drops a Lakitu Cloud.
 */
public class LakituEntity extends Mob implements Enemy, RangedAttackMob, GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    /** A cloud rider who hit this Lakitu, and so may be targeted despite riding. */
    private @Nullable UUID provokedBy;

    public LakituEntity(EntityType<? extends LakituEntity> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.moveControl = new Ghast.GhastMoveControl<>(this, false, () -> false);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(LakituConfig.values.lakituMaxHealth);
        this.setHealth(this.getMaxHealth());
    }

    public static EntityType.Builder<LakituEntity> configure(EntityType.Builder<LakituEntity> builder) {
        return builder.sized(1.0F, 2.0F).eyeHeight(1.7F).notInPeaceful().clientTrackingRange(10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
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

        LakituConfig config = LakituConfig.values;
        if (this.tickCount % LakituConfig.ticks(config.rainDamageIntervalSeconds) == 0 && LakituCloudEntity.isInRain(this))
            this.hurtServer(level, LakituCloudEntity.rainDamage(level), (float) config.rainDamage);
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

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(this.level() instanceof ServerLevel level))
            return;
        SpinyEggEntity egg = new SpinyEggEntity(level, this);
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double arc = Math.sqrt(dx * dx + dz * dz) * 0.2;
        Projectile.spawnProjectile(egg, level, ItemStack.EMPTY,
                projectile -> projectile.shoot(dx, target.getEyeY() - 1.1 + arc - projectile.getY(), dz, 1.6F, 4.0F));
        this.playSound(SoundEvents.EGG_THROW, 1.0F, 0.4F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        if (!killedByPlayer)
            return;
        LakituConfig config = LakituConfig.values;
        int looting = source.getEntity() instanceof LivingEntity killer
                ? EnchantmentHelper.getEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), killer)
                : 0;
        if (this.getRandom().nextDouble() < config.lakituCloudDropChance + config.lakituCloudDropChancePerLooting * looting)
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
        return SoundEvents.GHASTLING_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GHASTLING_DEATH;
    }

    // --- GeckoLib -----------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<LakituEntity>("float", test -> test.setAndContinue(IDLE)));
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

    /** Throws a spiny egg at the target every few seconds while it's in range and visible. */
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
                this.lakitu.performRangedAttack(target, 1.0F);
                this.cooldown = LakituConfig.ticks(config.lakituThrowIntervalSeconds);
            }
        }
    }
}

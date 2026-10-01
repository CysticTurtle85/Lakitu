package net.cystic.lakitu.entity;

import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.LakituConfig;
import net.cystic.lakitu.LakituSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The spiky egg a Lakitu throws, and a cloud rider too (the Spiny Egg item). Arcs like a snowball, hurts whatever it
 * hits ({@code lakituSpinyEggDamage}, or {@code riderSpinyEggDamage} from a player) and cracks open wherever it lands.
 * It never hits the thrower's own cloud. It doesn't hatch (yet).
 */
public class SpinyEggEntity extends ThrowableProjectile implements GeoEntity {
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("spin");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public SpinyEggEntity(EntityType<? extends SpinyEggEntity> type, Level level) {
        super(type, level);
    }

    public SpinyEggEntity(Level level, LivingEntity thrower) {
        this(Lakitu.spinyEgg.get(), level);
        this.setOwner(thrower);
        this.setPos(thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
    }

    public static EntityType.Builder<SpinyEggEntity> builder() {
        return EntityType.Builder.<SpinyEggEntity>of(SpinyEggEntity::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                //#if MC >= 1.21.2
                .noLootTable()
                //#endif
                .clientTrackingRange(4)
                .updateInterval(10);
    }

    @Override
    //#if MC >= 1.20.5
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    //#else
    protected void defineSynchedData() {
    //#endif
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        Entity owner = this.getOwner();
        // Vanilla only spares the owner's vehicle until the egg has left the owner; a rider throwing down would hit it.
        return super.canHitEntity(entity) && (owner == null || entity != owner.getVehicle());
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Entity entity = hitResult.getEntity();
        LakituConfig config = LakituConfig.values;
        double damage = this.getOwner() instanceof Player ? config.riderSpinyEggDamage : config.lakituSpinyEggDamage;
        entity.hurt(this.damageSources().thrown(this, this.getOwner()), (float) damage);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel level) {
            // Cracks open: shell bits and a crunch.
            //#if MC >= 1.21.2
            level.sendParticles(new DustParticleOptions(0xD62020, 1.2F), this.getX(), this.getY() + 0.2, this.getZ(), 10, 0.15, 0.15, 0.15, 0.0);
            //#else
            level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.84F, 0.13F, 0.13F), 1.2F), this.getX(), this.getY() + 0.2, this.getZ(), 10, 0.15, 0.15, 0.15, 0.0);
            //#endif
            level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.1, 0.1, 0.1, 0.2);
            this.playSound(LakituSounds.SPINY_EGG_CRACK, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
            this.discard();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        //#if MC >= 1.21.5
        controllers.add(new AnimationController<SpinyEggEntity>("spin", test -> test.setAndContinue(SPIN)));
        //#else
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
        //#endif
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}

package net.cystic.lakitu.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.cystic.lakitu.AltitudeSpeed;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.LakituConfig;
import net.cystic.lakitu.LakituSounds;
import net.cystic.lakitu.RainCloud;
import net.cystic.lakitu.flight.FlightSettings;
import net.cystic.lakitu.flight.FlyingMount;
import net.cystic.lakitu.item.CloudData;
import net.cystic.lakitu.item.DismountSlowFall;
import net.cystic.lakitu.item.LakituCloudItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The rideable cloud summoned from a Lakitu Cloud item. It only exists while its summoner rides it: whenever it loses
 * its rider (dismissed, rider died, or the item left their inventory) it disappears and its health goes back onto the
 * item. Portals take cloud and rider through together (vanilla vehicle travel), and a rider who logs out takes it
 * with them in their player data (like a horse), so they log back in still riding instead of falling.
 *
 * <p>It flies as a {@link FlyingMount} (the flying-mount framework module: steering, easing in and gliding out,
 * floating, turning and leaning). What's the cloud's own: height ({@link AltitudeSpeed}) and rain ({@link RainCloud}:
 * rain or water on the cloud or the rider turns it into a grey, slower rain cloud) change its speed, two effects show
 * the rider why, and its whole life is tied to its item.
 */
public class LakituCloudEntity extends FlyingMount implements GeoEntity {
    /** The cloud's look; speeds and timings come from the config (setUp). */
    public static final FlightSettings FLIGHT = FlightSettings.DEFAULT;
    private static final EntityDataAccessor<Float> DATA_ALTITUDE_TOP = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ALTITUDE_BOTTOM = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ALTITUDE_NETHER = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ALTITUDE_END = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RAIN_SPEED = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.FLOAT);
    /** Rained on or in water (for a moment after, too): grey and slower. Set by the server. */
    private static final EntityDataAccessor<Boolean> DATA_RAIN_CLOUD = SynchedEntityData.defineId(LakituCloudEntity.class, EntityDataSerializers.BOOLEAN);

    /** Clouds currently out in the world, by cloud id (server only). */
    private static final Map<UUID, LakituCloudEntity> ACTIVE = new ConcurrentHashMap<>();
    /** Deaths whose item couldn't be found at the time, applied when the item is next used (server only). */
    private static final Map<UUID, Long> PENDING_DEATHS = new ConcurrentHashMap<>();

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private @Nullable UUID cloudId;
    private @Nullable UUID lastRider;
    private float savedHealth = -1.0F;
    private int ticksWithoutRider;
    private int wetTicks;
    private boolean finished;

    public LakituCloudEntity(EntityType<? extends LakituCloudEntity> type, Level level) {
        super(type, level, FLIGHT);
        if (!level.isClientSide()) {
            LakituConfig config = LakituConfig.values;
            this.setFlightSettings(FLIGHT.withSpeeds((float) config.cloudHorizontalSpeed, (float) config.cloudVerticalSpeed)
                    .withTimings((float) config.cloudAccelerationSeconds, (float) config.cloudGlideSeconds));
            this.entityData.set(DATA_ALTITUDE_TOP, (float) LakituConfig.values.altitudeSpeedAtBuildLimit);
            this.entityData.set(DATA_ALTITUDE_BOTTOM, (float) LakituConfig.values.altitudeSpeedAtBottom);
            this.entityData.set(DATA_ALTITUDE_NETHER, (float) LakituConfig.values.altitudeSpeedInNether);
            this.entityData.set(DATA_ALTITUDE_END, (float) LakituConfig.values.altitudeSpeedInEnd);
            this.entityData.set(DATA_RAIN_SPEED, (float) LakituConfig.values.rainCloudSpeed);
        }
    }

    // Not noSave(): vanilla refuses to let anyone ride an entity type that can't be saved.
    public static EntityType.Builder<LakituCloudEntity> builder() {
        return EntityType.Builder.of(LakituCloudEntity::new, MobCategory.MISC)
                // Hitbox from art/lakitu.bbmodel's cloud group: the body is 1.25 blocks across (side puffs reach 1.4) and
                // the seat is 12 px up, where the rider's hips go (passenger point 0.6 minus the player's own 0.6 offset).
                .sized(1.25F, 0.75F)
                .passengerAttachments(0.6F)
                .noSummon()
                .clientTrackingRange(10);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0);
    }

    /** Riding a Lakitu Cloud in particular (Lakitus leave these riders alone; the effects only show for them). */

    public static boolean isRiding(Entity entity) {
        return entity.getVehicle() instanceof LakituCloudEntity;
    }

    public static boolean isOut(UUID cloudId) {
        LakituCloudEntity cloud = ACTIVE.get(cloudId);
        return cloud != null && !cloud.isRemoved();
    }

    public static @Nullable Long takePendingDeath(UUID cloudId) {
        return PENDING_DEATHS.remove(cloudId);
    }

    /** Server: links a freshly created cloud to its item's state, before it's added to the level. */
    public void setUp(UUID cloudId, float healthFraction, @Nullable Vec3 launch) {
        LakituConfig config = LakituConfig.values;
        this.cloudId = cloudId;
        ACTIVE.put(cloudId, this);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(config.cloudMaxHealth);
        this.setHealth(Math.max(1.0F, healthFraction * this.getMaxHealth()));
        this.savedHealth = this.getHealth() / this.getMaxHealth();
        if (launch != null)
            this.launch(launch, LakituConfig.ticks(config.testLaunchFadeSeconds));
    }

    public boolean belongsTo(ItemStack stack) {
        return cloudId != null && LakituCloudItem.data(stack).cloudId().filter(cloudId::equals).isPresent();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_ALTITUDE_TOP, 3.0F);
        entityData.define(DATA_ALTITUDE_BOTTOM, 0.5F);
        entityData.define(DATA_ALTITUDE_NETHER, 0.5F);
        entityData.define(DATA_ALTITUDE_END, 3.0F);
        entityData.define(DATA_RAIN_SPEED, 0.5F);
        entityData.define(DATA_RAIN_CLOUD, false);
    }

    // --- Riding ---------------------------------------------------------------------------------

    /** Height and rain on top of the rider's Speed/Slowness, from the cloud's exact height every tick (no steps). */
    @Override
    protected double speedMultiplier(Player rider) {
        double multiplier = super.speedMultiplier(rider) * this.altitudeMultiplier();
        return this.isRainCloud() ? multiplier * this.entityData.get(DATA_RAIN_SPEED) : multiplier;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Leave the rider where they sit instead of searching for the ground: the cloud is usually high up.
        return this.position();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide() && passenger instanceof LivingEntity living) {
            living.removeEffect(Lakitu.altitudeEffect.get());
            living.removeEffect(Lakitu.rainCloudEffect.get());
        }
    }

    // --- Server upkeep ------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && this.isAlive() && !this.isRemoved())
            this.serverTick(level);
    }

    private void serverTick(ServerLevel level) {
        if (!(this.getControllingPassenger() instanceof ServerPlayer rider)) {
            if (++this.ticksWithoutRider > 1)
                this.dismiss(level, false);
            return;
        }
        this.ticksWithoutRider = 0;
        this.lastRider = rider.getUUID();

        ItemStack stack = this.findItem(rider);
        if (stack.isEmpty() || rider.isDeadOrDying()) {
            this.dismiss(level, false);
            return;
        }

        // Rain or water on the cloud or the rider: a grey rain cloud, slower, not hurt.
        this.wetTicks = RainCloud.wetTicks(this.wetTicks, this.isInWaterOrRain() || rider.isInWaterOrRain());
        this.entityData.set(DATA_RAIN_CLOUD, this.wetTicks > 0);
        showSpeed(rider, Lakitu.altitudeEffect.get(), this.altitudeMultiplier());
        if (this.isRainCloud())
            showSpeed(rider, Lakitu.rainCloudEffect.get(), this.entityData.get(DATA_RAIN_SPEED));
        else
            rider.removeEffect(Lakitu.rainCloudEffect.get());

        // Replaces vanilla's "Press Shift to Dismount", which is wrong for the cloud.
        if (this.tickCount == 5)
            rider.sendOverlayMessage(Component.translatable("message.lakitu.cloud_controls"));

        // Keep the item up to date while riding: a disconnect saves the player before the cloud is removed.
        float fraction = this.getHealth() / this.getMaxHealth();
        if (this.isAlive() && Math.abs(fraction - this.savedHealth) > 0.001F)
            this.saveHealth(stack, level);
    }

    /** Speed multiplier for the cloud's current height and dimension (rider's Speed and Slowness, rain not included). */
    public double altitudeMultiplier() {
        return AltitudeSpeed.multiplier(this.level(), this.getY(), this.entityData.get(DATA_ALTITUDE_TOP), this.entityData.get(DATA_ALTITUDE_BOTTOM),
                this.entityData.get(DATA_ALTITUDE_NETHER), this.entityData.get(DATA_ALTITUDE_END));
    }

    /** Rained on or in water, or was a moment ago: grey and slower. */
    public boolean isRainCloud() {
        return this.entityData.get(DATA_RAIN_CLOUD);
    }

    /** Keeps a cloud-speed effect on the rider showing this multiplier (to a tenth; it's only re-sent then). */
    private void showSpeed(ServerPlayer rider, Holder<MobEffect> effect, double multiplier) {
        int amplifier = AltitudeSpeed.amplifier(multiplier);
        MobEffectInstance shown = rider.getEffect(effect);
        if (shown == null || shown.getAmplifier() != amplifier || !shown.isInfiniteDuration())
            // forceAddEffect: addEffect wouldn't lower an effect's amplifier. Ambient (beacon-style frame), no particles.
            rider.forceAddEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, true, false, true), this);
    }

    /** Server: sends the cloud back into its item. {@code deliberate} is a dismount with the item (Slow Falling). */
    public void dismiss(ServerLevel level, boolean deliberate) {
        if (this.finished)
            return;
        this.finished = true;
        Player rider = this.riderOrLastRider(level);
        if (rider != null) {
            ItemStack stack = this.findItem(rider);
            if (!stack.isEmpty())
                this.saveHealth(stack, level);
            if (deliberate && LakituConfig.values.slowFallingOnDismount && this.hasPassenger(rider))
                DismountSlowFall.grant(rider);
        }
        this.ejectPassengers();
        this.poof(level);
        this.playSound(LakituSounds.CLOUD_DISMISS, 1.0F, 1.0F);
        this.discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(this.level() instanceof ServerLevel level) || this.finished)
            return;
        this.finished = true;
        long now = level.getGameTime();
        int cooldown = LakituConfig.ticks(LakituConfig.values.cloudDeathCooldownSeconds);
        Player rider = this.riderOrLastRider(level);
        ItemStack stack = rider != null ? this.findItem(rider) : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            stack.set(Lakitu.cloudData.get(), LakituCloudItem.data(stack).died(now, now + cooldown));
            rider.getCooldowns().addCooldown(CloudData.cooldownGroup(this.cloudId), cooldown);
        } else if (this.cloudId != null) {
            PENDING_DEATHS.put(this.cloudId, now + cooldown);
        }
        // The rider falls from here with normal fall damage.
        this.ejectPassengers();
        this.poof(level);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.storeNullable("cloud_id", UUIDUtil.CODEC, this.cloudId);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.cloudId = input.read("cloud_id", UUIDUtil.CODEC).orElse(null);
        this.savedHealth = this.getHealth() / this.getMaxHealth();
        if (this.cloudId != null && !this.level().isClientSide())
            ACTIVE.put(this.cloudId, this);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        if (this.cloudId != null && !this.level().isClientSide())
            ACTIVE.remove(this.cloudId, this);
    }

    private void saveHealth(ItemStack stack, ServerLevel level) {
        float fraction = this.getHealth() / this.getMaxHealth();
        stack.set(Lakitu.cloudData.get(), LakituCloudItem.data(stack).withHealth(fraction, level.getGameTime()));
        this.savedHealth = fraction;
    }

    private ItemStack findItem(Player player) {
        return this.cloudId == null ? ItemStack.EMPTY : LakituCloudItem.find(player, this.cloudId);
    }

    private @Nullable Player riderOrLastRider(ServerLevel level) {
        if (this.getControllingPassenger() instanceof Player player)
            return player;
        return this.lastRider == null ? null : level.getServer().getPlayerList().getPlayer(this.lastRider);
    }

    private void poof(ServerLevel level) {
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.4, this.getZ(), 24, 0.6, 0.25, 0.6, 0.02);
    }

    // --- Damage -------------------------------------------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        // The rider can't hurt their own cloud (e.g. by swinging at something below them).
        if (source.getEntity() != null && this.hasPassenger(source.getEntity()))
            return false;
        return super.hurtServer(level, source, damage);
    }

    // --- Misc mob behaviour -----------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return LakituSounds.CLOUD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return LakituSounds.CLOUD_DEATH;
    }

    // --- GeckoLib -----------------------------------------------------------------------------

    /** No animations: the float is done in code ({@link #bob}) so the rider can float with it. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}

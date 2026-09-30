package net.cystic.lakitu.item;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.LakituConfig;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Summons a Lakitu Cloud and seats the player on it (like a Terraria mount); used again while riding, it dismisses
 * the cloud. The cloud's health lives on the item between summons ({@link CloudData}) and slowly heals there. If the
 * cloud dies, the item can't be used until the cloud re-forms.
 */
public class LakituCloudItem extends Item {
    public LakituCloudItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static CloudData data(ItemStack stack) {
        return stack.getOrDefault(Lakitu.cloudData.get(), CloudData.NEW);
    }

    /** The player's item for this cloud: anywhere in their inventory, or held on the cursor. */
    public static ItemStack find(Player player, UUID cloudId) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isFor(stack, cloudId))
                return stack;
        }
        ItemStack carried = player.containerMenu.getCarried();
        return isFor(carried, cloudId) ? carried : ItemStack.EMPTY;
    }

    private static boolean isFor(ItemStack stack, UUID cloudId) {
        return stack.getItem() instanceof LakituCloudItem && data(stack).cloudId().filter(cloudId::equals).isPresent();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        applyPendingDeath(stack, level);
        CloudData data = data(stack);

        if (player.getVehicle() instanceof LakituCloudEntity cloud) {
            if (level instanceof ServerLevel serverLevel) {
                if (!cloud.belongsTo(stack))
                    return InteractionResult.FAIL;
                cloud.dismiss(serverLevel, true);
            }
            return InteractionResult.SUCCESS;
        }

        if (data.isReforming(level.getGameTime())) {
            if (player instanceof ServerPlayer serverPlayer)
                serverPlayer.sendOverlayMessage(Component.translatable("message.lakitu.cloud_reforming", timeLeft(data, level)));
            return InteractionResult.FAIL;
        }
        if (player.isPassenger())
            return InteractionResult.FAIL;
        if (!(level instanceof ServerLevel serverLevel))
            return InteractionResult.SUCCESS;

        UUID cloudId = data.cloudId().orElseGet(UUID::randomUUID);
        if (LakituCloudEntity.isOut(cloudId)) {
            player.sendOverlayMessage(Component.translatable("message.lakitu.cloud_in_use"));
            return InteractionResult.FAIL;
        }
        LakituCloudEntity cloud = Lakitu.cloudEntity.get().create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
        if (cloud == null)
            return InteractionResult.FAIL;

        LakituConfig config = LakituConfig.values;
        Vec3 launch = config.testLaunchOnSummon ? player.getLookAngle().scale(config.testLaunchSpeed / 20.0) : null;
        cloud.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        cloud.setUp(cloudId, data.healthAt(level.getGameTime()), launch);
        // Forced: a normal mount fails while Shift is held or for 3 s after any dismount.
        if (!serverLevel.addFreshEntity(cloud) || !player.startRiding(cloud, true, true)) {
            cloud.discard();
            return InteractionResult.FAIL;
        }
        stack.set(Lakitu.cloudData.get(), data.withCloudId(cloudId));
        // Summoning and dismissing share a short per-cloud cooldown, applied by vanilla after a successful use.
        stack.set(DataComponents.USE_COOLDOWN, new UseCooldown((float) config.cloudUseCooldownSeconds, Optional.of(CloudData.cooldownGroup(cloudId))));
        serverLevel.sendParticles(ParticleTypes.CLOUD, cloud.getX(), cloud.getY() + 0.4, cloud.getZ(), 24, 0.6, 0.25, 0.6, 0.02);
        level.playSound(null, cloud.getX(), cloud.getY(), cloud.getZ(), SoundEvents.HARNESS_GOGGLES_DOWN, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof Player player))
            return;
        DismountSlowFall.tick(player);
        applyPendingDeath(stack, level);
        // Item cooldowns aren't saved, so restore the re-forming overlay after a relog.
        CloudData data = data(stack);
        if (level.getGameTime() % 20 == 0 && data.isReforming(level.getGameTime()) && data.cloudId().isPresent() && !player.getCooldowns().isOnCooldown(stack))
            player.getCooldowns().addCooldown(CloudData.cooldownGroup(data.cloudId().get()), (int) (data.reformsAt() - level.getGameTime()));
    }

    /** A cloud that died while its item was out of reach (dropped, in a chest) records its death here. */
    private static void applyPendingDeath(ItemStack stack, Level level) {
        if (level.isClientSide())
            return;
        CloudData data = data(stack);
        if (data.cloudId().isEmpty())
            return;
        Long reformsAt = LakituCloudEntity.takePendingDeath(data.cloudId().get());
        if (reformsAt != null)
            stack.set(Lakitu.cloudData.get(), data.died(level.getGameTime(), reformsAt));
    }

    private static String timeLeft(CloudData data, Level level) {
        return StringUtil.formatTickDuration((int) Math.max(0L, data.reformsAt() - level.getGameTime()), level.tickRateManager().tickrate());
    }

    // --- Health bar and tooltip ---------------------------------------------------------------

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // While re-forming, the vanilla cooldown overlay shows the time left instead.
        return data(stack).health() < 1.0F;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * data(stack).health());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(Math.max(0.0F, data(stack).health()) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        CloudData data = data(stack);
        builder.accept(Component.translatable("item.lakitu.lakitu_cloud.health", Math.round(data.health() * 100.0F)).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.lakitu.lakitu_cloud.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}

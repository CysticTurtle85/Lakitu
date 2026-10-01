package net.cystic.lakitu.item;

import java.util.function.Consumer;
import net.cystic.lakitu.LakituConfig;
import net.cystic.lakitu.LakituSounds;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.cystic.lakitu.entity.SpinyEggEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A spiny egg to throw, like a Lakitu does, but only from a Lakitu Cloud: on foot it says so and stays in the hand.
 * Thrown like an egg (5 damage, {@code riderSpinyEggDamage}), one per second ({@code riderSpinyEggCooldownSeconds}).
 * The tooltip says where it works. Lakitus drop them; Egg + Cactus + Red Dye makes two.
 */
public class SpinyEggItem extends Item {
    public SpinyEggItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!LakituCloudEntity.isRiding(player)) {
            if (!level.isClientSide())
                player.sendOverlayMessage(Component.translatable("message.lakitu.spiny_egg_riders_only"));
            return InteractionResult.FAIL;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), LakituSounds.SPINY_EGG_THROW, SoundSource.PLAYERS,
                0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        if (level instanceof ServerLevel serverLevel)
            Projectile.spawnProjectileFromRotation((egg, thrower, from) -> new SpinyEggEntity(egg, thrower), serverLevel, stack, player, 0.0F, 1.5F, 1.0F);
        player.getCooldowns().addCooldown(stack, LakituConfig.ticks(LakituConfig.values.riderSpinyEggCooldownSeconds));
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    /** Riders only, then the damage the way weapons show theirs ("When thrown:" / " 5 Attack Damage"). */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.lakitu.spiny_egg.riders_only").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.empty());
        builder.accept(Component.translatable("item.lakitu.spiny_egg.when_thrown").withStyle(ChatFormatting.GRAY));
        // This side's config: the same as the server's in single player and with a shared config.
        double damage = LakituConfig.values.riderSpinyEggDamage;
        String amount = damage == Math.rint(damage) ? String.valueOf((long) damage) : String.valueOf(damage);
        builder.accept(Component.translatable("item.lakitu.spiny_egg.damage", amount).withStyle(ChatFormatting.DARK_GREEN));
    }
}

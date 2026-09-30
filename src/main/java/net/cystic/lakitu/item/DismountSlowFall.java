package net.cystic.lakitu.item;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Slow Falling after dismissing the cloud, lasting until the player lands. The effect is given with a long duration
 * and removed on landing, checked from the item's inventory tick; if the item is dropped mid-fall the effect simply
 * runs out.
 */
public final class DismountSlowFall {
    private static final int MAX_TICKS = 60 * 20;
    private static final Set<UUID> FALLING = ConcurrentHashMap.newKeySet();

    private DismountSlowFall() {}

    public static void grant(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, MAX_TICKS, 0, false, false, true));
        FALLING.add(player.getUUID());
    }

    public static void tick(Player player) {
        if (!FALLING.contains(player.getUUID()))
            return;
        if (player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying || !player.hasEffect(MobEffects.SLOW_FALLING)) {
            FALLING.remove(player.getUUID());
            MobEffectInstance effect = player.getEffect(MobEffects.SLOW_FALLING);
            // Only remove our own effect, not a potion's.
            if (effect != null && effect.getDuration() <= MAX_TICKS && !effect.isVisible())
                player.removeEffect(MobEffects.SLOW_FALLING);
        }
    }
}

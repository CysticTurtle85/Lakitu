package net.cystic.lakitu.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.LakituConfig;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * The state of a Lakitu Cloud while it isn't summoned, stored on its item.
 *
 * @param health    the cloud's health as a fraction of its max health (1 = full)
 * @param stowedAt  game time when {@code health} was recorded; stowed healing counts from here
 * @param reformsAt game time when a dead cloud can be summoned again (0 or past = usable)
 * @param cloudId   identifies this particular cloud, linking the item to its entity; assigned on first summon
 */
public record CloudData(float health, long stowedAt, long reformsAt, Optional<UUID> cloudId) {
    public static final CloudData NEW = new CloudData(1.0F, 0L, 0L, Optional.empty());

    public static final Codec<CloudData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("health", 1.0F).forGetter(CloudData::health),
            Codec.LONG.optionalFieldOf("stowed_at", 0L).forGetter(CloudData::stowedAt),
            Codec.LONG.optionalFieldOf("reforms_at", 0L).forGetter(CloudData::reformsAt),
            UUIDUtil.CODEC.optionalFieldOf("cloud_id").forGetter(CloudData::cloudId)
    ).apply(instance, CloudData::new));

    public static final StreamCodec<ByteBuf, CloudData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CloudData::health,
            ByteBufCodecs.VAR_LONG, CloudData::stowedAt,
            ByteBufCodecs.VAR_LONG, CloudData::reformsAt,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), CloudData::cloudId,
            CloudData::new);

    public static DataComponentType<CloudData> createType() {
        return DataComponentType.<CloudData>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build();
    }

    public boolean isReforming(long gameTime) {
        return gameTime < reformsAt;
    }

    /** Health fraction including what the cloud regained while stowed, up to {@code gameTime}. */
    public float healthAt(long gameTime) {
        LakituConfig config = LakituConfig.values;
        long stowedTicks = Math.max(0L, gameTime - stowedAt);
        double healed = stowedTicks / (double) LakituConfig.ticks(config.cloudStowedHealIntervalSeconds) * config.cloudStowedHealAmount;
        return (float) Math.min(1.0, health + healed / config.cloudMaxHealth);
    }

    public CloudData withHealth(float health, long gameTime) {
        return new CloudData(Math.clamp(health, 0.0F, 1.0F), gameTime, reformsAt, cloudId);
    }

    /** The cloud died: it re-forms at full health once the cooldown is over. */
    public CloudData died(long gameTime, long reformsAt) {
        return new CloudData(1.0F, gameTime, reformsAt, cloudId);
    }

    public CloudData withCloudId(UUID id) {
        return new CloudData(health, stowedAt, reformsAt, Optional.of(id));
    }

    /** Each cloud gets its own cooldown group, so one cloud's cooldown doesn't lock the player's other clouds. */
    public static Identifier cooldownGroup(UUID cloudId) {
        return Lakitu.id("cloud/" + cloudId);
    }
}

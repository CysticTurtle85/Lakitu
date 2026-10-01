package net.cystic.lakitu;

import java.util.List;
import net.minecraft.sounds.SoundEvent;

/**
 * Lakitu's sounds: Mario-style, rebuilt from vanilla sound files in assets/lakitu/sounds.json (pitch and volume), so
 * no audio of Nintendo's is shipped (author, 2026-10-01). Registered by each loader entrypoint from {@link #ALL}.
 */
public final class LakituSounds {
    /** Breeze air, higher: a whoosh overhead. */
    public static final SoundEvent LAKITU_AMBIENT = event("entity.lakitu.ambient");
    /** Turtle hurt and death, higher: a Koopa squeak. */
    public static final SoundEvent LAKITU_HURT = event("entity.lakitu.hurt");
    public static final SoundEvent LAKITU_DEATH = event("entity.lakitu.death");
    /** Small slime, much higher: a springy cartoon "boing" as the egg leaves the hand. */
    public static final SoundEvent SPINY_EGG_THROW = event("entity.spiny_egg.throw");
    /** Turtle egg breaking, higher. */
    public static final SoundEvent SPINY_EGG_CRACK = event("entity.spiny_egg.crack");
    /** Pufferfish puffing up and down: the cloud forming and dispersing. */
    public static final SoundEvent CLOUD_SUMMON = event("entity.lakitu_cloud.summon");
    public static final SoundEvent CLOUD_DISMISS = event("entity.lakitu_cloud.dismiss");
    /** Breeze hurt and a wind burst: a windy cloud. */
    public static final SoundEvent CLOUD_HURT = event("entity.lakitu_cloud.hurt");
    public static final SoundEvent CLOUD_DEATH = event("entity.lakitu_cloud.death");

    public static final List<SoundEvent> ALL = List.of(LAKITU_AMBIENT, LAKITU_HURT, LAKITU_DEATH, SPINY_EGG_THROW,
            SPINY_EGG_CRACK, CLOUD_SUMMON, CLOUD_DISMISS, CLOUD_HURT, CLOUD_DEATH);

    private LakituSounds() {}

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(Lakitu.id(name));
    }
}

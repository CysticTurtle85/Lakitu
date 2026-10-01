package net.cystic.lakitu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Every tunable number, read from {@code config/lakitu.json}. Missing entries keep their defaults and the file is
 * rewritten on load, so new settings appear in old files. The server's values are the ones that count: the cloud's
 * speeds reach clients through the entity, and the item's cooldown through its components.
 */
public final class LakituConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The loaded settings. Replaced as a whole on load, never modified in place. */
    public static LakituConfig values = new LakituConfig();

    /** Bumped when a default changes, so {@link #migrate} can update files that still hold the old default. */
    private static final int CURRENT_VERSION = 3;
    /** Files from before versioning read as 0. */
    public int configVersion = 0;

    // --- Lakitu Cloud (mount) -------------------------------------------------------------------
    public double cloudMaxHealth = 40.0;
    /** Health regained while the cloud is stowed in its item, per {@link #cloudStowedHealIntervalSeconds}. */
    public double cloudStowedHealAmount = 1.0;
    public double cloudStowedHealIntervalSeconds = 10.0;
    /** Blocks per second at full input at sea level, before altitude, Speed and Slowness. */
    public double cloudHorizontalSpeed = 8.0;
    public double cloudVerticalSpeed = 5.0;
    /** How long the item can't be used after the cloud dies. The cloud comes back at full health. */
    public double cloudDeathCooldownSeconds = 30.0;
    /** Anti-spam gap after summoning or dismissing. */
    public double cloudUseCooldownSeconds = 1.0;
    /** Slow Falling until landing after dismissing the cloud with the item (not when the cloud dies). */
    public boolean slowFallingOnDismount = true;

    // --- Rain and water (cloud and Lakitu) -------------------------------------------------------
    /** In rain or water a cloud turns into a grey rain cloud and flies at this much of its speed (it isn't hurt). */
    public double rainCloudSpeed = 0.5;

    // --- Altitude speed while riding --------------------------------------------------------------
    /** The cloud's speed multiplier at the build limit. Normal (1×) at sea level, changing smoothly in between. */
    public double altitudeSpeedAtBuildLimit = 3.0;
    /** The multiplier at the bottom of the world (bedrock), changing smoothly from 1× at sea level. */
    public double altitudeSpeedAtBottom = 0.5;
    /** The Nether and the End ignore height and use these. */
    public double altitudeSpeedInNether = 0.5;
    public double altitudeSpeedInEnd = 3.0;

    // --- Spiny eggs thrown by cloud riders --------------------------------------------------------
    /** Only riders can throw them. Not scaled by difficulty (a player threw it). */
    public double riderSpinyEggDamage = 5.0;
    public double riderSpinyEggCooldownSeconds = 1.0;

    // --- Test option ------------------------------------------------------------------------------
    /** Summoning also launches the player in their look direction, then eases back to normal control. */
    public boolean testLaunchOnSummon = false;
    public double testLaunchSpeed = 20.0;
    public double testLaunchFadeSeconds = 1.0;

    // --- Lakitu (mob) -----------------------------------------------------------------------------
    public double lakituMaxHealth = 30.0;
    /** Scaled by difficulty like other mob projectiles: 3.5 on Easy, 5 on Normal, 7.5 on Hard. */
    public double lakituSpinyEggDamage = 5.0;
    public double lakituThrowIntervalSeconds = 2.0;
    public double lakituThrowRange = 16.0;
    /** Whether a Lakitu fights back when a player riding a cloud attacks it. */
    public boolean lakituRetaliatesAgainstRiders = true;
    /** Natural spawns only at or above this Y (mountain height). */
    public int lakituSpawnMinY = 128;
    /**
     * Chance that a natural spawn attempt that passed every other check goes ahead. 0.8 makes a Lakitu about as likely
     * at a mountain-top spot as a ghast is in Nether Wastes (weight 10 of ~525 × 0.8 vs ghast 50 of 168 × 1/20).
     */
    public double lakituSpawnChance = 0.8;
    /** Cloud item drop when killed by a player: 10% + 2% per Looting level. */
    public double lakituCloudDropChance = 0.1;
    public double lakituCloudDropChancePerLooting = 0.02;
    /** Spiny eggs dropped on death, whoever killed it: min to max, plus this many per Looting level. */
    public int lakituSpinyEggDropMin = 2;
    public int lakituSpinyEggDropMax = 4;
    public int lakituSpinyEggDropPerLooting = 1;

    public static void load(Path configDir) {
        Path file = configDir.resolve(Lakitu.MOD_ID + ".json");
        LakituConfig loaded = null;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, LakituConfig.class);
            } catch (IOException | JsonParseException e) {
                Lakitu.LOGGER.error("Couldn't read {}, using defaults", file, e);
            }
        }
        values = loaded != null ? loaded : new LakituConfig();
        values.migrate();
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(values, writer);
            }
        } catch (IOException e) {
            Lakitu.LOGGER.error("Couldn't write {}", file, e);
        }
    }

    /** A changed default only reaches existing files through here; values someone changed themselves are kept. */
    private void migrate() {
        if (this.configVersion < 2 && this.lakituSpinyEggDamage == 2.0)
            this.lakituSpinyEggDamage = 5.0;
        if (this.configVersion < 3) {
            if (this.lakituMaxHealth == 20.0)
                this.lakituMaxHealth = 30.0;
            if (this.lakituSpawnChance == 0.1)
                this.lakituSpawnChance = 0.8;
            if (this.lakituCloudDropChance == 0.025)
                this.lakituCloudDropChance = 0.1;
            if (this.lakituCloudDropChancePerLooting == 0.01)
                this.lakituCloudDropChancePerLooting = 0.02;
        }
        this.configVersion = CURRENT_VERSION;
    }

    public static int ticks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }
}

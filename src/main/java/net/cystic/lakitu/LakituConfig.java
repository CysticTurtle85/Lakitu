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

    // --- Lakitu Cloud (mount) -------------------------------------------------------------------
    public double cloudMaxHealth = 40.0;
    /** Health regained while the cloud is stowed in its item, per {@link #cloudStowedHealIntervalSeconds}. */
    public double cloudStowedHealAmount = 1.0;
    public double cloudStowedHealIntervalSeconds = 10.0;
    /** Blocks per second at full input, before the altitude bonus. */
    public double cloudHorizontalSpeed = 8.0;
    public double cloudVerticalSpeed = 5.0;
    /** How long the item can't be used after the cloud dies. The cloud comes back at full health. */
    public double cloudDeathCooldownSeconds = 30.0;
    /** Anti-spam gap after summoning or dismissing. */
    public double cloudUseCooldownSeconds = 1.0;
    /** Slow Falling until landing after dismissing the cloud with the item (not when the cloud dies). */
    public boolean slowFallingOnDismount = true;

    // --- Rain (cloud and Lakitu) ----------------------------------------------------------------
    public double rainDamage = 1.0;
    public double rainDamageIntervalSeconds = 2.0;

    // --- Altitude bonus while riding (player and cloud) -------------------------------------------
    /** Overworld (and other dimensions): no bonus at or below this Y... */
    public int altitudeMinY = 64;
    /** ...rising linearly to the full bonus at this Y. */
    public int altitudeMaxY = 320;
    /** Full bonus to max health, as a fraction of base max health (0.5 = +50%). */
    public double altitudeMaxHealthBonus = 0.5;
    /** Full bonus to the cloud's speed, as a fraction (0.5 = +50%). */
    public double altitudeMaxSpeedBonus = 0.5;
    /** Nether and End ignore height and use this share of the full bonus. */
    public double netherBonusShare = 0.5;
    public double endBonusShare = 1.0;

    // --- Test option ------------------------------------------------------------------------------
    /** Summoning also launches the player in their look direction, then eases back to normal control. */
    public boolean testLaunchOnSummon = false;
    public double testLaunchSpeed = 20.0;
    public double testLaunchFadeSeconds = 1.0;

    // --- Lakitu (mob) -----------------------------------------------------------------------------
    public double lakituMaxHealth = 20.0;
    public double lakituSpinyEggDamage = 2.0;
    public double lakituThrowIntervalSeconds = 2.0;
    public double lakituThrowRange = 16.0;
    /** Whether a Lakitu fights back when a player riding a cloud attacks it. */
    public boolean lakituRetaliatesAgainstRiders = true;
    /** Natural spawns only at or above this Y (mountain height). */
    public int lakituSpawnMinY = 128;
    /** Chance that a natural spawn attempt that passed every other check goes ahead. */
    public double lakituSpawnChance = 0.1;
    /** Cloud item drop when killed by a player: like a wither skeleton skull, 2.5% + 1% per Looting level. */
    public double lakituCloudDropChance = 0.025;
    public double lakituCloudDropChancePerLooting = 0.01;

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
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(values, writer);
            }
        } catch (IOException e) {
            Lakitu.LOGGER.error("Couldn't write {}", file, e);
        }
    }

    public static int ticks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }
}

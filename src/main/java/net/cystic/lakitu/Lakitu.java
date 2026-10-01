package net.cystic.lakitu;

import java.nio.file.Path;
import java.util.function.Supplier;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.cystic.lakitu.entity.LakituEntity;
import net.cystic.lakitu.entity.SpinyEggEntity;
import net.cystic.lakitu.item.CloudData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Lakitu {
    public static final String MOD_ID = "lakitu";
    public static final Logger LOGGER = LoggerFactory.getLogger("Lakitu");

    // Set by the loader entrypoint once registered.
    public static Supplier<DataComponentType<CloudData>> cloudData;
    public static Supplier<EntityType<LakituCloudEntity>> cloudEntity;
    public static Supplier<EntityType<LakituEntity>> lakituEntity;
    public static Supplier<EntityType<SpinyEggEntity>> spinyEgg;
    public static Supplier<Item> cloudItem;
    public static Supplier<Item> spawnEgg;
    public static Supplier<Item> spinyEggItem;
    /** Show a cloud rider what their height and the rain do to its speed ({@link CloudSpeedEffect}). */
    public static Supplier<Holder<MobEffect>> altitudeEffect;
    public static Supplier<Holder<MobEffect>> rainCloudEffect;

    private Lakitu() {}

    /** Called by each loader entrypoint before anything is registered. */
    public static void init(Path configDir) {
        LakituConfig.load(configDir);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

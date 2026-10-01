package net.cystic.lakitu;

import java.nio.file.Path;
import java.util.function.Supplier;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.cystic.lakitu.entity.LakituEntity;
import net.cystic.lakitu.entity.SpinyEggEntity;
import net.cystic.lakitu.item.CloudData;
//#if MC >= 1.20.5
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Lakitu {
    public static final String MOD_ID = "lakitu";
    public static final Logger LOGGER = LoggerFactory.getLogger("Lakitu");

    // Set by the loader entrypoint once registered.
    //#if MC >= 1.20.5
    public static Supplier<DataComponentType<CloudData>> cloudData;
    //#endif
    public static Supplier<EntityType<LakituCloudEntity>> cloudEntity;
    public static Supplier<EntityType<LakituEntity>> lakituEntity;
    public static Supplier<EntityType<SpinyEggEntity>> spinyEgg;
    public static Supplier<Item> cloudItem;
    public static Supplier<Item> spawnEgg;
    public static Supplier<Item> spinyEggItem;
    /** Show a cloud rider what their height and the rain do to its speed ({@link CloudSpeedEffect}). */
    //#if MC >= 1.20.5
    public static Supplier<Holder<MobEffect>> altitudeEffect;
    public static Supplier<Holder<MobEffect>> rainCloudEffect;
    //#else
    public static Supplier<MobEffect> altitudeEffect;
    public static Supplier<MobEffect> rainCloudEffect;
    //#endif

    private Lakitu() {}

    /** Called by each loader entrypoint before anything is registered. */
    public static void init(Path configDir) {
        LakituConfig.load(configDir);
    }

    /** A message over the hotbar (the action bar). */
    public static void actionBar(Player player, Component message) {
        //#if MC >= 26.1
        player.sendOverlayMessage(message);
        //#else
        player.displayClientMessage(message, true);
        //#endif
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}

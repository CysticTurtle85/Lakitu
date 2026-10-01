package net.cystic.lakitu.fabric;

import java.util.function.Function;
import net.cystic.lakitu.CloudSpeedEffect;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.LakituSounds;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.cystic.lakitu.entity.LakituEntity;
import net.cystic.lakitu.entity.SpinyEggEntity;
import net.cystic.lakitu.item.CloudData;
import net.cystic.lakitu.item.LakituCloudItem;
import net.cystic.lakitu.item.SpinyEggItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
//#if MC >= 26.1
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
//#else
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
//#endif
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
//#if MC >= 1.20.5
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
//#else
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.SpawnPlacements;
//#endif
import net.fabricmc.loader.api.FabricLoader;
//#if MC >= 1.20.5
import net.minecraft.core.Holder;
//#endif
import net.minecraft.core.Registry;
//#if MC >= 1.20.5
import net.minecraft.core.component.DataComponentType;
//#endif
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
//#if MC >= 1.20.5
import net.minecraft.world.entity.SpawnPlacementTypes;
//#endif
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;

public class LakituFabric implements ModInitializer {
    /** Natural spawn weight in the Overworld's monster pool; rarity is tuned by lakituSpawnChance in the config. */
    private static final int SPAWN_WEIGHT = 10;

    @Override
    public void onInitialize() {
        Lakitu.init(FabricLoader.getInstance().getConfigDir());

        for (SoundEvent sound : LakituSounds.ALL)
            Registry.register(BuiltInRegistries.SOUND_EVENT, LakituSounds.id(sound), sound);

        //#if MC >= 1.20.5
        DataComponentType<CloudData> cloudData = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Lakitu.id("cloud"), CloudData.createType());
        Lakitu.cloudData = () -> cloudData;
        //#endif

        EntityType<LakituCloudEntity> cloud = registerEntity("lakitu_cloud", LakituCloudEntity.builder());
        FabricDefaultAttributeRegistry.register(cloud, LakituCloudEntity.createAttributes());
        //#if MC >= 1.20.5
        EntityType<LakituEntity> lakitu = registerEntity("lakitu", LakituEntity.configure(FabricEntityType.Builder.createMob(LakituEntity::new, MobCategory.MONSTER,
                //#if MC >= 26.1
                mob -> mob.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, LakituEntity::checkLakituSpawnRules)
                //#else
                mob -> mob.spawnRestriction(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, LakituEntity::checkLakituSpawnRules)
                //#endif
                        .defaultAttributes(LakituEntity::createAttributes))));
        //#else
        EntityType<LakituEntity> lakitu = Registry.register(BuiltInRegistries.ENTITY_TYPE, Lakitu.id("lakitu"), FabricEntityTypeBuilder.createMob()
                .entityFactory(LakituEntity::new).spawnGroup(MobCategory.MONSTER).dimensions(EntityDimensions.fixed(1.25F, 2.0F)).trackRangeChunks(10)
                .spawnRestriction(SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, LakituEntity::checkLakituSpawnRules)
                .defaultAttributes(LakituEntity::createAttributes).build());
        //#endif
        EntityType<SpinyEggEntity> spinyEgg = registerEntity("spiny_egg", SpinyEggEntity.builder());
        Lakitu.cloudEntity = () -> cloud;
        Lakitu.lakituEntity = () -> lakitu;
        Lakitu.spinyEgg = () -> spinyEgg;

        Item cloudItem = registerItem("lakitu_cloud", LakituCloudItem::new);
        //#if MC >= 1.21.9
        Item spawnEgg = registerItem("lakitu_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(lakitu)));
        //#elif MC >= 1.21.4
        Item spawnEgg = registerItem("lakitu_spawn_egg", properties -> new SpawnEggItem(lakitu, properties));
        //#else
        Item spawnEgg = registerItem("lakitu_spawn_egg", properties -> new SpawnEggItem(lakitu, 0xFDC934, 0x47A834, properties));
        //#endif
        Item spinyEggItem = registerItem("spiny_egg", SpinyEggItem::new);
        Lakitu.cloudItem = () -> cloudItem;
        Lakitu.spawnEgg = () -> spawnEgg;
        Lakitu.spinyEggItem = () -> spinyEggItem;

        //#if MC >= 1.20.5
        Holder<MobEffect> altitude = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Lakitu.id("altitude"),
                new CloudSpeedEffect(MobEffectCategory.BENEFICIAL, 0x9ED8FF));
        Holder<MobEffect> rainCloud = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Lakitu.id("rain_cloud"),
                new CloudSpeedEffect(MobEffectCategory.HARMFUL, 0x767D88));
        //#else
        MobEffect altitude = Registry.register(BuiltInRegistries.MOB_EFFECT, Lakitu.id("altitude"), new CloudSpeedEffect(MobEffectCategory.BENEFICIAL, 0x9ED8FF));
        MobEffect rainCloud = Registry.register(BuiltInRegistries.MOB_EFFECT, Lakitu.id("rain_cloud"), new CloudSpeedEffect(MobEffectCategory.HARMFUL, 0x767D88));
        //#endif
        Lakitu.altitudeEffect = () -> altitude;
        Lakitu.rainCloudEffect = () -> rainCloud;

        //#if MC >= 26.1
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.SADDLE, cloudItem));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.insertAfter(Items.GHAST_SPAWN_EGG, spawnEgg));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.BLUE_EGG, spinyEggItem));
        //#else
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.addAfter(Items.SADDLE, cloudItem));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.addAfter(Items.GHAST_SPAWN_EGG, spawnEgg));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> entries.addAfter(Items.EGG, spinyEggItem));
        //#endif

        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, lakitu, SPAWN_WEIGHT, 1, 1);
    }

    private static <T extends Entity> EntityType<T> registerEntity(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Lakitu.id(name));
        //#if MC >= 1.21.2
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
        //#else
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(Lakitu.id(name).toString()));
        //#endif
    }

    private static Item registerItem(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Lakitu.id(name));
        //#if MC >= 1.21.2
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
        //#else
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties()));
        //#endif
    }
}

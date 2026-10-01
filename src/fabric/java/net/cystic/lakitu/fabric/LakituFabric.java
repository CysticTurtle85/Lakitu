package net.cystic.lakitu.fabric;

import java.util.function.Function;
import net.cystic.lakitu.CloudSpeedEffect;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.cystic.lakitu.entity.LakituEntity;
import net.cystic.lakitu.entity.SpinyEggEntity;
import net.cystic.lakitu.item.CloudData;
import net.cystic.lakitu.item.LakituCloudItem;
import net.cystic.lakitu.item.SpinyEggItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
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

        DataComponentType<CloudData> cloudData = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Lakitu.id("cloud"), CloudData.createType());
        Lakitu.cloudData = () -> cloudData;

        EntityType<LakituCloudEntity> cloud = registerEntity("lakitu_cloud", LakituCloudEntity.builder());
        FabricDefaultAttributeRegistry.register(cloud, LakituCloudEntity.createAttributes());
        EntityType<LakituEntity> lakitu = registerEntity("lakitu", LakituEntity.configure(FabricEntityType.Builder.createMob(LakituEntity::new, MobCategory.MONSTER,
                mob -> mob.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, LakituEntity::checkLakituSpawnRules)
                        .defaultAttributes(LakituEntity::createAttributes))));
        EntityType<SpinyEggEntity> spinyEgg = registerEntity("spiny_egg", SpinyEggEntity.builder());
        Lakitu.cloudEntity = () -> cloud;
        Lakitu.lakituEntity = () -> lakitu;
        Lakitu.spinyEgg = () -> spinyEgg;

        Item cloudItem = registerItem("lakitu_cloud", LakituCloudItem::new);
        Item spawnEgg = registerItem("lakitu_spawn_egg", properties -> new SpawnEggItem(properties.spawnEgg(lakitu)));
        Item spinyEggItem = registerItem("spiny_egg", SpinyEggItem::new);
        Lakitu.cloudItem = () -> cloudItem;
        Lakitu.spawnEgg = () -> spawnEgg;
        Lakitu.spinyEggItem = () -> spinyEggItem;

        Holder<MobEffect> altitude = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Lakitu.id("altitude"),
                new CloudSpeedEffect(MobEffectCategory.BENEFICIAL, 0x9ED8FF));
        Holder<MobEffect> rainCloud = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Lakitu.id("rain_cloud"),
                new CloudSpeedEffect(MobEffectCategory.HARMFUL, 0x767D88));
        Lakitu.altitudeEffect = () -> altitude;
        Lakitu.rainCloudEffect = () -> rainCloud;

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.SADDLE, cloudItem));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.insertAfter(Items.GHAST_SPAWN_EGG, spawnEgg));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.BLUE_EGG, spinyEggItem));

        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, lakitu, SPAWN_WEIGHT, 1, 1);
    }

    private static <T extends Entity> EntityType<T> registerEntity(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Lakitu.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    private static Item registerItem(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Lakitu.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
    }
}

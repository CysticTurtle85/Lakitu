package net.cystic.lakitu.neoforge;

import net.cystic.lakitu.CloudSpeedEffect;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.cystic.lakitu.entity.LakituEntity;
import net.cystic.lakitu.entity.SpinyEggEntity;
import net.cystic.lakitu.item.CloudData;
import net.cystic.lakitu.item.LakituCloudItem;
import net.cystic.lakitu.item.SpinyEggItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Natural spawns are added by data/lakitu/neoforge/biome_modifier/lakitu.json. */
@Mod(Lakitu.MOD_ID)
public class LakituNeoForge {
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Lakitu.MOD_ID);
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Lakitu.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Lakitu.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Lakitu.MOD_ID);

    private static final DeferredHolder<DataComponentType<?>, DataComponentType<CloudData>> CLOUD_DATA = COMPONENTS.register("cloud", CloudData::createType);
    private static final DeferredHolder<EntityType<?>, EntityType<LakituCloudEntity>> CLOUD = ENTITIES.register("lakitu_cloud",
            id -> LakituCloudEntity.builder().build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    private static final DeferredHolder<EntityType<?>, EntityType<LakituEntity>> LAKITU = ENTITIES.registerEntityType("lakitu",
            LakituEntity::new, MobCategory.MONSTER, LakituEntity::configure);
    private static final DeferredHolder<EntityType<?>, EntityType<SpinyEggEntity>> SPINY_EGG = ENTITIES.register("spiny_egg",
            id -> SpinyEggEntity.builder().build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    private static final DeferredItem<LakituCloudItem> CLOUD_ITEM = ITEMS.registerItem("lakitu_cloud", LakituCloudItem::new);
    private static final DeferredItem<SpawnEggItem> SPAWN_EGG = ITEMS.registerItem("lakitu_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(LAKITU.get())));
    private static final DeferredItem<SpinyEggItem> SPINY_EGG_ITEM = ITEMS.registerItem("spiny_egg", SpinyEggItem::new);
    private static final DeferredHolder<MobEffect, CloudSpeedEffect> ALTITUDE = EFFECTS.register("altitude",
            () -> new CloudSpeedEffect(MobEffectCategory.BENEFICIAL, 0x9ED8FF));
    private static final DeferredHolder<MobEffect, CloudSpeedEffect> RAIN_CLOUD = EFFECTS.register("rain_cloud",
            () -> new CloudSpeedEffect(MobEffectCategory.HARMFUL, 0x767D88));

    public LakituNeoForge(IEventBus modBus) {
        Lakitu.init(FMLPaths.CONFIGDIR.get());
        Lakitu.cloudData = CLOUD_DATA::get;
        Lakitu.cloudEntity = CLOUD::get;
        Lakitu.lakituEntity = LAKITU::get;
        Lakitu.spinyEgg = SPINY_EGG::get;
        Lakitu.cloudItem = CLOUD_ITEM::get;
        Lakitu.spawnEgg = SPAWN_EGG::get;
        Lakitu.spinyEggItem = SPINY_EGG_ITEM::get;
        Lakitu.altitudeEffect = () -> ALTITUDE;
        Lakitu.rainCloudEffect = () -> RAIN_CLOUD;

        COMPONENTS.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(LakituNeoForge::createAttributes);
        modBus.addListener(LakituNeoForge::registerSpawnPlacements);
        modBus.addListener(LakituNeoForge::addToCreativeTabs);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(CLOUD.get(), LakituCloudEntity.createAttributes().build());
        event.put(LAKITU.get(), LakituEntity.createAttributes().build());
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(LAKITU.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LakituEntity::checkLakituSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            event.insertAfter(new ItemStack(Items.SADDLE), new ItemStack(CLOUD_ITEM.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS)
            event.insertAfter(new ItemStack(Items.GHAST_SPAWN_EGG), new ItemStack(SPAWN_EGG.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        else if (event.getTabKey() == CreativeModeTabs.COMBAT)
            event.insertAfter(new ItemStack(Items.BLUE_EGG), new ItemStack(SPINY_EGG_ITEM.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}

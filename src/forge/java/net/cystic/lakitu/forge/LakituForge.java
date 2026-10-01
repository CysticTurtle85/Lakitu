package net.cystic.lakitu.forge;

import net.cystic.lakitu.CloudSpeedEffect;
import net.cystic.lakitu.Lakitu;
import net.cystic.lakitu.LakituSounds;
import net.cystic.lakitu.client.LakituCloudRenderer;
import net.cystic.lakitu.client.LakituRenderer;
import net.cystic.lakitu.client.SpinyEggRenderer;
import net.cystic.lakitu.entity.LakituCloudEntity;
import net.cystic.lakitu.entity.LakituEntity;
import net.cystic.lakitu.entity.SpinyEggEntity;
import net.cystic.lakitu.item.LakituCloudItem;
import net.cystic.lakitu.item.SpinyEggItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge (1.20.1) entrypoint. Natural spawns are added by data/lakitu/forge/biome_modifier/lakitu.json. */
@Mod(Lakitu.MOD_ID)
public class LakituForge {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Lakitu.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Lakitu.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Lakitu.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Lakitu.MOD_ID);

    static {
        for (SoundEvent sound : LakituSounds.ALL)
            SOUNDS.register(LakituSounds.id(sound).getPath(), () -> sound);
    }

    private static final RegistryObject<EntityType<LakituCloudEntity>> CLOUD = ENTITIES.register("lakitu_cloud",
            () -> LakituCloudEntity.builder().build(Lakitu.id("lakitu_cloud").toString()));
    private static final RegistryObject<EntityType<LakituEntity>> LAKITU = ENTITIES.register("lakitu",
            () -> LakituEntity.configure(EntityType.Builder.of(LakituEntity::new, MobCategory.MONSTER)).build(Lakitu.id("lakitu").toString()));
    private static final RegistryObject<EntityType<SpinyEggEntity>> SPINY_EGG = ENTITIES.register("spiny_egg",
            () -> SpinyEggEntity.builder().build(Lakitu.id("spiny_egg").toString()));
    private static final RegistryObject<Item> CLOUD_ITEM = ITEMS.register("lakitu_cloud", () -> new LakituCloudItem(new Item.Properties()));
    private static final RegistryObject<Item> SPAWN_EGG = ITEMS.register("lakitu_spawn_egg",
            () -> new ForgeSpawnEggItem(LAKITU, 0xFDC934, 0x47A834, new Item.Properties()));
    private static final RegistryObject<Item> SPINY_EGG_ITEM = ITEMS.register("spiny_egg", () -> new SpinyEggItem(new Item.Properties()));
    private static final RegistryObject<MobEffect> ALTITUDE = EFFECTS.register("altitude",
            () -> new CloudSpeedEffect(MobEffectCategory.BENEFICIAL, 0x9ED8FF));
    private static final RegistryObject<MobEffect> RAIN_CLOUD = EFFECTS.register("rain_cloud",
            () -> new CloudSpeedEffect(MobEffectCategory.HARMFUL, 0x767D88));

    public LakituForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Lakitu.init(FMLPaths.CONFIGDIR.get());
        Lakitu.cloudEntity = CLOUD;
        Lakitu.lakituEntity = LAKITU;
        Lakitu.spinyEgg = SPINY_EGG;
        Lakitu.cloudItem = CLOUD_ITEM;
        Lakitu.spawnEgg = SPAWN_EGG;
        Lakitu.spinyEggItem = SPINY_EGG_ITEM;
        Lakitu.altitudeEffect = ALTITUDE;
        Lakitu.rainCloudEffect = RAIN_CLOUD;

        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(LakituForge::createAttributes);
        modBus.addListener(LakituForge::registerSpawnPlacements);
        modBus.addListener(LakituForge::addToCreativeTabs);
        if (FMLEnvironment.dist == Dist.CLIENT)
            Client.register(modBus);
    }

    private static void createAttributes(EntityAttributeCreationEvent event) {
        event.put(CLOUD.get(), LakituCloudEntity.createAttributes().build());
        event.put(LAKITU.get(), LakituEntity.createAttributes().build());
    }

    private static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(LAKITU.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LakituEntity::checkLakituSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES)
            event.getEntries().putAfter(new ItemStack(Items.SADDLE), new ItemStack(CLOUD_ITEM.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS)
            event.getEntries().putAfter(new ItemStack(Items.GHAST_SPAWN_EGG), new ItemStack(SPAWN_EGG.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        else if (event.getTabKey() == CreativeModeTabs.COMBAT)
            event.getEntries().putAfter(new ItemStack(Items.EGG), new ItemStack(SPINY_EGG_ITEM.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    /** Client-only listeners, registered only on the client so dedicated servers never load renderer classes. */
    private static final class Client {
        static void register(IEventBus modBus) {
            modBus.addListener(Client::registerRenderers);
        }

        static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(CLOUD.get(), context -> new LakituCloudRenderer<>(context));
            event.registerEntityRenderer(LAKITU.get(), context -> new LakituRenderer<>(context));
            event.registerEntityRenderer(SPINY_EGG.get(), context -> new SpinyEggRenderer<>(context));
        }
    }
}

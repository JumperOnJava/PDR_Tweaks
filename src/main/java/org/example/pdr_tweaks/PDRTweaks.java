package org.example.pdr_tweaks;

import com.simibubi.create.AllCreativeModeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.example.pdr_tweaks.registry.PDRBlockEntities;
import org.example.pdr_tweaks.registry.PDRBlocks;
import org.example.pdr_tweaks.registry.PDRMenus;
import org.example.pdr_tweaks.registry.PDRNetwork;

@Mod(PDRTweaks.MODID)
public class PDRTweaks {
    public static final String MODID = "pdr_tweaks";

    public PDRTweaks(IEventBus modEventBus) {
        PDRBlocks.BLOCKS.register(modEventBus);
        PDRBlocks.ITEMS.register(modEventBus);
        PDRBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        PDRMenus.MENUS.register(modEventBus);
        PDRNetwork.register(modEventBus);

        modEventBus.addListener(PDRTweaks::onBuildCreativeTabContents);
    }

    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> createBaseTab = AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey();
        if (event.getTabKey().equals(createBaseTab)) {
            event.accept(PDRBlocks.SMART_GEARSHIFT_ITEM.get());
        }
    }
}

package org.example.pdr_tweaks.client;

import com.simibubi.create.content.kinetics.transmission.SplitShaftRenderer;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.example.pdr_tweaks.PDRTweaks;
import org.example.pdr_tweaks.content.kinetics.SmartGearshiftBlockEntity;
import org.example.pdr_tweaks.registry.PDRBlockEntities;
import org.example.pdr_tweaks.registry.PDRBlocks;
import org.example.pdr_tweaks.registry.PDRMenus;

@Mod(value = PDRTweaks.MODID, dist = Dist.CLIENT)
public class PDRClient {

    public PDRClient(IEventBus modEventBus) {
        modEventBus.addListener(PDRClient::onRegisterScreens);
        modEventBus.addListener(PDRClient::onClientSetup);
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(PDRMenus.SMART_GEARSHIFT.get(), SmartGearshiftScreen::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Render layer for overlay transparency
            ItemBlockRenderTypes.setRenderLayer(PDRBlocks.SMART_GEARSHIFT.get(), RenderType.cutoutMipped());
            // Fallback BER for shaft rendering (when Flywheel isn't active)
            BlockEntityRenderers.register(PDRBlockEntities.SMART_GEARSHIFT.get(), SplitShaftRenderer::new);
        });
        // Flywheel visual for shaft rendering (per-direction speed via getRotationSpeedModifier)
        SimpleBlockEntityVisualizer.builder(PDRBlockEntities.SMART_GEARSHIFT.get())
                .factory(SplitShaftVisual::new)
                .skipVanillaRender(be -> false)
                .apply();
    }
}

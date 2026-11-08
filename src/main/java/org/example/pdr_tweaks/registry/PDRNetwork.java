package org.example.pdr_tweaks.registry;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.example.pdr_tweaks.PDRTweaks;
import org.example.pdr_tweaks.network.SmartGearshiftConfigPacket;

public class PDRNetwork {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(PDRNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PDRTweaks.MODID);
        registrar.playToServer(SmartGearshiftConfigPacket.TYPE, SmartGearshiftConfigPacket.STREAM_CODEC, SmartGearshiftConfigPacket::handle);
    }
}

package org.example.pdr_tweaks.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.example.pdr_tweaks.PDRTweaks;
import org.example.pdr_tweaks.content.kinetics.SmartGearshiftBlockEntity;
import org.example.pdr_tweaks.content.kinetics.SmartGearshiftMode;
import org.jetbrains.annotations.NotNull;

public record SmartGearshiftConfigPacket(
        BlockPos pos,
        Action action,
        SmartGearshiftMode mode,
        ItemStack forwardFirst,
        ItemStack forwardLast,
        ItemStack reverseFirst,
        ItemStack reverseLast
) implements CustomPacketPayload {

    public enum Action {
        SET_MODE,
        SWAP,
        SET_FREQUENCIES
    }

    public static final Type<SmartGearshiftConfigPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PDRTweaks.MODID, "smart_gearshift_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartGearshiftConfigPacket> STREAM_CODEC =
            StreamCodec.of(SmartGearshiftConfigPacket::write, SmartGearshiftConfigPacket::read);

    private static SmartGearshiftConfigPacket read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Action action = buf.readEnum(Action.class);
        SmartGearshiftMode mode = buf.readEnum(SmartGearshiftMode.class);
        ItemStack forwardFirst = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack forwardLast = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack reverseFirst = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ItemStack reverseLast = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return new SmartGearshiftConfigPacket(pos, action, mode, forwardFirst, forwardLast, reverseFirst, reverseLast);
    }

    private static void write(RegistryFriendlyByteBuf buf, SmartGearshiftConfigPacket packet) {
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.action);
        buf.writeEnum(packet.mode);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.forwardFirst);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.forwardLast);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.reverseFirst);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, packet.reverseLast);
    }

    public static void handle(SmartGearshiftConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            if (serverPlayer.level().getBlockEntity(packet.pos) instanceof SmartGearshiftBlockEntity be) {
                switch (packet.action) {
                    case SET_MODE -> be.setMode(packet.mode);
                    case SWAP -> be.swapFrequencies();
                    case SET_FREQUENCIES -> {
                        be.setForwardFrequency(true, packet.forwardFirst);
                        be.setForwardFrequency(false, packet.forwardLast);
                        be.setReverseFrequency(true, packet.reverseFirst);
                        be.setReverseFrequency(false, packet.reverseLast);
                    }
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Convenience factory methods
    public static SmartGearshiftConfigPacket setMode(BlockPos pos, SmartGearshiftMode mode) {
        return new SmartGearshiftConfigPacket(pos, Action.SET_MODE, mode,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    public static SmartGearshiftConfigPacket swap(BlockPos pos) {
        return new SmartGearshiftConfigPacket(pos, Action.SWAP, SmartGearshiftMode.SUM,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    public static SmartGearshiftConfigPacket setFrequencies(BlockPos pos,
            ItemStack forwardFirst, ItemStack forwardLast,
            ItemStack reverseFirst, ItemStack reverseLast) {
        return new SmartGearshiftConfigPacket(pos, Action.SET_FREQUENCIES, SmartGearshiftMode.SUM,
                forwardFirst, forwardLast, reverseFirst, reverseLast);
    }
}

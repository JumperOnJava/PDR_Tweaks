package org.example.pdr_tweaks.content.kinetics;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntConsumer;

/**
 * A single BlockEntityBehaviour that manages TWO redstone link receiver channels
 * (forward + reverse) on the same block entity. Each channel is registered separately
 * with the RedstoneLinkNetworkHandler as its own IRedstoneLinkable.
 */
public class DualLinkBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<DualLinkBehaviour> TYPE = new BehaviourType<>("dual_link");

    private final Channel forwardChannel;
    private final Channel reverseChannel;

    public DualLinkBehaviour(SmartBlockEntity be, IntConsumer forwardCallback, IntConsumer reverseCallback) {
        super(be);
        this.forwardChannel = new Channel(true, forwardCallback);
        this.reverseChannel = new Channel(false, reverseCallback);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public void initialize() {
        super.initialize();
        if (getWorld() == null || getWorld().isClientSide) return;
        getHandler().addToNetwork(getWorld(), forwardChannel);
        getHandler().addToNetwork(getWorld(), reverseChannel);
    }

    @Override
    public void unload() {
        super.unload();
        if (getWorld() == null || getWorld().isClientSide) return;
        getHandler().removeFromNetwork(getWorld(), forwardChannel);
        getHandler().removeFromNetwork(getWorld(), reverseChannel);
    }

    @Override
    public boolean isSafeNBT() {
        return true;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.put("ForwardFirst", forwardChannel.frequencyFirst.getStack().saveOptional(registries));
        nbt.put("ForwardLast", forwardChannel.frequencyLast.getStack().saveOptional(registries));
        nbt.put("ReverseFirst", reverseChannel.frequencyFirst.getStack().saveOptional(registries));
        nbt.put("ReverseLast", reverseChannel.frequencyLast.getStack().saveOptional(registries));
        nbt.putLong("LastKnownPosition", blockEntity.getBlockPos().asLong());
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        forwardChannel.frequencyFirst = Frequency.of(ItemStack.parseOptional(registries, nbt.getCompound("ForwardFirst")));
        forwardChannel.frequencyLast = Frequency.of(ItemStack.parseOptional(registries, nbt.getCompound("ForwardLast")));
        reverseChannel.frequencyFirst = Frequency.of(ItemStack.parseOptional(registries, nbt.getCompound("ReverseFirst")));
        reverseChannel.frequencyLast = Frequency.of(ItemStack.parseOptional(registries, nbt.getCompound("ReverseLast")));
        forwardChannel.newPosition = blockEntity.getBlockPos().asLong() != nbt.getLong("LastKnownPosition");
        reverseChannel.newPosition = forwardChannel.newPosition;
    }

    // === Public API for frequency management ===

    public ItemStack getForwardFrequency(boolean first) {
        return first ? forwardChannel.frequencyFirst.getStack() : forwardChannel.frequencyLast.getStack();
    }

    public ItemStack getReverseFrequency(boolean first) {
        return first ? reverseChannel.frequencyFirst.getStack() : reverseChannel.frequencyLast.getStack();
    }

    public void setForwardFrequency(boolean first, ItemStack stack) {
        setChannelFrequency(forwardChannel, first, stack);
    }

    public void setReverseFrequency(boolean first, ItemStack stack) {
        setChannelFrequency(reverseChannel, first, stack);
    }

    public void swapFrequencies() {
        Frequency fwdFirst = forwardChannel.frequencyFirst;
        Frequency fwdLast = forwardChannel.frequencyLast;
        Frequency revFirst = reverseChannel.frequencyFirst;
        Frequency revLast = reverseChannel.frequencyLast;

        setChannelFrequencyRaw(forwardChannel, true, revFirst);
        setChannelFrequencyRaw(forwardChannel, false, revLast);
        setChannelFrequencyRaw(reverseChannel, true, fwdFirst);
        setChannelFrequencyRaw(reverseChannel, false, fwdLast);

        if (getWorld() != null && !getWorld().isClientSide) {
            getHandler().addToNetwork(getWorld(), forwardChannel);
            getHandler().addToNetwork(getWorld(), reverseChannel);
        }
        blockEntity.sendData();
    }

    private void setChannelFrequency(Channel channel, boolean first, ItemStack stack) {
        stack = stack.copy();
        stack.setCount(1);
        ItemStack toCompare = first ? channel.frequencyFirst.getStack() : channel.frequencyLast.getStack();
        boolean changed = !ItemStack.isSameItemSameComponents(stack, toCompare);
        if (changed && getWorld() != null && !getWorld().isClientSide) {
            getHandler().removeFromNetwork(getWorld(), channel);
        }
        if (first) channel.frequencyFirst = Frequency.of(stack);
        else channel.frequencyLast = Frequency.of(stack);
        if (changed && getWorld() != null && !getWorld().isClientSide) {
            getHandler().addToNetwork(getWorld(), channel);
        }
        blockEntity.sendData();
    }

    private void setChannelFrequencyRaw(Channel channel, boolean first, Frequency freq) {
        if (getWorld() != null && !getWorld().isClientSide) {
            getHandler().removeFromNetwork(getWorld(), channel);
        }
        if (first) channel.frequencyFirst = freq;
        else channel.frequencyLast = freq;
    }

    private RedstoneLinkNetworkHandler getHandler() {
        return Create.REDSTONE_LINK_NETWORK_HANDLER;
    }

    /**
     * Internal IRedstoneLinkable implementation for one channel (forward or reverse).
     */
    private class Channel implements IRedstoneLinkable {
        final boolean forward;
        final IntConsumer signalCallback;
        Frequency frequencyFirst = Frequency.EMPTY;
        Frequency frequencyLast = Frequency.EMPTY;
        boolean newPosition = true;

        Channel(boolean forward, IntConsumer signalCallback) {
            this.forward = forward;
            this.signalCallback = signalCallback;
        }

        @Override
        public int getTransmittedStrength() {
            return 0; // receiver only
        }

        @Override
        public void setReceivedStrength(int power) {
            signalCallback.accept(power);
        }

        @Override
        public boolean isListening() {
            return true; // always receiver
        }

        @Override
        public boolean isAlive() {
            var level = getWorld();
            var pos = getPos();
            if (blockEntity.isRemoved()) return false;
            if (level == null || !level.isLoaded(pos)) return false;
            return level.getBlockEntity(pos) == blockEntity;
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return Couple.create(frequencyFirst, frequencyLast);
        }

        @Override
        public BlockPos getLocation() {
            return getPos();
        }
    }
}

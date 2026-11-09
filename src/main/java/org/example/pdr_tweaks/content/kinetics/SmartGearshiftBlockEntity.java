package org.example.pdr_tweaks.content.kinetics;

import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.ticks.TickPriority;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SmartGearshiftBlockEntity extends SplitShaftBlockEntity {

    private SmartGearshiftMode mode = SmartGearshiftMode.SUM;
    private int forwardSignal = 0;
    private int reverseSignal = 0;
    private boolean directPower = false;
    private Boolean lastActiveForward = null;
    private boolean prevForwardActive = false;
    private boolean prevReverseActive = false;

    protected DualLinkBehaviour linkBehaviour;

    public SmartGearshiftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        linkBehaviour = new DualLinkBehaviour(this, this::onForwardSignal, this::onReverseSignal);
        behaviours.add(linkBehaviour);
    }

    private void onForwardSignal(int power) {
        forwardSignal = power;
        onSignalChanged();
    }

    private void onReverseSignal(int power) {
        reverseSignal = power;
        onSignalChanged();
    }

    public void onRedstoneChanged(boolean powered) {
        directPower = powered;
        onSignalChanged();
    }

    /**
     * Computes the output direction from current signals + mode.
     */
    private SmartGearshiftDirection computeDirection() {
        boolean forward = forwardSignal > 0 || directPower;
        boolean reverse = reverseSignal > 0;
        if (!forward && !reverse) return SmartGearshiftDirection.INACTIVE;

        return switch (mode) {
            case SUM -> {
                if (forward && reverse) yield SmartGearshiftDirection.INACTIVE;
                yield reverse ? SmartGearshiftDirection.REVERSE : SmartGearshiftDirection.ACTIVE;
            }
            case LATEST -> {
                // Latest ACTIVE input wins; if it dropped out, the remaining active input takes over.
                if (forward && reverse)
                    yield lastActiveForward == null || lastActiveForward
                            ? SmartGearshiftDirection.ACTIVE : SmartGearshiftDirection.REVERSE;
                yield reverse ? SmartGearshiftDirection.REVERSE : SmartGearshiftDirection.ACTIVE;
            }
            case POWER_REVERSE ->
                    !forward ? SmartGearshiftDirection.INACTIVE : reverse ? SmartGearshiftDirection.REVERSE : SmartGearshiftDirection.ACTIVE;
        };
    }

    private void onSignalChanged() {
        boolean forwardActive = forwardSignal > 0 || directPower;
        boolean reverseActive = reverseSignal > 0;

        // Rising-edge tracking: the most recently activated input becomes "latest".
        // Reverse wins if both rise in the same tick.
        if (forwardActive && !prevForwardActive) lastActiveForward = true;
        if (reverseActive && !prevReverseActive) lastActiveForward = false;
        prevForwardActive = forwardActive;
        prevReverseActive = reverseActive;

        if (level == null || level.isClientSide) return;
        sendData();
        applyDirectionChange();
    }

    /**
     * Detaches kinetics BEFORE changing the blockstate direction, then schedules
     * re-attachment next tick. This prevents Create's kinetic conflict (two shafts
     * in the same network spinning opposite directions) from breaking the block.
     */
    private void applyDirectionChange() {
        if (level == null || level.isClientSide) return;

        BlockState state = getBlockState();
        SmartGearshiftDirection dir = computeDirection();

        BlockState newState = state;
        if (state.hasProperty(SmartGearshiftBlock.DIRECTION) && state.getValue(SmartGearshiftBlock.DIRECTION) != dir) {
            newState = newState.setValue(SmartGearshiftBlock.DIRECTION, dir);
        }
        if (state.hasProperty(SmartGearshiftBlock.MODE) && state.getValue(SmartGearshiftBlock.MODE) != mode) {
            newState = newState.setValue(SmartGearshiftBlock.MODE, mode);
        }

        if (newState == state) return; // nothing changed

        // Detach from kinetic network first so the direction flip doesn't conflict
        if (hasSource() || getSpeed() != 0) {
            detachKinetics();
        }

        level.setBlockAndUpdate(worldPosition, newState);

        // Schedule re-attachment next tick (same pattern as vanilla GearshiftBlock)
        updateSpeed = true;
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(worldPosition, state.getBlock(), 1, TickPriority.EXTREMELY_HIGH);
        }
    }

    /**
     * Called by the block's tick() when the scheduled tick fires.
     * Re-attaches kinetics with the new direction.
     */
    public void reattachKinetics() {
        if (level != null && !level.isClientSide) {
            updateSpeed = true;
        }
    }

    /**
     * Speed modifier is driven entirely by the blockstate DIRECTION:
     * INACTIVE → 0 (no output), ACTIVE → 1 (forward), REVERSE → -1 (reverse).
     */
    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (!hasSource() || face == getSourceFacing()) {
            return 1;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(SmartGearshiftBlock.DIRECTION)) return 1;
        return switch (state.getValue(SmartGearshiftBlock.DIRECTION)) {
            case INACTIVE -> 0f;
            case ACTIVE -> 1f;
            case REVERSE -> -1f;
        };
    }

    @Override
    public void tick() {
        super.tick();
        // Safety net: if signals changed but blockstate wasn't updated (e.g. chunk load edge case),
        // re-check every 10 ticks
        if (level != null && !level.isClientSide && level.getGameTime() % 10 == 0) {
            BlockState state = getBlockState();
            SmartGearshiftDirection dir = computeDirection();
            if (state.hasProperty(SmartGearshiftBlock.DIRECTION) && state.getValue(SmartGearshiftBlock.DIRECTION) != dir) {
                applyDirectionChange();
            }
        }
    }

    public SmartGearshiftMode getMode() { return mode; }

    public void setMode(SmartGearshiftMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            lastActiveForward = null;
            onSignalChanged();
        }
    }

    public void swapFrequencies() {
        if (linkBehaviour != null) linkBehaviour.swapFrequencies();
    }

    public int getForwardSignal() { return forwardSignal; }
    public int getReverseSignal() { return reverseSignal; }

    public ItemStack getForwardFrequency(boolean first) {
        return linkBehaviour != null ? linkBehaviour.getForwardFrequency(first) : ItemStack.EMPTY;
    }

    public ItemStack getReverseFrequency(boolean first) {
        return linkBehaviour != null ? linkBehaviour.getReverseFrequency(first) : ItemStack.EMPTY;
    }

    public void setForwardFrequency(boolean first, ItemStack stack) {
        if (linkBehaviour != null) linkBehaviour.setForwardFrequency(first, stack);
    }

    public void setReverseFrequency(boolean first, ItemStack stack) {
        if (linkBehaviour != null) linkBehaviour.setReverseFrequency(first, stack);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            // Sync blockstate on load without detaching (no network yet)
            BlockState state = getBlockState();
            SmartGearshiftDirection dir = computeDirection();
            if (state.hasProperty(SmartGearshiftBlock.DIRECTION) && state.getValue(SmartGearshiftBlock.DIRECTION) != dir) {
                level.setBlockAndUpdate(worldPosition, state.setValue(SmartGearshiftBlock.DIRECTION, dir));
            }
            if (state.hasProperty(SmartGearshiftBlock.MODE) && state.getValue(SmartGearshiftBlock.MODE) != mode) {
                level.setBlockAndUpdate(worldPosition, getBlockState().setValue(SmartGearshiftBlock.MODE, mode));
            }
        }
    }

    @Override
    protected void write(@NotNull CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putString("Mode", mode.getSerializedName());
        compound.putInt("ForwardSignal", forwardSignal);
        compound.putInt("ReverseSignal", reverseSignal);
        compound.putBoolean("DirectPower", directPower);
        if (lastActiveForward != null) compound.putBoolean("LastActiveForward", lastActiveForward);
    }

    @Override
    protected void read(@NotNull CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        String modeName = compound.getString("Mode");
        mode = SmartGearshiftMode.SUM;
        for (SmartGearshiftMode m : SmartGearshiftMode.values()) {
            if (m.getSerializedName().equals(modeName)) { mode = m; break; }
        }
        forwardSignal = compound.getInt("ForwardSignal");
        reverseSignal = compound.getInt("ReverseSignal");
        directPower = compound.getBoolean("DirectPower");
        lastActiveForward = compound.contains("LastActiveForward") ? compound.getBoolean("LastActiveForward") : null;
    }
}

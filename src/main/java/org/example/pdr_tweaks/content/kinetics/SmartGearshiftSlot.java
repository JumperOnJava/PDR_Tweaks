package org.example.pdr_tweaks.content.kinetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * ValueBoxTransform for the two frequency display slots on the smart gearshift block.
 * Slots are placed on the top face of the block, side by side.
 */
public class SmartGearshiftSlot extends ValueBoxTransform.Dual {

    public SmartGearshiftSlot(boolean first) {
        super(first);
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        // Place on top face, two slots side by side along X axis
        float xOffset = isFirst() ? 5.5f : 10.5f;
        Vec3 location = VecHelper.voxelSpace(xOffset, 0.5f, 8f);

        // Rotate based on the block's axis
        Direction.Axis axis = state.hasProperty(BlockStateProperties.AXIS)
                ? state.getValue(BlockStateProperties.AXIS)
                : Direction.Axis.Y;

        return switch (axis) {
            case X -> VecHelper.rotateCentered(location, 90, Axis.Z);
            case Z -> VecHelper.rotateCentered(location, 90, Axis.X);
            default -> location;
        };
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction.Axis axis = state.hasProperty(BlockStateProperties.AXIS)
                ? state.getValue(BlockStateProperties.AXIS)
                : Direction.Axis.Y;
        float yRot = 0;
        float xRot = 90;
        switch (axis) {
            case X -> {
                yRot = 90;
                xRot = 90;
            }
            case Z -> {
                yRot = 180;
                xRot = 90;
            }
            default -> {
                yRot = 0;
                xRot = 90;
            }
        }
        TransformStack.of(ms)
                .rotateYDegrees(yRot)
                .rotateXDegrees(xRot);
    }

    @Override
    public float getScale() {
        return 0.4975f;
    }
}

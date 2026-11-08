package org.example.pdr_tweaks.content.kinetics;

import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.example.pdr_tweaks.registry.PDRBlockEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SmartGearshiftBlock extends AbstractEncasedShaftBlock implements IBE<SmartGearshiftBlockEntity> {

    public static final EnumProperty<SmartGearshiftDirection> DIRECTION =
            EnumProperty.create("direction", SmartGearshiftDirection.class);
    public static final EnumProperty<SmartGearshiftMode> MODE =
            EnumProperty.create("mode", SmartGearshiftMode.class);

    public SmartGearshiftBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(DIRECTION, SmartGearshiftDirection.INACTIVE)
                .setValue(MODE, SmartGearshiftMode.SUM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DIRECTION, MODE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Class<SmartGearshiftBlockEntity> getBlockEntityClass() {
        return SmartGearshiftBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SmartGearshiftBlockEntity> getBlockEntityType() {
        return PDRBlockEntities.SMART_GEARSHIFT.get();
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                                          @NotNull Player player, @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return onBlockEntityUse(level, pos, be -> {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new MenuProvider() {
                    @Override
                    public @NotNull Component getDisplayName() {
                        return Component.translatable("gui.pdr_tweaks.smart_gearshift");
                    }

                    @Nullable
                    @Override
                    public AbstractContainerMenu createMenu(int windowId, @NotNull Inventory playerInventory, @NotNull Player p) {
                        return new SmartGearshiftMenu(windowId, playerInventory, be);
                    }
                }, buf -> SmartGearshiftMenu.writeExtraData(buf, be));
            }
            return InteractionResult.CONSUME;
        });
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) return;
        withBlockEntityDo(level, pos, be -> be.onRedstoneChanged(level.hasNeighborSignal(pos)));
    }

    @Override
    public void tick(@NotNull BlockState state, @NotNull net.minecraft.server.level.ServerLevel level,
                     @NotNull BlockPos pos, @NotNull RandomSource random) {
        // Scheduled re-attachment after direction change (prevents kinetic conflict)
        withBlockEntityDo(level, pos, be -> {
            be.reattachKinetics();
            be.attachKinetics();
        });
    }
}

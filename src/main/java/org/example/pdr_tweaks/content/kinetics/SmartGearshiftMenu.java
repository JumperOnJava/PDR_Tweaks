package org.example.pdr_tweaks.content.kinetics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.example.pdr_tweaks.registry.PDRMenus;
import org.jetbrains.annotations.NotNull;

public class SmartGearshiftMenu extends AbstractContainerMenu {

    public static final int FORWARD_FIRST = 0;
    public static final int FORWARD_LAST = 1;
    public static final int REVERSE_FIRST = 2;
    public static final int REVERSE_LAST = 3;
    public static final int GHOST_SLOTS = 4;

    public final BlockPos pos;
    public final ItemStackHandler ghostInventory;
    private final Level level;

    // Server-side constructor — reads initial state from block entity
    public SmartGearshiftMenu(int windowId, Inventory playerInventory, SmartGearshiftBlockEntity be) {
        super(PDRMenus.SMART_GEARSHIFT.get(), windowId);
        this.pos = be.getBlockPos();
        this.level = be.getLevel();
        this.ghostInventory = createGhostInventory(be);
        initSlots(playerInventory);
    }

    // Client-side constructor — reads initial state from extra data buffer
    public SmartGearshiftMenu(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(PDRMenus.SMART_GEARSHIFT.get(), windowId);
        this.pos = extraData.readBlockPos();
        this.level = playerInventory.player.level();
        this.ghostInventory = new ItemStackHandler(GHOST_SLOTS);
        // Read initial frequency stacks sent from server
        ghostInventory.setStackInSlot(FORWARD_FIRST, ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
        ghostInventory.setStackInSlot(FORWARD_LAST, ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
        ghostInventory.setStackInSlot(REVERSE_FIRST, ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
        ghostInventory.setStackInSlot(REVERSE_LAST, ItemStack.OPTIONAL_STREAM_CODEC.decode(extraData));
        initSlots(playerInventory);
    }

    public static SmartGearshiftMenu fromNetwork(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        return new SmartGearshiftMenu(windowId, playerInventory, extraData);
    }

    /**
     * Writes the extra data for client menu creation. Called by the block when opening the menu.
     */
    public static void writeExtraData(RegistryFriendlyByteBuf buf, SmartGearshiftBlockEntity be) {
        buf.writeBlockPos(be.getBlockPos());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, be.getForwardFrequency(true));
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, be.getForwardFrequency(false));
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, be.getReverseFrequency(true));
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, be.getReverseFrequency(false));
    }

    private static ItemStackHandler createGhostInventory(SmartGearshiftBlockEntity be) {
        ItemStackHandler handler = new ItemStackHandler(GHOST_SLOTS);
        handler.setStackInSlot(FORWARD_FIRST, be.getForwardFrequency(true));
        handler.setStackInSlot(FORWARD_LAST, be.getForwardFrequency(false));
        handler.setStackInSlot(REVERSE_FIRST, be.getReverseFrequency(true));
        handler.setStackInSlot(REVERSE_LAST, be.getReverseFrequency(false));
        return handler;
    }

    private void initSlots(Inventory playerInventory) {
        addSlot(new SlotItemHandler(ghostInventory, FORWARD_FIRST, 44, 30));
        addSlot(new SlotItemHandler(ghostInventory, FORWARD_LAST, 62, 30));
        addSlot(new SlotItemHandler(ghostInventory, REVERSE_FIRST, 98, 30));
        addSlot(new SlotItemHandler(ghostInventory, REVERSE_LAST, 116, 30));

        for (int row = 0; row < 3; ++row)
            for (int col = 0; col < 9; ++col)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; ++col)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
    }

    public SmartGearshiftBlockEntity getBlockEntity() {
        if (level != null && level.getBlockEntity(pos) instanceof SmartGearshiftBlockEntity be) {
            return be;
        }
        return null;
    }

    public SmartGearshiftMode getMode() {
        SmartGearshiftBlockEntity be = getBlockEntity();
        return be != null ? be.getMode() : SmartGearshiftMode.SUM;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        if (index >= GHOST_SLOTS && index < GHOST_SLOTS + 36) {
            Slot slot = slots.get(index);
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem().copy();
                stack.setCount(1);
                for (int i = 0; i < GHOST_SLOTS; i++) {
                    if (ghostInventory.getStackInSlot(i).isEmpty()) {
                        ghostInventory.setStackInSlot(i, stack);
                        break;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int dragType, net.minecraft.world.inventory.ClickType clickType, @NotNull Player player) {
        if (slotId >= 0 && slotId < GHOST_SLOTS) {
            if (clickType == net.minecraft.world.inventory.ClickType.PICKUP) {
                ItemStack carried = getCarried();
                if (carried.isEmpty()) {
                    ghostInventory.setStackInSlot(slotId, ItemStack.EMPTY);
                } else {
                    ItemStack copy = carried.copy();
                    copy.setCount(1);
                    ghostInventory.setStackInSlot(slotId, copy);
                }
                getSlot(slotId).setChanged();
                return;
            }
            if (clickType == net.minecraft.world.inventory.ClickType.CLONE && player.isCreative()) {
                ItemStack stack = ghostInventory.getStackInSlot(slotId).copy();
                stack.setCount(stack.getMaxStackSize());
                setCarried(stack);
                return;
            }
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().getBlockEntity(pos) instanceof SmartGearshiftBlockEntity;
    }
}

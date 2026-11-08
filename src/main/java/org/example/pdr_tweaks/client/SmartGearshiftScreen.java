package org.example.pdr_tweaks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.example.pdr_tweaks.content.kinetics.SmartGearshiftMenu;
import org.example.pdr_tweaks.content.kinetics.SmartGearshiftMode;
import org.example.pdr_tweaks.network.SmartGearshiftConfigPacket;

public class SmartGearshiftScreen extends AbstractContainerScreen<SmartGearshiftMenu> {

    private Button modeButton;
    private Button swapButton;

    public SmartGearshiftScreen(SmartGearshiftMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        // Mode button
        modeButton = Button.builder(modeLabel(), b -> {
            SmartGearshiftMode next = menu.getMode().next();
            PacketDistributor.sendToServer(SmartGearshiftConfigPacket.setMode(menu.pos, next));
            b.setMessage(modeLabel(next));
        }).bounds(x + 8, y + 52, 80, 20).build();
        addRenderableWidget(modeButton);

        // Swap button
        swapButton = Button.builder(Component.translatable("gui.pdr_tweaks.smart_gearshift.swap"), b -> {
            PacketDistributor.sendToServer(SmartGearshiftConfigPacket.swap(menu.pos));
        }).bounds(x + 96, y + 52, 72, 20).build();
        addRenderableWidget(swapButton);
    }

    private Component modeLabel() {
        return modeLabel(menu.getMode());
    }

    private Component modeLabel(SmartGearshiftMode mode) {
        return Component.translatable("gui.pdr_tweaks.smart_gearshift.mode." + mode.getSerializedName());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Simple background
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        graphics.fill(x + 4, y + 4, x + imageWidth - 4, y + 20, 0xFF8B8B8B);

        // Labels
        graphics.drawString(font, Component.translatable("gui.pdr_tweaks.smart_gearshift.forward"),
                x + 44, y + 20, 0xFF404040, false);
        graphics.drawString(font, Component.translatable("gui.pdr_tweaks.smart_gearshift.reverse"),
                x + 98, y + 20, 0xFF404040, false);

        // Draw slot backgrounds for ghost slots
        for (int i = 0; i < 4; i++) {
            int slotX = x + 43 + (i % 2) * 18 + (i >= 2 ? 54 : 0);
            int slotY = y + 29;
            graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF8B8B8B);
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF373737);
        }

        // Title
        graphics.drawString(font, title, x + 8, y + 6, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Sync frequencies when closing or on change
        // (handled by packet on button press)
    }

    @Override
    public void onClose() {
        // Sync ghost slot contents to server before closing
        PacketDistributor.sendToServer(SmartGearshiftConfigPacket.setFrequencies(
                menu.pos,
                menu.ghostInventory.getStackInSlot(SmartGearshiftMenu.FORWARD_FIRST),
                menu.ghostInventory.getStackInSlot(SmartGearshiftMenu.FORWARD_LAST),
                menu.ghostInventory.getStackInSlot(SmartGearshiftMenu.REVERSE_FIRST),
                menu.ghostInventory.getStackInSlot(SmartGearshiftMenu.REVERSE_LAST)
        ));
        super.onClose();
    }
}

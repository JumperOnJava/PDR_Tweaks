package org.example.pdr_tweaks.integration.projectpdr;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import moze_intel.projectpdr.api.ItemInfo;
import moze_intel.projectpdr.api.capabilities.IKnowledgeProvider;
import moze_intel.projectpdr.api.capabilities.PECapabilities;
import moze_intel.projectpdr.api.proxy.IEMCProxy;
import moze_intel.projectpdr.gameObjs.block_entities.TransmutationTableBlockEntity;
import moze_intel.projectpdr.gameObjs.blocks.TransmutationStone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.example.pdr_tweaks.PDRTweaks;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles right-clicking a transmutation table while holding a Create clipboard
 * containing a material list (from the schematicannon).
 *
 * Normal click: spawns NBT chests with all required items (if checks pass).
 * Sneak click: spawns chests with only unchecked items and marks them checked,
 *              or as many as EMC/knowledge allows.
 */
@EventBusSubscriber(modid = PDRTweaks.MODID)
public class TransmutationTableMaterialList {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        // Check if the clicked block is a transmutation table
        if (!(level.getBlockState(pos).getBlock() instanceof TransmutationStone)) return;

        // Check if player is holding a clipboard with material list data
        ItemStack heldStack = event.getItemStack();
        if (heldStack.isEmpty()) return;
        if (!heldStack.is(AllBlocks.CLIPBOARD.asItem())) return;

        ClipboardContent clipboardContent = heldStack.get(AllDataComponents.CLIPBOARD_CONTENT);
        if (clipboardContent == null || clipboardContent.pages().isEmpty()) return;

        // Parse material list entries
        List<MaterialEntry> entries = parseMaterialList(clipboardContent);
        if (entries.isEmpty()) return;

        // Get the transmutation table block entity
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TransmutationTableBlockEntity table)) return;

        // === CHECK 1: Team match ===
        String tableTeam = table.getTeam();
        String playerTeam = player.getTeam() == null ? null : player.getTeam().getName();
        if (tableTeam != null && !tableTeam.equals(playerTeam)) {
            player.sendSystemMessage(Component.translatable("gui.pdr_tweaks.material_list.team_mismatch"));
            event.setCanceled(true);
            return;
        }

        // Get knowledge provider (team-aware)
        IKnowledgeProvider provider = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        if (provider == null) return;

        boolean sneak = player.isShiftKeyDown();
        BigInteger availableEmc = provider.getEmc();

        // Checked entries are already obtained — they don't count toward the list
        List<MaterialEntry> unchecked = entries.stream().filter(e -> !e.checked).toList();

        if (unchecked.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gui.pdr_tweaks.material_list.nothing_to_spawn"));
            event.setCanceled(true);
            return;
        }

        if (!sneak) {
            // === Normal click: full-list mode ===
            // Give items ONLY if every unchecked entry can be satisfied:
            // knowledge for all of them + enough EMC for the total cost.
            BigInteger totalNeeded = BigInteger.ZERO;
            List<MaterialEntry> missingKnowledge = new ArrayList<>();

            for (MaterialEntry entry : unchecked) {
                if (!provider.hasKnowledge(ItemInfo.fromStack(entry.stack))) {
                    missingKnowledge.add(entry);
                }
                long itemEmc = IEMCProxy.INSTANCE.getValue(entry.stack);
                totalNeeded = totalNeeded.add(BigInteger.valueOf(itemEmc).multiply(BigInteger.valueOf(entry.amount)));
            }

            BigInteger missingEmc = totalNeeded.subtract(availableEmc);
            boolean fullySatisfiable = missingKnowledge.isEmpty() && missingEmc.signum() <= 0;

            if (!fullySatisfiable) {
                // Spawn nothing; report what's missing instead
                player.sendSystemMessage(Component.translatable("gui.pdr_tweaks.material_list.cannot_fully_satisfy"));
                if (!missingKnowledge.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("gui.pdr_tweaks.material_list.missing_knowledge"));
                    for (MaterialEntry entry : missingKnowledge) {
                        player.sendSystemMessage(Component.literal("- ")
                                .append(entry.stack().getHoverName().copy().append(" x" + entry.amount())));
                    }
                }
                if (missingEmc.signum() > 0) {
                    player.sendSystemMessage(Component.translatable("gui.pdr_tweaks.material_list.missing_emc",
                            missingEmc.toString(), totalNeeded.toString(), availableEmc.toString()));
                }
                event.setCanceled(true);
                return;
            }

            // Fully satisfiable — give the whole (unchecked) list
            spawnChestsOnTable(serverLevel, pos, unchecked);
            int totalItems = unchecked.stream().mapToInt(e -> e.amount).sum();
            player.sendSystemMessage(Component.translatable("gui.pdr_tweaks.material_list.spawned",
                    unchecked.size(), totalItems));
            event.setCanceled(true);
            return;
        }

        // === Sneak click: partial mode — spawn whatever is allowed, mark it checked ===
        List<MaterialEntry> spawnable = new ArrayList<>();
        for (MaterialEntry entry : unchecked) {
            ItemInfo info = ItemInfo.fromStack(entry.stack);
            long itemEmc = IEMCProxy.INSTANCE.getValue(entry.stack);
            BigInteger totalEmc = BigInteger.valueOf(itemEmc).multiply(BigInteger.valueOf(entry.amount));

            if (provider.hasKnowledge(info) && totalEmc.compareTo(availableEmc) <= 0) {
                spawnable.add(entry);
                availableEmc = availableEmc.subtract(totalEmc);
            }
        }

        if (spawnable.isEmpty()) {
            player.sendSystemMessage(Component.translatable("gui.pdr_tweaks.material_list.cannot_spawn_any"));
            event.setCanceled(true);
            return;
        }

        // === Spawn chests on top of the transmutation table ===
        spawnChestsOnTable(serverLevel, pos, spawnable);

        // === Sneak mode: mark spawned items as checked in clipboard ===
        ClipboardContent updated = markEntriesAsChecked(clipboardContent, spawnable);
        heldStack.set(AllDataComponents.CLIPBOARD_CONTENT, updated);

        // Cancel the normal transmutation table GUI from opening
        event.setCanceled(true);

        int totalItems = spawnable.stream().mapToInt(e -> e.amount).sum();
        player.sendSystemMessage(Component.translatable("gui.pdr_tweaks.material_list.spawned",
                spawnable.size(), totalItems));
    }

    private static List<MaterialEntry> parseMaterialList(ClipboardContent content) {
        List<MaterialEntry> entries = new ArrayList<>();
        for (List<ClipboardEntry> page : content.pages()) {
            for (ClipboardEntry entry : page) {
                if (!entry.icon.isEmpty() && entry.itemAmount > 0) {
                    ItemStack icon = entry.icon.copy();
                    icon.setCount(entry.itemAmount);
                    entries.add(new MaterialEntry(icon, entry.itemAmount, entry.checked));
                }
            }
        }
        return entries;
    }

    private static void spawnChestsOnTable(ServerLevel level, BlockPos tablePos, List<MaterialEntry> entries) {
        // Pack items into chests (27 slots per chest)
        List<ItemStack> allItems = new ArrayList<>();
        for (MaterialEntry entry : entries) {
            ItemStack stack = entry.stack.copy();
            int remaining = entry.amount;
            while (remaining > 0) {
                int count = Math.min(remaining, stack.getMaxStackSize());
                ItemStack portion = stack.copy();
                portion.setCount(count);
                allItems.add(portion);
                remaining -= count;
            }
        }

        // Split into chests of 27 slots
        int chestCount = (allItems.size() + 26) / 27;
        double spawnX = tablePos.getX() + 0.5;
        double spawnY = tablePos.getY() + 1.2;
        double spawnZ = tablePos.getZ() + 0.5;

        for (int chestIdx = 0; chestIdx < chestCount; chestIdx++) {
            int start = chestIdx * 27;
            int end = Math.min(start + 27, allItems.size());
            List<ItemStack> chestItems = new ArrayList<>(allItems.subList(start, end));

            ItemStack chestStack = new ItemStack(Items.CHEST);
            chestStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(chestItems));

            ItemEntity itemEntity = new ItemEntity(level, spawnX, spawnY, spawnZ, chestStack);
            itemEntity.setDeltaMovement(
                    (level.random.nextDouble() - 0.5) * 0.1,
                    0.2 + level.random.nextDouble() * 0.1,
                    (level.random.nextDouble() - 0.5) * 0.1
            );
            level.addFreshEntity(itemEntity);
        }
    }

    private static ClipboardContent markEntriesAsChecked(ClipboardContent content, List<MaterialEntry> spawned) {
        List<List<ClipboardEntry>> newPages = new ArrayList<>();
        for (List<ClipboardEntry> page : content.pages()) {
            List<ClipboardEntry> newPage = new ArrayList<>();
            for (ClipboardEntry entry : page) {
                boolean shouldCheck = false;
                if (!entry.icon.isEmpty() && entry.itemAmount > 0) {
                    for (MaterialEntry spawnedEntry : spawned) {
                        if (ItemStack.isSameItemSameComponents(entry.icon, spawnedEntry.stack)) {
                            shouldCheck = true;
                            break;
                        }
                    }
                }
                if (shouldCheck && !entry.checked) {
                    ClipboardEntry checkedEntry = new ClipboardEntry(true, entry.text.copy());
                    if (!entry.icon.isEmpty()) {
                        checkedEntry.displayItem(entry.icon, entry.itemAmount);
                    }
                    newPage.add(checkedEntry);
                } else {
                    newPage.add(entry);
                }
            }
            newPages.add(newPage);
        }
        return new ClipboardContent(
                content.type(),
                newPages,
                content.readOnly(),
                content.previouslyOpenedPage(),
                content.copiedValues()
        );
    }

    private record MaterialEntry(ItemStack stack, int amount, boolean checked) {}
}

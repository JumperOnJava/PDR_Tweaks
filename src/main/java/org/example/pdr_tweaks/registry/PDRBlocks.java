package org.example.pdr_tweaks.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.example.pdr_tweaks.PDRTweaks;
import org.example.pdr_tweaks.content.kinetics.SmartGearshiftBlock;

public class PDRBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PDRTweaks.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PDRTweaks.MODID);

    public static final DeferredBlock<SmartGearshiftBlock> SMART_GEARSHIFT = BLOCKS.register("smart_gearshift",
            () -> new SmartGearshiftBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.PODZOL)
                    .sound(SoundType.STONE)
                    .strength(1.5f)
                    .noOcclusion()));

    public static final DeferredHolder<Item, BlockItem> SMART_GEARSHIFT_ITEM =
            ITEMS.register("smart_gearshift",
                    () -> new BlockItem(SMART_GEARSHIFT.get(), new Item.Properties()));
}

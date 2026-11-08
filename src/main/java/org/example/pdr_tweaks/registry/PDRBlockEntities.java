package org.example.pdr_tweaks.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.example.pdr_tweaks.PDRTweaks;
import org.example.pdr_tweaks.content.kinetics.SmartGearshiftBlockEntity;

public class PDRBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PDRTweaks.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmartGearshiftBlockEntity>> SMART_GEARSHIFT =
            BLOCK_ENTITIES.register("smart_gearshift", PDRBlockEntities::createSmartGearshiftType);

    private static BlockEntityType<SmartGearshiftBlockEntity> createSmartGearshiftType() {
        // The factory lambda is invoked at runtime (when BEs are created), not at registration time,
        // so referencing SMART_GEARSHIFT.get() inside it is safe.
        return BlockEntityType.Builder.of(
                (pos, state) -> new SmartGearshiftBlockEntity(SMART_GEARSHIFT.get(), pos, state),
                PDRBlocks.SMART_GEARSHIFT.get()
        ).build(null);
    }
}

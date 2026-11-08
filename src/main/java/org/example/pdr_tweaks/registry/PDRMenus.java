package org.example.pdr_tweaks.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.example.pdr_tweaks.PDRTweaks;
import org.example.pdr_tweaks.content.kinetics.SmartGearshiftMenu;

import java.util.function.Supplier;

public class PDRMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PDRTweaks.MODID);

    public static final Supplier<MenuType<SmartGearshiftMenu>> SMART_GEARSHIFT =
            MENUS.register("smart_gearshift",
                    () -> IMenuTypeExtension.create(SmartGearshiftMenu::fromNetwork));
}

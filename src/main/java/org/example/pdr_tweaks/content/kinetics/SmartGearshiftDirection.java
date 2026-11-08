package org.example.pdr_tweaks.content.kinetics;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum SmartGearshiftDirection implements StringRepresentable {
    INACTIVE("inactive"),
    ACTIVE("active"),
    REVERSE("reverse");

    private final String name;

    SmartGearshiftDirection(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }
}

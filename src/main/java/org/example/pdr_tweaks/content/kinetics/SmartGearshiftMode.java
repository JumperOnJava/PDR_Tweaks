package org.example.pdr_tweaks.content.kinetics;

import net.minecraft.util.StringRepresentable;

public enum SmartGearshiftMode implements StringRepresentable {
    SUM("sum"),
    LATEST("latest"),
    POWER_REVERSE("power_reverse");

    private final String name;

    SmartGearshiftMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public SmartGearshiftMode next() {
        SmartGearshiftMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}

package com.smartvillager.needqueue;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * How urgently a NeedRequest needs filling.
 * Higher ordinal = higher urgency. Villagers polled during idle pick the
 * highest-priority matching open request first.
 */
public enum NeedPriority implements StringRepresentable {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high"),
    URGENT("urgent");

    public static final Codec<NeedPriority> CODEC = StringRepresentable.fromEnum(NeedPriority::values);

    private final String name;

    NeedPriority(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Numeric priority for comparison; higher = more urgent. */
    public int order() {
        return ordinal();
    }
}

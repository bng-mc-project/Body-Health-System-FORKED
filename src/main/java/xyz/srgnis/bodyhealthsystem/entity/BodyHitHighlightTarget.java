package xyz.srgnis.bodyhealthsystem.entity;

import net.minecraft.util.Identifier;

public interface BodyHitHighlightTarget {
    void setHighlightedPart(Identifier part);

    Identifier getHighlightedPart();
}

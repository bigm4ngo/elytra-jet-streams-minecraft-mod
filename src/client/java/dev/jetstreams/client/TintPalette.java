package dev.jetstreams.client;

import dev.jetstreams.config.ConfigManager;
import dev.jetstreams.registry.JetFlowOption;

/**
 * Resolves the configured per-direction tint colors (#RRGGBB strings) into packed
 * RGB ints. Used by both the screen tints and the directional stream particles so
 * the two visual systems always agree.
 */
public final class TintPalette {
    private TintPalette() {}

    /** Opaque RGB for a flow direction ({@link JetFlowOption} constants). */
    public static int forDirection(int flowDir) {
        var c = ConfigManager.get().client;
        return switch (flowDir) {
            case JetFlowOption.NORTH -> parse(c.tintNorth);
            case JetFlowOption.SOUTH -> parse(c.tintSouth);
            case JetFlowOption.EAST -> parse(c.tintEast);
            case JetFlowOption.WEST -> parse(c.tintWest);
            default -> parse(c.tintNeutral);
        };
    }

    /** Opaque RGB for the dead-zone neutral tint. */
    public static int neutral() {
        return parse(ConfigManager.get().client.tintNeutral);
    }

    /** Parses #RRGGBB into 0xRRGGBB; falls back to a soft gray on bad input. */
    public static int parse(String hex) {
        try {
            String v = hex == null ? "" : hex.trim();
            if (v.startsWith("#")) {
                v = v.substring(1);
            }
            return v.length() == 6 ? (int) Long.parseLong(v, 16) : 0x8C99A8;
        } catch (NumberFormatException e) {
            return 0x8C99A8;
        }
    }
}

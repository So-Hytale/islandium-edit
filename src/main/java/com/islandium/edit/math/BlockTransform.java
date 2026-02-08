package com.islandium.edit.math;

import com.islandium.edit.debug.DebugLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles transformation of block states (facing, rotation, etc.)
 * when rotating/flipping clipboards.
 *
 * Block IDs in Hytale may contain state information that needs to be transformed.
 */
public class BlockTransform {

    // Pattern to match block states like "block[facing=north,half=top]"
    private static final Pattern STATE_PATTERN = Pattern.compile("^([^\\[]+)(?:\\[(.+)\\])?$");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("([^=,]+)=([^=,]+)");

    // Direction mappings for rotations
    private static final Map<String, String> ROTATE_90_CW = Map.of(
        "north", "east",
        "east", "south",
        "south", "west",
        "west", "north"
    );

    private static final Map<String, String> ROTATE_180 = Map.of(
        "north", "south",
        "east", "west",
        "south", "north",
        "west", "east"
    );

    private static final Map<String, String> ROTATE_270_CW = Map.of(
        "north", "west",
        "east", "north",
        "south", "east",
        "west", "south"
    );

    // Flip mappings
    private static final Map<String, String> FLIP_X = Map.of(
        "east", "west",
        "west", "east"
    );

    private static final Map<String, String> FLIP_Z = Map.of(
        "north", "south",
        "south", "north"
    );

    private static final Map<String, String> FLIP_Y = Map.of(
        "up", "down",
        "down", "up",
        "top", "bottom",
        "bottom", "top"
    );

    // Stair shape flips
    private static final Map<String, String> STAIR_SHAPE_FLIP = Map.of(
        "outer_left", "outer_right",
        "outer_right", "outer_left",
        "inner_left", "inner_right",
        "inner_right", "inner_left"
    );

    // Hinge flips for doors
    private static final Map<String, String> HINGE_FLIP = Map.of(
        "left", "right",
        "right", "left"
    );

    // Axis rotations
    private static final Map<String, String> AXIS_ROTATE_90 = Map.of(
        "x", "z",
        "z", "x"
    );

    /**
     * Transforms a block ID based on the given affine transform.
     *
     * @param blockId the original block ID (may include state)
     * @param transform the transformation to apply
     * @return the transformed block ID
     */
    @NotNull
    public static String transformBlock(@NotNull String blockId, @NotNull AffineTransform transform) {
        if (transform.isIdentity()) {
            return blockId;
        }

        // Parse block ID and state
        Matcher matcher = STATE_PATTERN.matcher(blockId);
        if (!matcher.matches()) {
            return blockId;
        }

        String baseName = matcher.group(1);
        String stateStr = matcher.group(2);

        if (stateStr == null || stateStr.isEmpty()) {
            return blockId;
        }

        // Parse properties
        Map<String, String> properties = parseProperties(stateStr);
        if (properties.isEmpty()) {
            return blockId;
        }

        // Transform properties
        int rotation = transform.getYRotation();
        boolean flipX = transform.isFlipX();
        boolean flipZ = transform.isFlipZ();
        boolean vFlip = transform.isVerticalFlip();

        Map<String, String> transformed = new HashMap<>(properties);

        // Handle facing property
        if (transformed.containsKey("facing")) {
            String facing = transformed.get("facing");
            facing = transformDirection(facing, rotation, flipX, flipZ, vFlip);
            transformed.put("facing", facing);
        }

        // Handle axis property (logs, pillars)
        if (transformed.containsKey("axis")) {
            String axis = transformed.get("axis");
            if (rotation == 90 || rotation == 270) {
                axis = AXIS_ROTATE_90.getOrDefault(axis, axis);
            }
            transformed.put("axis", axis);
        }

        // Handle stair shape (flip when mirroring)
        if (transformed.containsKey("shape") && (flipX || flipZ)) {
            String shape = transformed.get("shape");
            shape = STAIR_SHAPE_FLIP.getOrDefault(shape, shape);
            transformed.put("shape", shape);
        }

        // Handle door hinge (flip when mirroring)
        if (transformed.containsKey("hinge") && (flipX || flipZ)) {
            String hinge = transformed.get("hinge");
            hinge = HINGE_FLIP.getOrDefault(hinge, hinge);
            transformed.put("hinge", hinge);
        }

        // Handle half (top/bottom for slabs, stairs)
        if (transformed.containsKey("half") && vFlip) {
            String half = transformed.get("half");
            half = FLIP_Y.getOrDefault(half, half);
            transformed.put("half", half);
        }

        // Handle type (top/bottom for slabs)
        if (transformed.containsKey("type") && vFlip) {
            String type = transformed.get("type");
            if ("top".equals(type) || "bottom".equals(type)) {
                type = FLIP_Y.getOrDefault(type, type);
                transformed.put("type", type);
            }
        }

        // Handle rotation property (signs, banners, etc.) - 0-15 for 16 directions
        if (transformed.containsKey("rotation")) {
            try {
                int rot = Integer.parseInt(transformed.get("rotation"));
                // Each increment is 22.5 degrees, so rotation/90*4 = steps to add
                int steps = (rotation / 90) * 4;
                if (flipX || flipZ) {
                    // Flip reverses the rotation
                    rot = (16 - rot) % 16;
                }
                rot = (rot + steps) % 16;
                transformed.put("rotation", String.valueOf(rot));
            } catch (NumberFormatException ignored) {
            }
        }

        // Rebuild block ID
        String result = buildBlockId(baseName, transformed);

        // Debug log si le block state a changé
        if (!result.equals(blockId)) {
            DebugLogger dbg = DebugLogger.get();
            if (dbg != null) {
                dbg.logBlockStateTransform(blockId, result, rotation, flipX, flipZ, vFlip);
            }
        }

        return result;
    }

    /**
     * Transforms a direction based on rotation and flips.
     */
    @NotNull
    private static String transformDirection(@NotNull String direction, int rotation, boolean flipX, boolean flipZ, boolean vFlip) {
        String result = direction;

        // Apply X flip (east/west)
        if (flipX) {
            result = FLIP_X.getOrDefault(result, result);
        }

        // Apply Z flip (north/south)
        if (flipZ) {
            result = FLIP_Z.getOrDefault(result, result);
        }

        // Apply vertical flip
        if (vFlip) {
            result = FLIP_Y.getOrDefault(result, result);
        }

        // Apply rotation
        result = switch (rotation) {
            case 90 -> ROTATE_90_CW.getOrDefault(result, result);
            case 180 -> ROTATE_180.getOrDefault(result, result);
            case 270 -> ROTATE_270_CW.getOrDefault(result, result);
            default -> result;
        };

        return result;
    }

    /**
     * Parses block state properties from a string like "facing=north,half=top".
     */
    @NotNull
    private static Map<String, String> parseProperties(@NotNull String stateStr) {
        Map<String, String> properties = new HashMap<>();
        Matcher matcher = PROPERTY_PATTERN.matcher(stateStr);
        while (matcher.find()) {
            properties.put(matcher.group(1), matcher.group(2));
        }
        return properties;
    }

    /**
     * Builds a block ID from base name and properties.
     */
    @NotNull
    private static String buildBlockId(@NotNull String baseName, @NotNull Map<String, String> properties) {
        if (properties.isEmpty()) {
            return baseName;
        }

        StringBuilder sb = new StringBuilder(baseName);
        sb.append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }
}

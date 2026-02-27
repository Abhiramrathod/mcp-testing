package mcp.toolkit.testing.framework.core.util;

import java.util.Objects;

/**
 * Validation helpers for common argument checks.
 */
public final class McpValidation {

    private McpValidation() {
    }

    /**
     * Ensures the provided value is not null.
     *
     * @param value value to validate
     * @param name argument name used in error messages
     * @param <T> value type
     * @return the same value if not null
     * @throws NullPointerException if value is null
     */
    public static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, nullMessage(name));
    }

    /**
     * Ensures the provided string is not null or blank.
     *
     * @param value string to validate
     * @param name argument name used in error messages
     * @return the same value if not blank
     * @throws IllegalArgumentException if the string is null or blank
     */
    public static String requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(blankMessage(name));
        }
        return value;
    }

    private static String nullMessage(String name) {
        return "Required argument '" + name + "' must not be null.";
    }

    private static String blankMessage(String name) {
        return "Required argument '" + name + "' must not be blank.";
    }
}


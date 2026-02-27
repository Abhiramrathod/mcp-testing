package mcp.toolkit.testing.framework.client.lifecycle;

import mcp.toolkit.testing.framework.core.util.McpValidation;
import java.util.function.Supplier;

/**
 * Ensures MCP initialization runs before executing client actions.
 */
public final class McpInitializationGuard {

    private final Runnable ensureInitialized;

    /**
     * Creates a guard that executes the supplied initializer before actions.
     *
     * @param ensureInitialized runnable that performs initialization if needed
     */
    public McpInitializationGuard(Runnable ensureInitialized) {
        this.ensureInitialized = McpValidation.requireNonNull(ensureInitialized, "ensureInitialized");
    }

    /**
     * Runs an action after ensuring initialization.
     *
     * @param action action to execute
     * @param <T> action return type
     * @return action result
     */
    public <T> T withInitialized(Supplier<T> action) {
        ensureInitialized.run();
        return action.get();
    }

    /**
     * Runs a void action after ensuring initialization.
     *
     * @param action action to execute
     */
    public void withInitialized(Runnable action) {
        ensureInitialized.run();
        action.run();
    }
}


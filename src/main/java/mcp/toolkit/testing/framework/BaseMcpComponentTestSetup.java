package mcp.toolkit.testing.framework;

/**
 * Factory helpers for tests that need a fully initialized {@link McpTestClient}.
 */
public final class BaseMcpComponentTestSetup {

    private BaseMcpComponentTestSetup() {
    }

    /**
     * Creates a client with default SSE endpoint and performs MCP initialization.
     *
     * @param baseUrl base server URL
     * @return initialized MCP test client
     */
    public static McpTestClient initializeMcpTestClient(String baseUrl) {
        McpTestClient client = new McpTestClient(baseUrl);
        client.initialize();
        return client;
    }

    /**
     * Creates a client with a custom SSE endpoint path and performs MCP initialization.
     *
     * @param baseUrl         base server URL
     * @param sseEndpointPath path for the SSE subscription endpoint (relative to baseUrl)
     * @return initialized MCP test client
     */
    public static McpTestClient initializeMcpTestClient(String baseUrl, String sseEndpointPath) {
        McpTestClient client = new McpTestClient(baseUrl, sseEndpointPath);
        client.initialize();
        return client;
    }
}


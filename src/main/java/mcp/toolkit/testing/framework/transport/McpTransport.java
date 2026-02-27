package mcp.toolkit.testing.framework.transport;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Transport abstraction for sending and receiving MCP JSON-RPC messages.
 */
public interface McpTransport extends AutoCloseable {

    /**
     * Establishes the transport connection.
     *
     * <p>This method should be idempotent and safe to call multiple times.
     */
    void connect();

    /**
     * Sends a JSON-RPC request and waits for the response.
     *
     * @param payload JSON-RPC request payload
     * @param requestId JSON-RPC request id
     * @return JSON-RPC response node
     * @throws IllegalStateException if not connected or connection fails
     */
    JsonNode sendRequest(String payload, long requestId);

    /**
     * Sends a JSON-RPC notification (no response expected).
     *
     * @param payload JSON-RPC notification payload
     * @throws IllegalStateException if not connected or connection fails
     */
    void sendNotification(String payload);

    /**
     * Closes the transport connection and releases resources.
     */
    @Override
    void close();
}


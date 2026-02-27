package mcp.toolkit.testing.framework;

import mcp.toolkit.testing.framework.client.lifecycle.McpInitializationGuard;
import mcp.toolkit.testing.framework.client.prompts.McpPromptDirectory;
import mcp.toolkit.testing.framework.client.resources.McpResourceDirectory;
import mcp.toolkit.testing.framework.client.rpc.McpRpcClient;
import mcp.toolkit.testing.framework.client.rpc.RpcExchangeTracker;
import mcp.toolkit.testing.framework.client.tools.McpToolDirectory;
import mcp.toolkit.testing.framework.core.codec.McpJsonCodec;
import mcp.toolkit.testing.framework.core.constants.McpTestClientConstants;
import mcp.toolkit.testing.framework.core.util.McpValidation;
import mcp.toolkit.testing.framework.core.util.McpTestClientUtils;
import mcp.toolkit.testing.framework.transport.McpTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static mcp.toolkit.testing.framework.core.util.McpTestClientUtils.buildInitializeParams;
import static mcp.toolkit.testing.framework.core.util.McpTestClientUtils.ClientComponents;
import static mcp.toolkit.testing.framework.core.util.McpTestClientUtils.buildComponents;

/**
 * High-level MCP test client that manages SSE connection, initialization,
 * and JSON-RPC calls.
 *
 * <p>Domain-specific operations are accessed through typed accessors:
 * <ul>
 *   <li>{@link #tools()} &ndash; tool discovery and invocation</li>
 *   <li>{@link #resources()} &ndash; resource listing and reading</li>
 *   <li>{@link #prompts()} &ndash; prompt listing and retrieval</li>
 * </ul>
 *
 * <p>Initialization is idempotent and performed lazily on first request unless
 * {@link #initialize()} is called explicitly. The SSE connection is established
 * as part of initialization.
 */
public class McpTestClient implements AutoCloseable {

    private final ObjectMapper objectMapper;
    private final String protocolVersion;

    private final McpInitializationGuard initGuard;
    private final McpTransport transport;
    private final McpJsonCodec jsonCodec;
    private final McpRpcClient rpcClient;
    private final McpToolDirectory toolDirectory;
    private final McpResourceDirectory resourceDirectory;
    private final McpPromptDirectory promptDirectory;

    private final Object initLock = new Object();

    private volatile boolean initialized;
    private volatile JsonNode initializeResult;

    /**
     * Creates a client with default SSE endpoint and protocol version.
     *
     * @param baseUrl base server URL, for example "http://localhost:8080"
     */
    public McpTestClient(String baseUrl) {
        this(baseUrl, McpTestClientConstants.Endpoints.SSE);
    }

    /**
     * Creates a client with a custom SSE endpoint path.
     *
     * @param baseUrl         base server URL, for example "http://localhost:8080"
     * @param sseEndpointPath path for the SSE subscription endpoint (relative to baseUrl)
     */
    public McpTestClient(String baseUrl, String sseEndpointPath) {
        this(new ObjectMapper(),
                McpTestClientConstants.Defaults.PROTOCOL_VERSION,
                baseUrl,
                sseEndpointPath);
    }

    private McpTestClient(
            ObjectMapper objectMapper,
            String protocolVersion,
            String baseUrl,
            String sseEndpointPath) {
        this.objectMapper = McpValidation.requireNonNull(objectMapper, "objectMapper");
        this.protocolVersion = McpTestClientUtils.resolveProtocolVersion(protocolVersion);
        this.initGuard = new McpInitializationGuard(this::ensureInitialized);
        ClientComponents components = buildComponents(
                this.objectMapper,
                this.protocolVersion,
                baseUrl,
                sseEndpointPath,
                this.initGuard);
        this.transport = components.transport();
        this.jsonCodec = components.jsonCodec();
        this.rpcClient = components.rpcClient();
        this.toolDirectory = components.toolDirectory();
        this.resourceDirectory = components.resourceDirectory();
        this.promptDirectory = components.promptDirectory();
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Establishes the SSE connection and performs the MCP initialize handshake.
     *
     * <p>This method is safe to call multiple times.
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        synchronized (initLock) {
            if (initialized) {
                return;
            }
            transport.connect();
            JsonNode result = rpcClient.callAndRequireResult(McpTestClientConstants.Methods.INITIALIZE,
                    () -> buildInitializeParams(jsonCodec, protocolVersion));
            initialized = true;
            initializeResult = result;
            rpcClient.sendNotification(McpTestClientConstants.Notifications.INITIALIZED, objectMapper::createObjectNode);
        }
    }

    /**
     * Returns {@code true} if the MCP handshake has completed.
     *
     * @return whether the client is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the server's initialize response, or {@code null} if
     * {@link #initialize()} has not been called yet.
     *
     * @return initialize result containing server capabilities
     */
    public JsonNode getInitializeResult() {
        return initializeResult;
    }

    /**
     * Closes the SSE connection and releases all resources.
     */
    @Override
    public void close() {
        transport.close();
    }

    // ── Domain accessors ─────────────────────────────────────────────────

    /**
     * Returns the tool directory for discovering and invoking MCP tools.
     *
     * @return tool directory
     */
    public McpToolDirectory tools() {
        return toolDirectory;
    }

    /**
     * Returns the resource directory for listing and reading MCP resources.
     *
     * @return resource directory
     */
    public McpResourceDirectory resources() {
        return resourceDirectory;
    }

    /**
     * Returns the prompt directory for listing and retrieving MCP prompts.
     *
     * @return prompt directory
     */
    public McpPromptDirectory prompts() {
        return promptDirectory;
    }

    // ── Observability ────────────────────────────────────────────────────

    /**
     * Returns the exchange tracker that records every JSON-RPC
     * request/response for inspection and test assertions.
     *
     * @return exchange tracker
     */
    public RpcExchangeTracker exchangeTracker() {
        return rpcClient.exchangeTracker();
    }

    // ── Generic RPC ──────────────────────────────────────────────────────

    /**
     * Calls an MCP JSON-RPC method and returns the result payload.
     *
     * @param method MCP method name
     * @param params optional params object (converted to JSON)
     * @return result node from the MCP response
     * @throws AssertionError if the response is missing or contains an error
     */
    public JsonNode call(String method, Object params) {
        McpValidation.requireNonNull(method, "method");
        return initGuard.withInitialized(() -> rpcClient.callAndRequireResult(method, () -> jsonCodec.toJsonNode(params)));
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }
}


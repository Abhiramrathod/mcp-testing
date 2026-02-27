package mcp.toolkit.testing.framework.client.resources;

import mcp.toolkit.testing.framework.client.lifecycle.McpInitializationGuard;
import mcp.toolkit.testing.framework.client.rpc.McpRpcClient;
import mcp.toolkit.testing.framework.core.codec.McpJsonCodec;
import mcp.toolkit.testing.framework.core.constants.McpTestClientConstants;
import mcp.toolkit.testing.framework.core.util.McpValidation;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Client-side facade for MCP resource listing and reading.
 */
public final class McpResourceDirectory {

    private final McpInitializationGuard initGuard;
    private final McpRpcClient rpcClient;
    private final McpJsonCodec jsonCodec;

    /**
     * Creates a resource directory backed by the given RPC client and JSON codec.
     *
     * @param initGuard guard that ensures initialization before calls
     * @param rpcClient JSON-RPC client
     * @param jsonCodec JSON codec used to build params
     */
    public McpResourceDirectory(McpInitializationGuard initGuard, McpRpcClient rpcClient, McpJsonCodec jsonCodec) {
        this.initGuard = McpValidation.requireNonNull(initGuard, "initGuard");
        this.rpcClient = McpValidation.requireNonNull(rpcClient, "rpcClient");
        this.jsonCodec = McpValidation.requireNonNull(jsonCodec, "jsonCodec");
    }

    /**
     * Lists available resources using default parameters.
     *
     * @return resources/list response result
     */
    public JsonNode listResources() {
        return listResources(Map.of());
    }

    /**
     * Lists available resources with optional parameters.
     *
     * @param params list parameters
     * @return resources/list response result
     */
    public JsonNode listResources(Object params) {
        return initGuard.withInitialized(() -> rpcClient.callAndRequireResult(
                McpTestClientConstants.Methods.RESOURCES_LIST, () -> jsonCodec.toJsonNode(params)));
    }

    /**
     * Reads a resource by URI.
     *
     * @param uri resource URI
     * @return resources/read response result
     */
    public JsonNode readResource(String uri) {
        McpValidation.requireNonNull(uri, "uri");
        return initGuard.withInitialized(() -> rpcClient.callAndRequireResult(
                McpTestClientConstants.Methods.RESOURCES_READ,
                () -> jsonCodec.buildParams(params -> params.put("uri", uri))));
    }
}


package mcp.toolkit.testing.framework.client.tools;

import mcp.toolkit.testing.framework.client.lifecycle.McpInitializationGuard;
import mcp.toolkit.testing.framework.client.rpc.McpRpcClient;
import mcp.toolkit.testing.framework.core.codec.McpJsonCodec;
import mcp.toolkit.testing.framework.core.constants.McpTestClientConstants;
import mcp.toolkit.testing.framework.core.util.McpValidation;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Client-side facade for MCP tool discovery and invocation.
 */
public final class McpToolDirectory {

    private final McpInitializationGuard initGuard;
    private final McpRpcClient rpcClient;
    private final McpJsonCodec jsonCodec;

    /**
     * Creates a tool directory backed by the given RPC client and JSON codec.
     *
     * @param initGuard guard that ensures initialization before calls
     * @param rpcClient JSON-RPC client
     * @param jsonCodec JSON codec used to build params
     */
    public McpToolDirectory(McpInitializationGuard initGuard, McpRpcClient rpcClient, McpJsonCodec jsonCodec) {
        this.initGuard = McpValidation.requireNonNull(initGuard, "initGuard");
        this.rpcClient = McpValidation.requireNonNull(rpcClient, "rpcClient");
        this.jsonCodec = McpValidation.requireNonNull(jsonCodec, "jsonCodec");
    }

    /**
     * Lists available tools using default parameters.
     *
     * @return tools/list response result
     */
    public JsonNode listTools() {
        return listTools(Map.of());
    }

    /**
     * Lists available tools with optional parameters.
     *
     * @param params list parameters
     * @return tools/list response result
     */
    public JsonNode listTools(Object params) {
        return initGuard.withInitialized(() -> rpcClient.callAndRequireResult(
                McpTestClientConstants.Methods.TOOLS_LIST, () -> jsonCodec.toJsonNode(params)));
    }

    /**
     * Returns the tool definition array from a tools/list response.
     *
     * @return array of tool definitions
     * @throws AssertionError if the tools node is not an array
     */
    public JsonNode allToolDefinitions() {
        JsonNode result = listTools();
        JsonNode tools = result.path("tools");
        if (!tools.isArray()) {
            throw new AssertionError("Expected tools/list to return an array.");
        }
        return tools;
    }

    /**
     * Finds a tool definition by name.
     *
     * @param name tool name
     * @return tool definition node
     * @throws AssertionError if the tool is not found
     */
    public JsonNode toolDefinition(String name) {
        McpValidation.requireNonNull(name, "name");
        JsonNode tools = allToolDefinitions();
        for (JsonNode tool : tools) {
            if (name.equals(tool.path("name").asText())) {
                return tool;
            }
        }
        throw new AssertionError("No MCP tool found with name: " + name);
    }

    /**
     * Calls a tool by name with optional arguments.
     *
     * @param name tool name
     * @param args tool arguments object
     * @return tools/call response result
     */
    public JsonNode callTool(String name, Object args) {
        McpValidation.requireNonNull(name, "name");
        return initGuard.withInitialized(() -> rpcClient.callAndRequireResult(
                McpTestClientConstants.Methods.TOOLS_CALL, () -> jsonCodec.buildParams(
                params -> {
                    params.put("name", name);
                    params.set("arguments", jsonCodec.toArgumentsNode(args));
                })));
    }
}


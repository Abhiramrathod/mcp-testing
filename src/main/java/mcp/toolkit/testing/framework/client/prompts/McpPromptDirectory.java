package mcp.toolkit.testing.framework.client.prompts;

import mcp.toolkit.testing.framework.client.lifecycle.McpInitializationGuard;
import mcp.toolkit.testing.framework.client.rpc.McpRpcClient;
import mcp.toolkit.testing.framework.core.codec.McpJsonCodec;
import mcp.toolkit.testing.framework.core.constants.McpTestClientConstants;
import mcp.toolkit.testing.framework.core.util.McpValidation;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Client-side facade for MCP prompt listing and retrieval.
 */
public final class McpPromptDirectory {

    private final McpInitializationGuard initGuard;
    private final McpRpcClient rpcClient;
    private final McpJsonCodec jsonCodec;

    /**
     * Creates a prompt directory backed by the given RPC client and JSON codec.
     *
     * @param initGuard guard that ensures initialization before calls
     * @param rpcClient JSON-RPC client
     * @param jsonCodec JSON codec used to build params
     */
    public McpPromptDirectory(McpInitializationGuard initGuard, McpRpcClient rpcClient, McpJsonCodec jsonCodec) {
        this.initGuard = McpValidation.requireNonNull(initGuard, "initGuard");
        this.rpcClient = McpValidation.requireNonNull(rpcClient, "rpcClient");
        this.jsonCodec = McpValidation.requireNonNull(jsonCodec, "jsonCodec");
    }

    /**
     * Lists available prompts using default parameters.
     *
     * @return prompts/list response result
     */
    public JsonNode listPrompts() {
        return listPrompts(Map.of());
    }

    /**
     * Lists available prompts with optional parameters.
     *
     * @param params list parameters
     * @return prompts/list response result
     */
    public JsonNode listPrompts(Object params) {
        return initGuard.withInitialized(() -> rpcClient.callAndRequireResult(
                McpTestClientConstants.Methods.PROMPTS_LIST, () -> jsonCodec.toJsonNode(params)));
    }

    /**
     * Retrieves a prompt by name without arguments.
     *
     * @param name prompt name
     * @return prompts/get response result
     */
    public JsonNode getPrompt(String name) {
        return getPrompt(name, null);
    }

    /**
     * Retrieves a prompt by name with optional arguments.
     *
     * @param name prompt name
     * @param args prompt arguments object
     * @return prompts/get response result
     */
    public JsonNode getPrompt(String name, Object args) {
        McpValidation.requireNonNull(name, "name");
        return initGuard.withInitialized(() -> rpcClient.callAndRequireResult(
                McpTestClientConstants.Methods.PROMPTS_GET, () -> jsonCodec.buildParams(params -> {
                    params.put("name", name);
                    params.set("arguments", jsonCodec.toArgumentsNode(args));
                })));
    }
}


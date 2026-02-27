package mcp.toolkit.testing.framework.core.util;

import mcp.toolkit.testing.framework.client.lifecycle.McpInitializationGuard;
import mcp.toolkit.testing.framework.client.prompts.McpPromptDirectory;
import mcp.toolkit.testing.framework.client.resources.McpResourceDirectory;
import mcp.toolkit.testing.framework.client.rpc.McpRpcClient;
import mcp.toolkit.testing.framework.client.tools.McpToolDirectory;
import mcp.toolkit.testing.framework.core.codec.McpJsonCodec;
import mcp.toolkit.testing.framework.core.constants.McpTestClientConstants;
import mcp.toolkit.testing.framework.transport.McpSseTransport;
import mcp.toolkit.testing.framework.transport.McpTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility methods for building and configuring MCP test clients.
 */
public final class McpTestClientUtils {

    private McpTestClientUtils() {
    }

    /**
     * Resolves the protocol version, falling back to the default when null.
     *
     * @param protocolVersion requested version
     * @return resolved protocol version
     */
    public static String resolveProtocolVersion(String protocolVersion) {
        return protocolVersion == null
                ? McpTestClientConstants.Defaults.PROTOCOL_VERSION
                : protocolVersion;
    }

    /**
     * Resolves base and SSE endpoint URIs from input parameters.
     *
     * @param baseUrl         base server URL
     * @param sseEndpointPath SSE subscription endpoint path
     * @return resolved endpoints holder
     */
    public static ResolvedEndpoints resolveEndpoints(String baseUrl, String sseEndpointPath) {
        URI resolvedBaseUri = normalizeBaseUri(baseUrl);
        URI resolvedSseEndpoint = resolvedBaseUri.resolve(normalizePath(sseEndpointPath));
        return new ResolvedEndpoints(resolvedBaseUri, resolvedSseEndpoint);
    }

    /**
     * Normalizes the base URL by trimming and ensuring a trailing slash.
     *
     * @param baseUrl base server URL
     * @return normalized base URI
     * @throws IllegalArgumentException if the baseUrl is blank
     */
    public static URI normalizeBaseUri(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        String trimmed = baseUrl.trim();
        if (!trimmed.endsWith("/")) {
            trimmed = trimmed + "/";
        }
        return URI.create(trimmed);
    }

    /**
     * Normalizes an endpoint path to ensure it starts with a slash.
     *
     * @param path endpoint path
     * @return normalized path ("/" if blank)
     */
    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }

    /**
     * Builds the initialize params payload for the MCP handshake.
     *
     * @param jsonCodec       JSON codec to build the payload
     * @param protocolVersion MCP protocol version to advertise
     * @return initialize params object
     */
    public static ObjectNode buildInitializeParams(McpJsonCodec jsonCodec, String protocolVersion) {
        return jsonCodec.buildParams(params -> {
            params.put("protocolVersion", protocolVersion);
            ObjectNode capabilities = params.putObject("capabilities");
            capabilities.putObject("roots").put("listChanged", true);
            capabilities.putObject("sampling");
            ObjectNode clientInfo = params.putObject("clientInfo");
            clientInfo.put("name", "mcp-test-client");
            clientInfo.put("version", "1.0.0");
        });
    }

    /**
     * Builds and wires the core client components for an
     * {@link mcp.toolkit.testing.framework.McpTestClient}.
     *
     * @param objectMapper    object mapper used for JSON conversion
     * @param protocolVersion MCP protocol version to advertise
     * @param baseUrl         base server URL
     * @param sseEndpointPath SSE subscription endpoint path
     * @param initGuard       guard that ensures initialization before calls
     * @return aggregated client components
     */
    public static ClientComponents buildComponents(
            ObjectMapper objectMapper,
            String protocolVersion,
            String baseUrl,
            String sseEndpointPath,
            McpInitializationGuard initGuard) {
        ResolvedEndpoints endpoints = resolveEndpoints(baseUrl, sseEndpointPath);
        McpJsonCodec jsonCodec = new McpJsonCodec(objectMapper);
        AtomicLong idSequence = new AtomicLong(1);

        McpTransport transport = new McpSseTransport(
                endpoints.sseEndpointUri(),
                endpoints.baseUri(),
                protocolVersion,
                McpTestClientConstants.Defaults.TIMEOUT,
                jsonCodec);

        McpRpcClient rpcClient = new McpRpcClient(transport, idSequence, jsonCodec);
        McpToolDirectory toolDirectory = new McpToolDirectory(initGuard, rpcClient, jsonCodec);
        McpResourceDirectory resourceDirectory = new McpResourceDirectory(initGuard, rpcClient, jsonCodec);
        McpPromptDirectory promptDirectory = new McpPromptDirectory(initGuard, rpcClient, jsonCodec);
        return new ClientComponents(transport, jsonCodec, rpcClient, toolDirectory, resourceDirectory, promptDirectory);
    }

    /**
     * Aggregated components used by
     * {@link mcp.toolkit.testing.framework.McpTestClient}.
     *
     * @param transport         MCP transport
     * @param jsonCodec         JSON codec
     * @param rpcClient         JSON-RPC client
     * @param toolDirectory     tool directory facade
     * @param resourceDirectory resource directory facade
     * @param promptDirectory   prompt directory facade
     */
    public record ClientComponents(
            McpTransport transport,
            McpJsonCodec jsonCodec,
            McpRpcClient rpcClient,
            McpToolDirectory toolDirectory,
            McpResourceDirectory resourceDirectory,
            McpPromptDirectory promptDirectory) {
    }

    /**
     * Holder for resolved base and SSE endpoint URIs.
     */
    public static final class ResolvedEndpoints {

        private final URI baseUri;
        private final URI sseEndpointUri;

        private ResolvedEndpoints(URI baseUri, URI sseEndpointUri) {
            this.baseUri = baseUri;
            this.sseEndpointUri = sseEndpointUri;
        }

        /**
         * @return normalized base URI
         */
        public URI baseUri() {
            return baseUri;
        }

        /**
         * @return resolved SSE subscription endpoint URI
         */
        public URI sseEndpointUri() {
            return sseEndpointUri;
        }
    }
}


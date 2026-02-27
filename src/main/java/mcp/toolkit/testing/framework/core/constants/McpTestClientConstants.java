package mcp.toolkit.testing.framework.core.constants;

import java.time.Duration;

/**
 * Shared constants used by the MCP testing framework.
 */
public final class McpTestClientConstants {

    /**
     * Default configuration values for the test client.
     */
    public static final class Defaults {

        /** Default request timeout used by the client. */
        public static final Duration TIMEOUT = Duration.ofSeconds(10);

        /** Default MCP protocol version advertised during initialization. */
        public static final String PROTOCOL_VERSION = "2024-11-05";

        private Defaults() {
        }
    }

    /**
     * Default endpoint paths relative to the MCP server base URL.
     */
    public static final class Endpoints {

        /** SSE subscription endpoint used to establish the event stream. */
        public static final String SSE = "/sse";

        /**
         * Default HTTP POST endpoint used to send JSON-RPC messages.
         *
         * <p>This is used as a fallback when an SSE {@code endpoint} event
         * is not emitted by the server.
         */
        public static final String MESSAGE = "/mcp/message";

        private Endpoints() {
        }
    }

    /**
     * SSE event type names emitted by the MCP server.
     */
    public static final class SseEvents {

        /** Event carrying the message POST endpoint URL. */
        public static final String ENDPOINT = "endpoint";

        /** Event carrying a JSON-RPC response. */
        public static final String MESSAGE = "message";

        private SseEvents() {
        }
    }

    /**
     * HTTP header names used by the MCP protocol.
     */
    public static final class Headers {

        /** Header for MCP protocol version negotiation. */
        public static final String MCP_PROTOCOL_VERSION = "MCP-Protocol-Version";

        private Headers() {
        }
    }

    /**
     * Standard MCP JSON-RPC method names.
     */
    public static final class Methods {

        /** Initialize handshake method. */
        public static final String INITIALIZE = "initialize";

        /** List resources method. */
        public static final String RESOURCES_LIST = "resources/list";

        /** Read resource method. */
        public static final String RESOURCES_READ = "resources/read";

        /** List prompts method. */
        public static final String PROMPTS_LIST = "prompts/list";

        /** Get prompt method. */
        public static final String PROMPTS_GET = "prompts/get";

        /** Call tool method. */
        public static final String TOOLS_CALL = "tools/call";

        /** List tools method. */
        public static final String TOOLS_LIST = "tools/list";

        private Methods() {
        }
    }

    /**
     * Standard MCP notification names used by the test client.
     */
    public static final class Notifications {

        /** Notification sent after initialize completes. */
        public static final String INITIALIZED = "notifications/initialized";

        private Notifications() {
        }
    }

    private McpTestClientConstants() {
    }
}


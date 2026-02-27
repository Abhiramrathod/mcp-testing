package mcp.toolkit.testing.framework.transport;

import mcp.toolkit.testing.framework.core.codec.McpJsonCodec;
import mcp.toolkit.testing.framework.core.constants.McpTestClientConstants;
import mcp.toolkit.testing.framework.core.util.McpValidation;
import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * {@link McpTransport} implementation using Server-Sent Events (SSE).
 *
 * <p>Connection lifecycle:
 * <ol>
 *   <li>{@code GET /sse} opens a persistent SSE stream.</li>
 *   <li>Optionally, the server sends an {@code endpoint} event containing the message POST URL.</li>
 *   <li>JSON-RPC requests are POSTed to the discovered endpoint, or to a fallback endpoint
 *       when no {@code endpoint} event is emitted.</li>
 *   <li>Responses arrive as {@code message} events.</li>
 * </ol>
 */
public class McpSseTransport implements McpTransport {

    private final URI sseEndpointUri;
    private final URI baseUri;
    private final String protocolVersion;
    private final Duration timeout;
    private final McpJsonCodec jsonCodec;

    private final ConcurrentHashMap<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final Object connectLock = new Object();

    private volatile URI messageEndpointUri;
    private volatile boolean connected;
    private volatile boolean closed;

    private HttpClient httpClient;
    private CompletableFuture<HttpResponse<Stream<String>>> sseConnectionFuture;

    /**
     * @param sseEndpointUri  fully resolved URI for the SSE subscription endpoint
     * @param baseUri         base server URI used to resolve relative endpoint paths
     * @param protocolVersion MCP protocol version to advertise in headers
     * @param timeout         timeout for connection establishment and individual requests
     * @param jsonCodec       codec for parsing JSON-RPC responses
     */
    public McpSseTransport(URI sseEndpointUri,
                           URI baseUri,
                           String protocolVersion,
                           Duration timeout,
                           McpJsonCodec jsonCodec) {
        this.sseEndpointUri = McpValidation.requireNonNull(sseEndpointUri, "sseEndpointUri");
        this.baseUri = McpValidation.requireNonNull(baseUri, "baseUri");
        this.messageEndpointUri = this.baseUri.resolve(McpTestClientConstants.Endpoints.MESSAGE);
        this.protocolVersion = McpValidation.requireNonNull(protocolVersion, "protocolVersion");
        this.timeout = timeout == null ? McpTestClientConstants.Defaults.TIMEOUT : timeout;
        this.jsonCodec = McpValidation.requireNonNull(jsonCodec, "jsonCodec");
    }

    @Override
    public void connect() {
        if (connected) {
            return;
        }
        synchronized (connectLock) {
            if (connected) {
                return;
            }
            if (closed) {
                throw new IllegalStateException("McpSseTransport is closed");
            }

            httpClient = HttpClient.newBuilder()
                    .connectTimeout(timeout)
                    .build();

            HttpRequest sseRequest = HttpRequest.newBuilder()
                    .uri(sseEndpointUri)
                    .header("Accept", "text/event-stream")
                    .GET()
                    .build();

            CountDownLatch connectLatch = new CountDownLatch(1);
            final Exception[] connectError = {null};

            sseConnectionFuture = httpClient.sendAsync(sseRequest, HttpResponse.BodyHandlers.ofLines());

            sseConnectionFuture.thenAcceptAsync(response -> {
                int status = response.statusCode();
                if (status >= 400) {
                    Exception error = new IllegalStateException("SSE connection failed with status " + status);
                    connectError[0] = error;
                    connectLatch.countDown();
                    failAllPending(error);
                    return;
                }
                connectLatch.countDown();
                processSseStream(response.body());
            }).exceptionally(ex -> {
                Exception error = ex instanceof Exception ? (Exception) ex
                        : new IllegalStateException("SSE stream error", ex);
                connectError[0] = error;
                connectLatch.countDown();
                failAllPending(error);
                return null;
            });

            try {
                if (!connectLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting for SSE stream from " + sseEndpointUri);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for SSE stream", ex);
            }

            if (connectError[0] != null) {
                throw new IllegalStateException(
                        "Failed to establish SSE stream to " + sseEndpointUri, connectError[0]);
            }
            connected = true;
        }
    }

    @Override
    public JsonNode sendRequest(String payload, long requestId) {
        requireConnected();
        CompletableFuture<JsonNode> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);
        try {
            postMessage(payload);
            return responseFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            pendingRequests.remove(requestId);
            throw new IllegalStateException(
                    "Timed out waiting for SSE response for request id " + requestId, ex);
        } catch (ExecutionException ex) {
            pendingRequests.remove(requestId);
            throw new IllegalStateException(
                    "Error receiving SSE response for request id " + requestId, ex.getCause());
        } catch (InterruptedException ex) {
            pendingRequests.remove(requestId);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for response", ex);
        }
    }

    @Override
    public void sendNotification(String payload) {
        requireConnected();
        postMessage(payload);
    }

    /**
     * Returns {@code true} if the SSE connection is active.
     */
    public boolean isConnected() {
        return connected && !closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        connected = false;
        failAllPending(new IllegalStateException("Transport closed"));
        if (sseConnectionFuture != null) {
            sseConnectionFuture.cancel(true);
        }
    }

    private void postMessage(String payload) {
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(messageEndpointUri)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header(McpTestClientConstants.Headers.MCP_PROTOCOL_VERSION, protocolVersion)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(timeout)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "MCP POST failed with status " + response.statusCode()
                                + ": " + response.body());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during MCP POST", ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }
            throw new IllegalStateException("Failed to POST MCP message to " + messageEndpointUri, ex);
        }
    }

    private void processSseStream(Stream<String> lines) {
        final String[] currentEvent = {null};
        final StringBuilder currentData = new StringBuilder();

        try {
            lines.takeWhile(line -> !closed).forEach(line -> {
                if (line.startsWith("event:")) {
                    currentEvent[0] = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (currentData.length() > 0) {
                        currentData.append('\n');
                    }
                    currentData.append(line.substring(5).trim());
                } else if (line.isBlank() && currentEvent[0] != null) {
                    handleEvent(currentEvent[0], currentData.toString());
                    currentEvent[0] = null;
                    currentData.setLength(0);
                }
            });
        } catch (Exception ignored) {
            // stream cancelled or connection closed
        } finally {
            if (!closed) {
                connected = false;
                failAllPending(new IllegalStateException(
                        "SSE connection closed unexpectedly"));
            }
        }
    }

    private void handleEvent(String eventType, String data) {
        if (data == null || data.isBlank()) {
            return;
        }
        switch (eventType) {
            case McpTestClientConstants.SseEvents.ENDPOINT -> {
                messageEndpointUri = baseUri.resolve(data);
            }
            case McpTestClientConstants.SseEvents.MESSAGE -> {
                JsonNode response = jsonCodec.parseJson(data);
                if (response != null && response.has("id")) {
                    long id = response.get("id").asLong(-1);
                    if (id >= 0) {
                        CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                        if (future != null) {
                            future.complete(response);
                        }
                    }
                }
            }
            default -> { /* ignore unknown event types */ }
        }
    }

    private void failAllPending(Exception cause) {
        pendingRequests.forEach((id, future) ->
                future.completeExceptionally(cause));
        pendingRequests.clear();
    }

    private void requireConnected() {
        if (!connected || closed) {
            throw new IllegalStateException("McpSseTransport is not connected");
        }
    }
}


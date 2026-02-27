package mcp.toolkit.testing.framework.client.rpc;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable record of a single JSON-RPC request/response exchange.
 */
public final class RpcExchange {

    /**
     * Outcome of an RPC exchange.
     */
    public enum Status {
        /** Response received successfully. */
        SUCCESS,
        /** Server returned a JSON-RPC error. */
        ERROR,
        /** No response received within timeout. */
        TIMEOUT,
        /** Transport or connection failure. */
        FAILED
    }

    private final long id;
    private final String method;
    private final JsonNode params;
    private final JsonNode request;
    private final JsonNode response;
    private final Instant sentAt;
    private final Instant receivedAt;
    private final Duration latency;
    private final Status status;
    private final String errorDetail;

    private RpcExchange(Builder builder) {
        this.id = builder.id;
        this.method = builder.method;
        this.params = builder.params;
        this.request = builder.request;
        this.response = builder.response;
        this.sentAt = builder.sentAt;
        this.receivedAt = builder.receivedAt;
        this.latency = (sentAt != null && receivedAt != null)
                ? Duration.between(sentAt, receivedAt) : null;
        this.status = builder.status;
        this.errorDetail = builder.errorDetail;
    }

    /** JSON-RPC request id. */
    public long id() { return id; }

    /** MCP method name (e.g. "tools/list"). */
    public String method() { return method; }

    /** Request params, or {@code null} for no-arg calls. */
    public JsonNode params() { return params; }

    /** Full JSON-RPC request payload. */
    public JsonNode request() { return request; }

    /** Full JSON-RPC response payload, or {@code null} on failure/timeout. */
    public JsonNode response() { return response; }

    /** Timestamp when the request was sent. */
    public Instant sentAt() { return sentAt; }

    /** Timestamp when the response arrived, or {@code null} if none. */
    public Instant receivedAt() { return receivedAt; }

    /** Round-trip latency, or {@code null} if no response received. */
    public Duration latency() { return latency; }

    /** Outcome of the exchange. */
    public Status status() { return status; }

    /** Human-readable error detail for ERROR/FAILED/TIMEOUT, or {@code null}. */
    public String errorDetail() { return errorDetail; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RpcExchange{");
        sb.append("id=").append(id);
        sb.append(", method='").append(method).append('\'');
        sb.append(", status=").append(status);
        if (latency != null) {
            sb.append(", latency=").append(latency.toMillis()).append("ms");
        }
        if (errorDetail != null) {
            sb.append(", error='").append(errorDetail).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private long id;
        private String method;
        private JsonNode params;
        private JsonNode request;
        private JsonNode response;
        private Instant sentAt;
        private Instant receivedAt;
        private Status status;
        private String errorDetail;

        Builder id(long id) { this.id = id; return this; }
        Builder method(String method) { this.method = method; return this; }
        Builder params(JsonNode params) { this.params = params; return this; }
        Builder request(JsonNode request) { this.request = request; return this; }
        Builder response(JsonNode response) { this.response = response; return this; }
        Builder sentAt(Instant sentAt) { this.sentAt = sentAt; return this; }
        Builder receivedAt(Instant receivedAt) { this.receivedAt = receivedAt; return this; }
        Builder status(Status status) { this.status = status; return this; }
        Builder errorDetail(String errorDetail) { this.errorDetail = errorDetail; return this; }

        RpcExchange build() {
            return new RpcExchange(this);
        }
    }
}


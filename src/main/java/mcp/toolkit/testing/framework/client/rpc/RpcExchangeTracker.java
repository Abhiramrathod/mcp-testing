package mcp.toolkit.testing.framework.client.rpc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Thread-safe tracker that records every JSON-RPC exchange for inspection
 * and test assertions.
 *
 * <p>Usage example:
 * <pre>{@code
 *   RpcExchangeTracker tracker = client.exchangeTracker();
 *   client.listTools();
 *   RpcExchange last = tracker.last();
 *   assert last.status() == RpcExchange.Status.SUCCESS;
 *   assert last.latency().toMillis() < 5000;
 * }</pre>
 */
public final class RpcExchangeTracker {

    private final CopyOnWriteArrayList<RpcExchange> exchanges = new CopyOnWriteArrayList<>();

    /**
     * Records a completed exchange.
     *
     * @param exchange the exchange to record
     */
    void record(RpcExchange exchange) {
        if (exchange != null) {
            exchanges.add(exchange);
        }
    }

    /**
     * Returns an unmodifiable snapshot of all recorded exchanges in order.
     *
     * @return list of exchanges
     */
    public List<RpcExchange> all() {
        return Collections.unmodifiableList(exchanges);
    }

    /**
     * Returns the most recent exchange, or empty if none recorded.
     *
     * @return optional last exchange
     */
    public Optional<RpcExchange> last() {
        return exchanges.isEmpty()
                ? Optional.empty()
                : Optional.of(exchanges.get(exchanges.size() - 1));
    }

    /**
     * Returns all exchanges for a given MCP method name.
     *
     * @param method MCP method (e.g. "tools/list", "tools/call")
     * @return filtered list of exchanges
     */
    public List<RpcExchange> forMethod(String method) {
        return exchanges.stream()
                .filter(e -> method.equals(e.method()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the exchange with the given JSON-RPC request id, or empty.
     *
     * @param requestId JSON-RPC id
     * @return optional exchange
     */
    public Optional<RpcExchange> byId(long requestId) {
        return exchanges.stream()
                .filter(e -> e.id() == requestId)
                .findFirst();
    }

    /**
     * Returns all exchanges that ended with the given status.
     *
     * @param status desired outcome
     * @return filtered list of exchanges
     */
    public List<RpcExchange> withStatus(RpcExchange.Status status) {
        return exchanges.stream()
                .filter(e -> e.status() == status)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns the total number of recorded exchanges.
     *
     * @return exchange count
     */
    public int size() {
        return exchanges.size();
    }

    /**
     * Clears all recorded exchanges.
     */
    public void clear() {
        exchanges.clear();
    }
}


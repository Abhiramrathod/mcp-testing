# MCP Testing Framework - Quick Reference & Examples

## Table of Contents

1. [Quick Start](#quick-start)
2. [Common Scenarios](#common-scenarios)
3. [Class Selection Guide](#class-selection-guide)
4. [Why Each Decision](#why-each-decision)
5. [Performance Tips](#performance-tips)
6. [Troubleshooting](#troubleshooting)

---

## Quick Start

### Installation & Setup

```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.15.2</version>
</dependency>
```

### Basic Example

```java
import mcp.toolkit.testing.framework.BaseMcpComponentTestSetup;
import com.fasterxml.jackson.databind.JsonNode;

@Test
void testBasicToolCall() {
    // Create and initialize client
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    // Use it
    JsonNode result = client.tools().callTool("add", 
        Map.of("a", 2, "b", 3));
    
    // Cleanup
    client.close();
}
```

---

## Common Scenarios

### Scenario 1: List and Filter Tools

**Goal:** Find all tools matching a pattern

```java
@Test
void findToolsByPattern() {
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    // Get all tool definitions
    JsonNode toolsArray = client.tools().allToolDefinitions();
    
    // Filter for tools containing "data" in name
    List<String> dataTools = new ArrayList<>();
    for (JsonNode tool : toolsArray) {
        String name = tool.path("name").asText();
        if (name.contains("data")) {
            dataTools.add(name);
        }
    }
    
    // Verify we found tools
    assertFalse(dataTools.isEmpty());
    
    client.close();
}
```

**Why This Works:**
- `allToolDefinitions()` returns array (not wrapped in "tools" object)
- We can iterate and filter
- `path()` is safe (returns missing node if not present)

---

### Scenario 2: Tool with Complex Arguments

**Goal:** Call tool passing structured object

```java
@Test
void callToolWithStructuredArgs() {
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    // Create complex argument object
    Map<String, Object> searchParams = Map.of(
        "query", "find all users",
        "filters", Map.of(
            "status", "active",
            "role", "admin"
        ),
        "pagination", Map.of(
            "limit", 10,
            "offset", 0
        )
    );
    
    // Call tool
    JsonNode result = client.tools()
        .callTool("search", searchParams);
    
    // Verify results
    JsonNode matches = result.path("matches");
    assertTrue(matches.isArray());
    
    client.close();
}
```

**Why This Works:**
- `jsonCodec.toArgumentsNode()` converts any object to JSON
- Nested Maps become nested JSON objects
- Tool receives proper JSON structure

---

### Scenario 3: Resource Management

**Goal:** List and read resources

```java
@Test
void listAndReadResources() {
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    // Step 1: List all resources
    JsonNode listResult = client.resources().listResources();
    JsonNode resourcesArray = listResult.path("resources");
    
    if (!resourcesArray.isEmpty()) {
        // Get first resource URI
        String firstUri = resourcesArray.get(0)
            .path("uri").asText();
        
        // Step 2: Read that resource
        JsonNode readResult = client.resources()
            .readResource(firstUri);
        
        // Get content
        JsonNode contents = readResult.path("contents");
        String text = contents.get(0)
            .path("text").asText();
        
        assertFalse(text.isEmpty());
    }
    
    client.close();
}
```

**Why This Works:**
- `listResources()` returns wrapped response
- `readResource()` takes URI, returns content
- `path()` returns missing node safely (won't throw)

---

### Scenario 4: Prompt with Arguments

**Goal:** Get prompt with template arguments filled

```java
@Test
void getPromptWithArguments() {
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    // Get prompt with arguments
    Map<String, String> args = Map.of(
        "language", "Spanish",
        "style", "formal"
    );
    
    JsonNode prompt = client.prompts()
        .getPrompt("translate", args);
    
    String promptText = prompt.path("prompt").asText();
    assertThat(promptText)
        .contains("Spanish")
        .contains("formal");
    
    client.close();
}
```

**Why This Works:**
- Prompt templates can have parameters
- Arguments fill in the template
- Server processes template, returns filled prompt

---

### Scenario 5: Testing Error Handling

**Goal:** Verify error when calling non-existent tool

```java
@Test
void toolNotFoundError() {
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    // Try to call non-existent tool
    assertThrows(AssertionError.class, () -> {
        client.tools().callTool("does-not-exist", Map.of());
    });
    
    // Inspect error exchange
    RpcExchange lastExchange = client.exchangeTracker()
        .last().get();
    
    assertEquals(RpcExchange.Status.ERROR, lastExchange.status());
    assertTrue(lastExchange.errorDetail()
        .contains("not found"));
    
    client.close();
}
```

**Why This Works:**
- `callTool()` validates tool exists first
- Server returns JSON-RPC error
- Framework converts to AssertionError
- Can inspect error in exchange tracker

---

### Scenario 6: Performance Testing

**Goal:** Measure tool call latency

```java
@Test
void measureToolLatency() {
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    // Call tool multiple times
    for (int i = 0; i < 100; i++) {
        client.tools().callTool("fast-operation", Map.of("id", i));
    }
    
    // Analyze latencies
    RpcExchangeTracker tracker = client.exchangeTracker();
    
    List<Long> latencies = tracker
        .forMethod("tools/call")
        .stream()
        .map(ex -> ex.latency().toMillis())
        .sorted()
        .collect(Collectors.toList());
    
    // Statistics
    long avg = latencies.stream()
        .mapToLong(Long::longValue)
        .average()
        .orElse(0L);
    
    long p95 = latencies.get((int)(latencies.size() * 0.95));
    long p99 = latencies.get((int)(latencies.size() * 0.99));
    
    System.out.println("Average: " + avg + "ms");
    System.out.println("P95: " + p95 + "ms");
    System.out.println("P99: " + p99 + "ms");
    
    assertTrue(avg < 100);  // Average under 100ms
    assertTrue(p99 < 500);  // 99th percentile under 500ms
    
    client.close();
}
```

**Why This Works:**
- Exchange tracker records all calls
- Each exchange has latency pre-calculated
- Can analyze performance characteristics
- Thread-safe queries work concurrently

---

### Scenario 7: Multi-threaded Testing

**Goal:** Test concurrent tool calls

```java
@Test
void testConcurrentToolCalls() throws Exception {
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    // Create thread pool
    ExecutorService executor = Executors
        .newFixedThreadPool(10);
    
    try {
        // Submit 100 concurrent tasks
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int id = i;
            futures.add(executor.submit(() -> {
                JsonNode result = client.tools()
                    .callTool("process-item", 
                        Map.of("id", id));
                assertTrue(result.has("status"));
            }));
        }
        
        // Wait for all to complete
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        
        // Verify all succeeded
        RpcExchangeTracker tracker = client.exchangeTracker();
        assertEquals(100, tracker.size());
        assertEquals(100, 
            tracker.withStatus(RpcExchange.Status.SUCCESS)
                .size());
        
    } finally {
        executor.shutdown();
        client.close();
    }
}
```

**Why This Works:**
- Transport layer is thread-safe
- ConcurrentHashMap handles request matching
- Tracker uses CopyOnWriteArrayList (thread-safe)
- Multiple threads can call simultaneously

---

### Scenario 8: Custom Exception Handling

**Goal:** Gracefully handle and inspect errors

```java
@Test
void handleErrorsGracefully() {
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    try {
        // This might fail
        JsonNode result = client.tools()
            .callTool("risky-operation", Map.of());
    } catch (AssertionError e) {
        // Tool call failed with JSON-RPC error
        RpcExchange error = client.exchangeTracker()
            .last().get();
        
        if (error.status() == RpcExchange.Status.ERROR) {
            System.out.println("Server error: " + 
                error.errorDetail());
            
            // Check error code from response
            JsonNode errorNode = error.response()
                .path("error");
            int code = errorNode.path("code").asInt();
            String message = errorNode.path("message")
                .asText();
            
            System.out.println("Code: " + code);
            System.out.println("Message: " + message);
        }
    } finally {
        client.close();
    }
}
```

**Why This Works:**
- Exchange contains full error details
- Can inspect error code and message
- Graceful error handling pattern
- Full diagnostic information available

---

### Scenario 9: Testing Server Initialization

**Goal:** Verify server capabilities after init

```java
@Test
void verifyServerCapabilities() {
    McpTestClient client = new McpTestClient(
        "http://localhost:8080");
    
    // Explicit initialization
    client.initialize();
    
    // Get server's reported capabilities
    JsonNode initResult = client.getInitializeResult();
    
    // Verify capabilities
    JsonNode capabilities = initResult.path("capabilities");
    
    assertTrue(capabilities.has("tools"));
    assertTrue(capabilities.has("resources"));
    assertTrue(capabilities.has("prompts"));
    
    // Check tool capability details
    JsonNode toolsCap = capabilities.path("tools");
    if (toolsCap.has("maxCallDepth")) {
        int maxDepth = toolsCap.path("maxCallDepth").asInt();
        assertTrue(maxDepth > 0);
    }
    
    client.close();
}
```

**Why This Works:**
- `initialize()` performs MCP handshake
- Server responds with capabilities
- `getInitializeResult()` returns that response
- Can verify server features before testing

---

## Class Selection Guide

### When to Use Each Class

#### McpTestClient vs Factory

```java
// Use Factory (Simpler)
McpTestClient client = BaseMcpComponentTestSetup
    .initializeMcpTestClient("http://localhost:8080");

// Use Direct Class (More Control)
McpTestClient client = new McpTestClient(
    "http://localhost:8080",
    "/custom/sse"  // Custom endpoint
);
```

**Choose Factory When:**
- Standard setup is fine
- Want minimal code
- Testing in isolation

**Choose Direct When:**
- Need custom endpoint path
- Need custom ObjectMapper
- Need custom protocol version

---

#### Domain Facades vs Generic RPC

```java
// Use Domain Facade (Recommended)
JsonNode result = client.tools().callTool("add", Map.of("a", 1, "b", 2));

// Use Generic RPC (Advanced)
JsonNode result = client.call("tools/call", 
    Map.of("name", "add", "arguments", Map.of("a", 1, "b", 2)));
```

**Choose Facade When:**
- Normal testing
- Type safety preferred
- Clear domain language

**Choose Generic When:**
- Testing custom methods
- Debugging protocol
- Advanced use cases

---

#### Direct Client vs With Guard

```java
// With Guard (Automatic Init)
JsonNode result = client.tools().listTools();
// Initialize happens automatically if needed

// Without Guard (Manual Control)
client.initialize();
JsonNode result = client.tools().listTools();
```

**Guard Handles:**
- Lazy initialization
- Thread-safe setup
- Idempotent re-init
- Transparent to caller

---

## Why Each Decision

### Why Jackson for JSON?

```
Alternatives:
- Gson: Simpler but less flexible
- org.json: Lightweight but limited
- json-simple: Minimal but lacks features

Jackson Chosen Because:
✓ Industry standard for Java
✓ Streaming, binding, and tree models
✓ High performance
✓ Extensive customization
✓ Wide framework support
```

### Why CopyOnWriteArrayList for Tracker?

```
Pattern: Exchange Tracker
- Writes: 1 per RPC call
- Reads: Unlimited during testing
  
CopyOnWriteArrayList Advantages:
✓ O(1) reads (snapshot read)
✓ Thread-safe without locks
✓ Good for read-heavy workloads
✓ Iterators safe during mutation

Alternative (ArrayList): 
✗ Need external synchronization
✗ All readers block on write
✗ Not ideal for test inspection

Alternative (LinkedList):
✗ O(n) reads
✗ Worse for traversal
```

### Why AtomicLong for Request IDs?

```
Pattern: Unique ID Generation
- Need: Thread-safe counter
- Used: Every RPC call

AtomicLong Advantages:
✓ Lock-free (CAS operations)
✓ Multiple threads read/write
✓ Guaranteed unique across all threads
✓ No synchronization overhead

Alternative (synchronized counter):
✗ All threads wait for lock
✗ Contention on high concurrency
✗ Slower than atomic operations
```

### Why Lazy Initialization?

```
Problem: Eager Init
client = new McpTestClient(...)
  → immediately connects to server
  → test hangs if server not ready
  → delays test startup

Solution: Lazy Init
client = new McpTestClient(...)
  → doesn't connect
client.tools().listTools()  // First use
  → connects on demand
  → faster test setup
  → predictable timing
```

### Why Builder Pattern for RpcExchange?

```
Problem: Many Optional Fields
- request, response, params
- sentAt, receivedAt, latency
- status, errorDetail
- All filled at different times

Solution: Builder
rpcExchange.Builder()
  .id(1)
  .sentAt(now)
  // ... fill fields as available
  .build()  // Create immutable

Benefits:
✓ Immutable result
✓ Incremental filling
✓ Validation at build
✓ Clear construction order
```

---

## Performance Tips

### Tip 1: Reuse Client Connection

```java
// ❌ SLOW: Create new client per test
@Test void test1() {
    McpTestClient client = new McpTestClient("http://localhost:8080");
    client.initialize();
    client.tools().listTools();
    client.close();
}

@Test void test2() {
    McpTestClient client = new McpTestClient("http://localhost:8080");
    client.initialize();
    // Connect again!
    // ...
}

// ✅ FAST: Share client across tests
@BeforeAll
static void setup() {
    client = new McpTestClient("http://localhost:8080");
    client.initialize();
}

@Test void test1() {
    client.tools().listTools();
}

@Test void test2() {
    client.resources().listResources();
}

@AfterAll
static void cleanup() {
    client.close();
}
```

### Tip 2: Batch Operations

```java
// ❌ SLOW: Multiple exchanges
for (int i = 0; i < 100; i++) {
    JsonNode tool = client.tools()
        .callTool("process", Map.of("id", i));
    // 100 round trips
}

// ✅ FASTER: If server supports batch
JsonNode batchResult = client.call("batch", Map.of(
    "requests", List.of(
        Map.of("method", "tools/call", "params", Map.of("name", "process", "arguments", Map.of("id", 1))),
        Map.of("method", "tools/call", "params", Map.of("name", "process", "arguments", Map.of("id", 2))),
        // ...
    )
));
```

### Tip 3: Filter Results Efficiently

```java
// ❌ SLOW: Download all, filter client-side
JsonNode allTools = client.tools().listTools();
List<String> bigTools = new ArrayList<>();
for (JsonNode tool : allTools.path("tools")) {
    // Client-side filtering
    if (tool.path("inputSchema").toString().length() > 1000) {
        bigTools.add(tool.path("name").asText());
    }
}

// ✅ FASTER: Filter server-side if supported
JsonNode bigTools = client.tools().listTools(
    Map.of("filter", Map.of("minSchemaSize", 1000))
);
```

### Tip 4: Use Concurrent Threads Wisely

```java
// ✅ Concurrent for I/O bound
ExecutorService executor = Executors.newFixedThreadPool(10);
for (int i = 0; i < 100; i++) {
    executor.submit(() -> {
        client.tools().callTool("api-call", Map.of(...));
    });
}

// ❌ Concurrent for CPU-heavy analysis
// (Thread overhead dominates)
ExecutorService executor = Executors.newFixedThreadPool(10);
for (int i = 0; i < 100; i++) {
    executor.submit(() -> {
        // Parse JSON, do calculations
        analyzeResult(client.tools().callTool(...));
    });
}
// Better: Sequential analysis
```

---

## Troubleshooting

### Issue 1: Connection Timeout

```
Error: "Timed out waiting for SSE stream from http://localhost:8080/sse"

Causes:
1. Server not running
2. Server using different port
3. Network firewall blocking
4. SSE endpoint doesn't exist

Solutions:
1. Check server is running
2. Verify URL: curl http://localhost:8080/sse
3. Check firewall rules
4. Verify endpoint path

Code Fix:
McpTestClient client = new McpTestClient(
    "http://localhost:8080",
    "/custom/sse"  // If different path
);
```

### Issue 2: Tool Not Found

```
Error: AssertionError: "No MCP tool found with name: my-tool"

Causes:
1. Tool doesn't exist on server
2. Tool name typo
3. Server not initialized yet

Solutions:
1. Check server has tool
2. Verify tool name exact spelling
3. Ensure client.initialize() called

Code Fix:
// Debug: List all tools first
JsonNode allTools = client.tools().allToolDefinitions();
System.out.println(allTools);  // See available

// Then use correct name
JsonNode result = client.tools().callTool("correct-name", Map.of());
```

### Issue 3: JSON Parse Error

```
Error: IllegalStateException: "Failed to serialize MCP payload"

Causes:
1. Object not JSON-serializable
2. Custom object without proper structure
3. Circular reference in object

Solutions:
1. Use standard types (Map, List, String, etc.)
2. Convert custom objects to Map first
3. Ensure no circular references

Code Fix:
// ❌ Won't work
client.tools().callTool("tool", myCustomObject);

// ✅ Convert to Map first
client.tools().callTool("tool", 
    Map.of("field1", myCustomObject.getField1(),
           "field2", myCustomObject.getField2())
);
```

### Issue 4: Null Pointer Exception

```
Error: NullPointerException at JsonNode.path()

Causes:
1. Response missing expected field
2. Calling before initialization
3. Using path() on null

Solutions:
1. Use path() not get() (doesn't throw)
2. Ensure initialize() called
3. Check hasValue() before accessing

Code Fix:
// ❌ Throws if missing
String name = result.get("name").asText();

// ✅ Safe with path
String name = result.path("name").asText("");  // Default ""
```

### Issue 5: Concurrent Access Issues

```
Error: ConcurrentModificationException in tracker

Causes:
1. Modifying tracker while iterating
2. Clearing tracker during reads
3. Multiple threads without sync

Solutions:
1. Don't clear during iteration
2. Don't clear during active testing
3. Use thread-safe queries

Code Fix:
// ❌ Can cause issues
for (RpcExchange ex : tracker.all()) {
    if (shouldRemove(ex)) {
        tracker.clear();  // Modifying during iteration
    }
}

// ✅ Safe
tracker.clear();  // Do this after collecting results
List<RpcExchange> toKeep = tracker.all().stream()
    .filter(ex -> !shouldRemove(ex))
    .collect(Collectors.toList());
```

### Issue 6: Wrong Endpoint Path

```
Error: Connection succeeds but requests timeout

Causes:
1. POST endpoint wrong
2. Server using different message endpoint
3. No endpoint event from server

Solutions:
1. Check /sse returns endpoint event
2. Use custom path if needed
3. Inspect transport logs

Code Fix:
// Debug: Check what endpoint server sends
McpSseTransport transport = ...;  // Get transport
// Check messageEndpointUri after connect

// Or use explicit endpoint
McpTestClient client = new McpTestClient(
    "http://localhost:8080",
    "/events"  // Custom SSE path
);
```

---

## Summary

This framework provides:
- ✅ Clean, type-safe API for testing MCP servers
- ✅ Comprehensive request/response tracking
- ✅ Thread-safe concurrent operations
- ✅ Clear error messages and diagnostics
- ✅ Performance measurement capabilities
- ✅ Factory methods for common patterns

Use this guide to quickly test MCP servers with confidence!


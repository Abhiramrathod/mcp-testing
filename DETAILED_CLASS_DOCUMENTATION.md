# MCP Testing Framework - Comprehensive Class Documentation

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Core Components](#core-components)
3. [Detailed Class Documentation](#detailed-class-documentation)
4. [Design Patterns](#design-patterns)
5. [Why Each Component Exists](#why-each-component-exists)

---

## Architecture Overview

### What is MCP?

**MCP (Model Context Protocol)** is a standardized protocol for AI models to interact with external tools, resources, and data sources. Our testing framework allows you to programmatically test MCP servers using JSON-RPC communication over HTTP/SSE.

### Communication Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Your Test Code                           │
│              (Test Classes & Assertions)                    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                   McpTestClient                             │
│         (High-level entry point, initialization)           │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌────────────────────────────────────────────────────────────┐
│              Domain Facades                                │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ McpToolDirectory    │ McpResourceDirectory          │ │
│  │ McpPromptDirectory  │ (Typed operations)            │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
                           ↓
┌────────────────────────────────────────────────────────────┐
│              McpInitializationGuard                        │
│         (Ensures lazy initialization before ops)          │
└────────────────────────────────────────────────────────────┘
                           ↓
┌────────────────────────────────────────────────────────────┐
│         McpRpcClient + RpcExchangeTracker                  │
│    (JSON-RPC marshaling, request/response tracking)       │
└────────────────────────────────────────────────────────────┘
                           ↓
┌────────────────────────────────────────────────────────────┐
│              McpSseTransport                              │
│     (HTTP/SSE communication with MCP server)              │
└────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                  MCP Server                                 │
│          (Testing target - tools, resources, etc.)         │
└─────────────────────────────────────────────────────────────┘
```

---

## Core Components

### Why We Need Each Layer

1. **Test Client** - Simplifies usage from test code
2. **Domain Facades** - Type-safe, discoverable API
3. **Initialization Guard** - Lazy, idempotent initialization
4. **RPC Client** - Low-level JSON-RPC handling
5. **Transport** - Network communication abstraction
6. **Utilities** - Shared logic, constants, validation

---

## Detailed Class Documentation

### 1. McpTestClient

**File:** `src/main/java/mcp/toolkit/testing/framework/McpTestClient.java`

**Purpose:** Main entry point and orchestrator for all MCP testing operations.

**Why We Use It:**
- Provides a single, clean interface to interact with an MCP server
- Manages the entire lifecycle (connection, initialization, cleanup)
- Encapsulates all components (RPC client, transport, directories)
- Implements AutoCloseable for resource management
- Handles thread-safe initialization

**Key Responsibilities:**

```
┌─────────────────────────────────────────────────────┐
│             McpTestClient                          │
├─────────────────────────────────────────────────────┤
│ INITIALIZATION MANAGEMENT                          │
│ • Lazy initialization on first operation          │
│ • Thread-safe init with synchronization lock      │
│ • Idempotent - safe to call initialize() multiple │
│   times                                            │
│                                                    │
│ DOMAIN ACCESS                                     │
│ • tools() - Returns McpToolDirectory             │
│ • resources() - Returns McpResourceDirectory     │
│ • prompts() - Returns McpPromptDirectory        │
│                                                    │
│ LIFECYCLE MANAGEMENT                             │
│ • initialize() - Establish connection            │
│ • isInitialized() - Check status                |
│ • close() - Cleanup resources                    │
│                                                    │
│ GENERIC RPC ACCESS                               │
│ • call(method, params) - Direct RPC calls       │
│ • exchangeTracker() - Inspection & assertions   │
└─────────────────────────────────────────────────────┘
```

**Class Fields:**

| Field | Type | Purpose |
|-------|------|---------|
| `objectMapper` | ObjectMapper | Jackson mapper for JSON serialization |
| `protocolVersion` | String | MCP protocol version (e.g., "2024-11-05") |
| `initGuard` | McpInitializationGuard | Ensures initialization before operations |
| `transport` | McpTransport | Handles HTTP/SSE communication |
| `jsonCodec` | McpJsonCodec | JSON utility helper |
| `rpcClient` | McpRpcClient | JSON-RPC client with tracking |
| `toolDirectory` | McpToolDirectory | Tool operations facade |
| `resourceDirectory` | McpResourceDirectory | Resource operations facade |
| `promptDirectory` | McpPromptDirectory | Prompt operations facade |
| `initialized` | volatile boolean | Thread-safe initialization flag |
| `initializeResult` | JsonNode | Server's initialize response |
| `initLock` | Object | Synchronization lock for thread-safe init |

**Example Usage:**

```java
// Create client with default endpoint (/sse)
McpTestClient client = new McpTestClient("http://localhost:8080");

// Initialize explicitly (or happens lazily)
client.initialize();

// Use domain facades
JsonNode tools = client.tools().listTools();
JsonNode result = client.tools().callTool("my-tool", new Args());

// Check initialization status
if (client.isInitialized()) {
    JsonNode capabilities = client.getInitializeResult();
}

// Generic RPC call
JsonNode response = client.call("custom/method", myParams);

// Inspect exchanges
RpcExchangeTracker tracker = client.exchangeTracker();
RpcExchange lastExchange = tracker.last().orElse(null);

// Cleanup
client.close();
```

**Why This Design?**
- **Single Responsibility:** Acts as facade, delegates to specialized components
- **Clean API:** Users see simple methods, complexity hidden
- **Resource Safety:** AutoCloseable pattern ensures cleanup
- **Lazy Init:** Connection only established when needed
- **Thread Safe:** Multiple threads can use safely

---

### 2. BaseMcpComponentTestSetup

**File:** `src/main/java/mcp/toolkit/testing/framework/BaseMcpComponentTestSetup.java`

**Purpose:** Factory class providing simplified client creation and initialization for tests.

**Why We Use It:**
- Reduces boilerplate in test code
- Ensures clients are always initialized
- Factory pattern makes it easy to extend
- Single responsibility: component creation

**Key Methods:**

```java
public static McpTestClient initializeMcpTestClient(String baseUrl)
// Creates and initializes client with default /sse endpoint
// Returns: ready-to-use client

public static McpTestClient initializeMcpTestClient(String baseUrl, String sseEndpointPath)
// Creates and initializes client with custom endpoint
// Returns: ready-to-use client
```

**Why Factory Pattern?**
- Centralizes initialization logic
- Easy to change creation strategy
- Reduces duplication across tests
- One point to add setup/teardown logic

**Example Usage:**

```java
@Test
void testToolInvocation() {
    // One-liner to get initialized client
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    // Ready to use immediately
    JsonNode result = client.tools().listTools();
    
    client.close();
}
```

---

### 3. McpTransport (Interface)

**File:** `src/main/java/mcp/toolkit/testing/framework/transport/McpTransport.java`

**Purpose:** Abstract interface for transport mechanisms (HTTP/SSE, WebSocket, etc.)

**Why We Use It:**
- **Abstraction:** Isolates transport implementation from client code
- **Testability:** Can mock for unit tests
- **Flexibility:** Easy to add new transports (WebSocket, gRPC, etc.)
- **Separation of Concerns:** Network logic separate from RPC logic

**Core Contracts:**

| Method | Purpose | Notes |
|--------|---------|-------|
| `connect()` | Establish transport connection | Idempotent, safe to call multiple times |
| `sendRequest(payload, id)` | Send request, wait for response | Blocks until response received or timeout |
| `sendNotification(payload)` | Send fire-and-forget message | No response expected |
| `close()` | Close connection, free resources | Should be idempotent |

**Why This Interface?**

```
Without Interface (Tightly Coupled):
  McpTestClient → McpSseTransport (tightly bound)
  Hard to test without real HTTP
  Difficult to add new transports

With Interface (Loosely Coupled):
  McpTestClient → McpTransport (abstraction)
    ↓
  McpSseTransport (one implementation)
  Easy to mock for testing
  Easy to add WebSocket, etc.
```

**Implementation Contract:**
- Must be thread-safe
- Must handle connection failures gracefully
- Must match request IDs with responses
- Must respect timeouts

---

### 4. McpSseTransport

**File:** `src/main/java/mcp/toolkit/testing/framework/transport/McpSseTransport.java`

**Purpose:** Server-Sent Events (SSE) implementation of McpTransport for HTTP/SSE communication.

**Why We Use SSE?**
- **Bidirectional:** Server can push messages to client
- **Event-Based:** Natural fit for async message handling
- **HTTP-Based:** No special networking required
- **Persistent Connection:** Reduces latency vs polling
- **Standard:** Well-supported across frameworks

**How SSE Works in MCP:**

```
Client                                    Server
  │                                         │
  ├─── GET /sse ─────────────────────────→ │ (Subscribe to events)
  │                                         │
  │ ← ─ ─ ─ ─ (event: endpoint) ─ ─ ─ ← ─ ┤ (Optional: endpoint discovery)
  │                                         │
  ├─── POST /mcp/message ─────────────────→ │ (Send RPC request)
  │   (JSON-RPC with id=1)                 │
  │                                         │
  │ ← ─ ─ (event: message) ─ ─ ─ ─ ─ ← ─ ┤ (Response with id=1)
  │                                         │
  ├─── POST /mcp/message ─────────────────→ │ (Send another request)
  │   (JSON-RPC with id=2)                 │
  │                                         │
  │ ← ─ ─ (event: message) ─ ─ ─ ─ ─ ← ─ ┤ (Response with id=2)
```

**Key Components:**

| Component | Purpose |
|-----------|---------|
| `sseEndpointUri` | GET endpoint to open SSE stream |
| `messageEndpointUri` | POST endpoint for requests (discovered or fallback) |
| `pendingRequests` | ConcurrentHashMap tracking requests waiting for responses |
| `httpClient` | Java 11+ HttpClient for async HTTP |
| `sseConnectionFuture` | Async future for SSE stream |

**Why This Implementation?**

1. **ConcurrentHashMap for pendingRequests:**
   - Thread-safe without heavy locking
   - Fast concurrent access
   - O(1) lookup by request ID

2. **HttpClient.BodyHandlers.ofLines():**
   - Streams responses line-by-line
   - Memory efficient for long connections
   - Natural fit for SSE format

3. **CountDownLatch for connect():**
   - Wait for connection without blocking threads
   - Timeout support built-in
   - Clean wait semantics

4. **CompletableFuture for responses:**
   - Non-blocking response waiting
   - Integrates with modern Java async
   - Timeout support

**Example SSE Event Processing:**

```
Raw SSE Stream:
  event: endpoint
  data: /mcp/message
  
  event: message
  data: {"id":1,"result":{"tools":[...]}}

Parsed by processSseStream():
  Event Type: "endpoint"
  Data: "/mcp/message"
  Action: Update messageEndpointUri
  
  Event Type: "message"  
  Data: JSON string
  Action: Parse, extract id, find pending request, complete future
```

**Thread Safety:**
- `pendingRequests`: ConcurrentHashMap (thread-safe)
- `connected`, `closed`: volatile flags
- `connectLock`: synchronized block for connection setup
- Lines processed by stream safely without blocking

---

### 5. McpRpcClient

**File:** `src/main/java/mcp/toolkit/testing/framework/client/rpc/McpRpcClient.java`

**Purpose:** Low-level JSON-RPC 2.0 client that marshals requests/responses and tracks all exchanges.

**Why We Use It:**
- **Protocol Compliance:** Ensures JSON-RPC 2.0 format
- **Request Tracking:** Records all exchanges for debugging
- **Error Handling:** Validates responses, reports errors
- **ID Management:** Automatic request ID sequencing

**JSON-RPC 2.0 Request Format:**

```json
{
  "jsonrpc": "2.0",
  "method": "tools/list",
  "params": {},
  "id": 1
}
```

**Response Format (Success):**

```json
{
  "jsonrpc": "2.0",
  "result": {
    "tools": [...]
  },
  "id": 1
}
```

**Response Format (Error):**

```json
{
  "jsonrpc": "2.0",
  "error": {
    "code": -32601,
    "message": "Method not found"
  },
  "id": 1
}
```

**Key Responsibilities:**

| Method | Purpose |
|--------|---------|
| `callAndRequireResult()` | Send request, validate response, return result |
| `sendNotification()` | Send notification (no id, no response) |
| `buildRequest()` | Construct JSON-RPC request object |
| `requireResult()` | Validate response has valid result |
| `exchangeTracker()` | Access tracker for assertions |

**Request ID Sequencing:**

```java
private final AtomicLong idSequence; // Thread-safe counter

public JsonNode callAndRequireResult(String method, ...) {
    long id = idSequence.getAndIncrement(); // 1, 2, 3, ...
    // Use id in request
    // Wait for response with matching id
}
```

Why AtomicLong?
- Thread-safe increment without locks
- Multiple threads can call simultaneously
- Guaranteed unique IDs

**Response Validation:**

```
Response received
  ↓
Check if null → throw "No MCP response"
  ↓
Check for "error" field → throw "MCP error: ..."
  ↓
Extract "result" → check not null → return
  ↓
Success ✓
```

**Example Flow:**

```java
// Request 1
JsonNode result = rpcClient.callAndRequireResult("tools/list", 
    () -> jsonCodec.toJsonNode(Map.of()));

// Request 2 (can be concurrent)
JsonNode result2 = rpcClient.callAndRequireResult("resources/list",
    () -> jsonCodec.toJsonNode(Map.of()));

// Both tracked in exchangeTracker
List<RpcExchange> all = rpcClient.exchangeTracker().all();
```

---

### 6. RpcExchange

**File:** `src/main/java/mcp/toolkit/testing/framework/client/rpc/RpcExchange.java`

**Purpose:** Immutable record of a single JSON-RPC request/response exchange.

**Why We Use It:**
- **Immutability:** Thread-safe, can't be modified
- **Complete Tracing:** All exchange details in one object
- **Testing:** Perfect for assertions
- **Debugging:** Rich information for investigation

**Status Enum:**

```java
public enum Status {
    SUCCESS,    // Response received, no error
    ERROR,      // Response received with JSON-RPC error
    TIMEOUT,    // No response within timeout
    FAILED      // Transport/connection failure
}
```

**Fields Tracked:**

| Field | Type | Purpose |
|-------|------|---------|
| `id` | long | JSON-RPC request ID |
| `method` | String | MCP method name ("tools/list") |
| `params` | JsonNode | Request parameters |
| `request` | JsonNode | Full request payload |
| `response` | JsonNode | Full response payload (null if failed) |
| `sentAt` | Instant | Timestamp when request sent |
| `receivedAt` | Instant | Timestamp when response received |
| `latency` | Duration | Round-trip time (auto-calculated) |
| `status` | Status | Exchange outcome |
| `errorDetail` | String | Error message if failed/timeout |

**Builder Pattern:**

```java
RpcExchange.Builder builder = RpcExchange.builder()
    .id(1)
    .method("tools/list")
    .params(params)
    .request(request)
    .sentAt(Instant.now());

// Later...
builder.response(response)
    .receivedAt(Instant.now())
    .status(RpcExchange.Status.SUCCESS);

RpcExchange exchange = builder.build(); // Immutable
```

**Why Builder Pattern?**
- Immutable final object
- Can fill fields incrementally as request progresses
- Validation at build time
- Clean, readable construction

**Example Usage in Tests:**

```java
@Test
void testPerformance() {
    client.tools().listTools();
    
    RpcExchange exchange = client.exchangeTracker().last().get();
    
    // Assertions on exchange
    assertEquals(RpcExchange.Status.SUCCESS, exchange.status());
    assertTrue(exchange.latency().toMillis() < 1000);
    assertEquals("tools/list", exchange.method());
}
```

---

### 7. RpcExchangeTracker

**File:** `src/main/java/mcp/toolkit/testing/framework/client/rpc/RpcExchangeTracker.java`

**Purpose:** Thread-safe tracker recording all JSON-RPC exchanges for inspection and assertions.

**Why We Use It:**
- **Test Assertions:** Verify correct methods called, correct data sent
- **Debugging:** Inspect all requests/responses
- **Performance:** Measure latencies
- **Error Investigation:** See what failed and why
- **Thread Safe:** Multiple threads can query safely

**Thread Safety Implementation:**

```java
private final CopyOnWriteArrayList<RpcExchange> exchanges 
    = new CopyOnWriteArrayList<>();
```

Why CopyOnWriteArrayList?
- Reads are extremely fast (snapshot read)
- Writes are thread-safe
- Ideal for "write once, read many" pattern
- Good for ~100-1000 items
- Used by tests which read more than write

**Query Methods:**

| Method | Purpose | Example |
|--------|---------|---------|
| `all()` | Get all exchanges | `tracker.all()` |
| `last()` | Get most recent | `tracker.last().get()` |
| `forMethod(String)` | Filter by method | `tracker.forMethod("tools/list")` |
| `byId(long)` | Find by request ID | `tracker.byId(1)` |
| `withStatus(Status)` | Filter by status | `tracker.withStatus(SUCCESS)` |
| `size()` | Count exchanges | `tracker.size()` |
| `clear()` | Reset tracker | `tracker.clear()` |

**Example Usage:**

```java
@Test
void testMultipleRequests() {
    client.tools().listTools();
    client.tools().listTools();
    client.resources().listResources();
    
    RpcExchangeTracker tracker = client.exchangeTracker();
    
    // All exchanges
    assertEquals(3, tracker.size());
    
    // Specific method
    assertEquals(2, tracker.forMethod("tools/list").size());
    
    // Check all succeeded
    assertEquals(3, tracker.withStatus(SUCCESS).size());
    
    // Check latest
    assertEquals("resources/list", tracker.last().get().method());
    
    // Performance check
    tracker.all().forEach(ex -> 
        assertTrue(ex.latency().toMillis() < 5000)
    );
}
```

---

### 8. McpToolDirectory

**File:** `src/main/java/mcp/toolkit/testing/framework/client/tools/McpToolDirectory.java`

**Purpose:** Type-safe facade for MCP tool operations (discovery and invocation).

**Why We Use It:**
- **Type Safety:** Specific methods for tool operations, not generic RPC
- **Discoverable API:** IDEs can autocomplete tool methods
- **Error Checking:** Validates tool existence before calling
- **Consistency:** Same pattern as other directories (resources, prompts)

**Core Operations:**

```java
// Discovery
JsonNode listTools() 
  // Returns: {"tools": [{name, description, inputSchema}, ...]}

JsonNode listTools(Object params)
  // Same as above but with custom pagination/filtering params

JsonNode allToolDefinitions()
  // Returns just the tools array
  // Validates it's actually an array

JsonNode toolDefinition(String name)
  // Find specific tool by name
  // Throws AssertionError if not found

// Invocation
JsonNode callTool(String name, Object args)
  // Call tool by name with arguments
  // Builds proper JSON-RPC params
```

**Example Workflow:**

```java
// Step 1: List all tools
JsonNode listResult = directory.listTools();
// Returns: {
//   "tools": [
//     {"name": "calculator", "description": "...", ...},
//     {"name": "weather", "description": "...", ...}
//   ]
// }

// Step 2: Find specific tool
JsonNode toolDef = directory.toolDefinition("calculator");
// Returns: {"name": "calculator", "description": "...", ...}

// Step 3: Call tool
JsonNode result = directory.callTool("calculator", new CalcArgs(2, 2));
// Internally calls: {"method": "tools/call", "params": {"name": "calculator", "arguments": {...}}}
```

**Why Separate Facade?**
- Encapsulates tool-specific logic
- Can add tool-specific helper methods
- Easier to use than raw RPC
- Consistent with ResourceDirectory, PromptDirectory

---

### 9. McpResourceDirectory

**File:** `src/main/java/mcp/toolkit/testing/framework/client/resources/McpResourceDirectory.java`

**Purpose:** Type-safe facade for MCP resource operations (listing and reading).

**Why We Use It:**
- **Type Safety:** Specific methods for resource operations
- **Abstraction:** Hide RPC details, use domain language
- **Consistency:** Same pattern as other directories
- **Error Messages:** Resource-specific validation

**Core Operations:**

```java
// Discovery
JsonNode listResources()
  // Returns: {"resources": [{"uri": "...", "name": "...", ...}, ...]}

JsonNode listResources(Object params)
  // Same with optional cursor/pagination

// Access
JsonNode readResource(String uri)
  // Read resource content by URI
  // Returns: {"contents": [{"uri": "...", "mimeType": "...", "text": "..."}, ...]}
```

**Example Usage:**

```java
// List available resources
JsonNode resources = directory.listResources();
// {"resources": [
//   {"uri": "file:///home/user/data.json", "name": "data.json", ...},
//   {"uri": "file:///home/user/config.yaml", "name": "config.yaml", ...}
// ]}

// Read specific resource
JsonNode content = directory.readResource("file:///home/user/data.json");
// {"contents": [{
//   "uri": "file:///home/user/data.json",
//   "mimeType": "application/json",
//   "text": "{...json content...}"
// }]}
```

---

### 10. McpPromptDirectory

**File:** `src/main/java/mcp/toolkit/testing/framework/client/prompts/McpPromptDirectory.java`

**Purpose:** Type-safe facade for MCP prompt operations (listing and retrieval).

**Why We Use It:**
- **Type Safety:** Specific methods for prompt operations
- **Domain Language:** Use prompt terminology, not raw RPC
- **Consistency:** Same pattern as tool and resource directories
- **Convenience:** Pre-built parameters

**Core Operations:**

```java
// Discovery
JsonNode listPrompts()
  // Returns: {"prompts": [{"name": "...", "description": "...", ...}, ...]}

JsonNode listPrompts(Object params)
  // Same with optional filtering

// Retrieval
JsonNode getPrompt(String name)
  // Get prompt by name without arguments

JsonNode getPrompt(String name, Object args)
  // Get prompt by name with optional arguments
  // Arguments filled into prompt template
```

**Example Usage:**

```java
// List available prompts
JsonNode prompts = directory.listPrompts();
// {"prompts": [
//   {"name": "summarize", "description": "Summarize text", ...},
//   {"name": "analyze", "description": "Analyze code", ...}
// ]}

// Get specific prompt
JsonNode prompt = directory.getPrompt("summarize");
// {"prompt": "Please summarize the following text: ..."}

// Get prompt with arguments
Map<String, Object> args = Map.of("style", "bullet-points");
JsonNode result = directory.getPrompt("summarize", args);
// Fills in arguments where template has placeholders
```

---

### 11. McpJsonCodec

**File:** `src/main/java/mcp/toolkit/testing/framework/core/codec/McpJsonCodec.java`

**Purpose:** Utility class for JSON serialization/deserialization using Jackson.

**Why We Use It:**
- **Abstraction:** Hide Jackson complexity
- **Consistency:** All JSON conversion goes through one place
- **Convenience:** Fluent building API
- **Error Handling:** Graceful null handling

**Key Methods:**

```java
// Building JSON
ObjectNode buildParams(Consumer<ObjectNode> paramsWriter)
  // Use: jsonCodec.buildParams(params -> {
  //   params.put("name", "value");
  //   params.putObject("nested");
  // })

// Converting objects to JSON
JsonNode toJsonNode(Object value)
  // Convert any object to JsonNode
  // Returns null if input is null

JsonNode toArgumentsNode(Object value)
  // Like toJsonNode but returns empty {} if null
  // Used for tool/prompt arguments (can't be null)

// Parsing JSON
JsonNode parseJson(String data)
  // Parse JSON string to JsonNode
  // Returns null on parsing failure (graceful)

// Serializing JSON
String toJson(JsonNode payload)
  // Convert JsonNode to string
  // Throws IllegalStateException if serialization fails
```

**Example Usage:**

```java
// Build request parameters
ObjectNode params = jsonCodec.buildParams(p -> {
    p.put("method", "tools/list");
    ObjectNode tools = p.putObject("options");
    tools.put("includeDescriptions", true);
});

// Convert object to JSON
List<String> myList = List.of("a", "b", "c");
JsonNode node = jsonCodec.toJsonNode(myList);

// Parse JSON string
String json = "{\"name\": \"test\"}";
JsonNode parsed = jsonCodec.parseJson(json);

// Serialize to string
String serialized = jsonCodec.toJson(parsed);
```

**Why Jackson?**
- Industry standard JSON library for Java
- High performance
- Flexible (streaming, binding, tree model)
- Widely supported by frameworks

---

### 12. McpTestClientConstants

**File:** `src/main/java/mcp/toolkit/testing/framework/core/constants/McpTestClientConstants.java`

**Purpose:** Centralized configuration constants used throughout framework.

**Why We Use It:**
- **Single Source of Truth:** One place to change protocol version, endpoints, etc.
- **No Magic Strings:** Avoid errors from typos
- **DRY Principle:** Don't Repeat Yourself
- **Easy Customization:** Change defaults once, affects everywhere

**Constant Categories:**

```java
// Defaults
Defaults.TIMEOUT              // 10 seconds
Defaults.PROTOCOL_VERSION     // "2024-11-05"

// Endpoint paths
Endpoints.SSE                 // "/sse"
Endpoints.MESSAGE             // "/mcp/message"

// SSE event types
SseEvents.ENDPOINT            // "endpoint" event
SseEvents.MESSAGE             // "message" event

// HTTP headers
Headers.MCP_PROTOCOL_VERSION  // "MCP-Protocol-Version"

// JSON-RPC methods
Methods.INITIALIZE            // "initialize"
Methods.TOOLS_LIST           // "tools/list"
Methods.TOOLS_CALL           // "tools/call"
Methods.RESOURCES_LIST       // "resources/list"
Methods.RESOURCES_READ       // "resources/read"
Methods.PROMPTS_LIST         // "prompts/list"
Methods.PROMPTS_GET          // "prompts/get"

// Notification names
Notifications.INITIALIZED     // "notifications/initialized"
```

**Example Usage:**

```java
// Don't do this (magic strings):
transport.sendRequest(json, "http://localhost:8080/sse");

// Do this (constants):
URI sseUri = baseUri.resolve(McpTestClientConstants.Endpoints.SSE);

// When server changes protocol version:
// Change ONE place:
Defaults.PROTOCOL_VERSION = "2025-01-01";
// All code automatically uses new version
```

---

### 13. McpTestClientUtils

**File:** `src/main/java/mcp/toolkit/testing/framework/core/util/McpTestClientUtils.java`

**Purpose:** Utility methods for building components and normalizing URLs/paths.

**Why We Use It:**
- **Component Assembly:** Wires all components together
- **URL Normalization:** Handles trailing slashes, leading slashes consistently
- **Initialization Params:** Builds MCP handshake payload
- **Reusable Logic:** Shared across multiple classes

**Key Methods:**

```java
// Building components
ClientComponents buildComponents(
    ObjectMapper objectMapper,
    String protocolVersion,
    String baseUrl,
    String sseEndpointPath,
    McpInitializationGuard initGuard)
  // Returns: All wired components ready to use
  // Handles: creating transport, RPC client, directories

// Initialize request building
ObjectNode buildInitializeParams(
    McpJsonCodec jsonCodec,
    String protocolVersion)
  // Returns: Proper MCP initialize request
  // Includes: protocol version, capabilities, client info

// URL normalization
URI normalizeBaseUri(String baseUrl)
  // Ensures trailing slash
  // "http://localhost" → "http://localhost/"

String normalizePath(String path)
  // Ensures leading slash
  // "sse" → "/sse"

// Protocol version resolution
String resolveProtocolVersion(String protocolVersion)
  // If null, returns default
  // Otherwise returns provided value
```

**Component Building Logic:**

```java
ClientComponents buildComponents(...) {
    // 1. Resolve endpoints
    ResolvedEndpoints endpoints = resolveEndpoints(baseUrl, sseEndpointPath);
    
    // 2. Create JSON codec
    McpJsonCodec jsonCodec = new McpJsonCodec(objectMapper);
    
    // 3. Create ID sequence
    AtomicLong idSequence = new AtomicLong(1);
    
    // 4. Create transport
    McpTransport transport = new McpSseTransport(
        endpoints.sseEndpointUri(),
        endpoints.baseUri(),
        protocolVersion,
        timeout,
        jsonCodec
    );
    
    // 5. Create RPC client
    McpRpcClient rpcClient = new McpRpcClient(
        transport, idSequence, jsonCodec
    );
    
    // 6. Create directories
    McpToolDirectory toolDirectory = new McpToolDirectory(
        initGuard, rpcClient, jsonCodec
    );
    McpResourceDirectory resourceDirectory = new McpResourceDirectory(
        initGuard, rpcClient, jsonCodec
    );
    McpPromptDirectory promptDirectory = new McpPromptDirectory(
        initGuard, rpcClient, jsonCodec
    );
    
    // 7. Return aggregated components
    return new ClientComponents(
        transport, jsonCodec, rpcClient,
        toolDirectory, resourceDirectory, promptDirectory
    );
}
```

**Initialize Params Example:**

```java
// Builds this JSON-RPC request:
{
  "jsonrpc": "2.0",
  "id": 0,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "roots": {
        "listChanged": true
      },
      "sampling": {}
    },
    "clientInfo": {
      "name": "mcp-test-client",
      "version": "1.0.0"
    }
  }
}
```

---

### 14. McpValidation

**File:** `src/main/java/mcp/toolkit/testing/framework/core/util/McpValidation.java`

**Purpose:** Reusable argument validation helpers.

**Why We Use It:**
- **Consistent Validation:** All null checks done same way
- **Clear Error Messages:** Tells developer what argument is invalid
- **Reduce Boilerplate:** Don't repeat null checks everywhere
- **Early Failures:** Fail fast with clear message

**Methods:**

```java
<T> T requireNonNull(T value, String name)
  // Validates value is not null
  // Throws: NullPointerException with message
  // Example: "Required argument 'uri' must not be null."

String requireNotBlank(String value, String name)
  // Validates string is not null and not empty
  // Throws: IllegalArgumentException with message
  // Example: "Required argument 'method' must not be blank."
```

**Example Usage:**

```java
public void readResource(String uri) {
    // Validate immediately
    McpValidation.requireNonNull(uri, "uri");
    
    // Now safe to use uri
    return transport.sendRequest(...);
}

// If uri is null:
// Throws: NullPointerException("Required argument 'uri' must not be null.")
// Clear error for debugging!
```

**Why Not Just Use Objects.requireNonNull?**
- It's from Java standard library
- But we add consistent naming convention
- Slightly more descriptive error messages
- One place to customize validation behavior

---

### 15. McpInitializationGuard

**File:** `src/main/java/mcp/toolkit/testing/framework/client/lifecycle/McpInitializationGuard.java`

**Purpose:** Ensures MCP initialization runs before executing operations (lazy initialization pattern).

**Why We Use It:**
- **Lazy Initialization:** Connection only created when needed
- **Idempotent:** Safe to "initialize" multiple times
- **Transparent:** Operations automatically init if needed
- **Thread Safe:** Guard handles synchronization

**How It Works:**

```
User calls: client.tools().listTools()
           ↓
MCpInitializationGuard.withInitialized(() -> ...)
           ↓
Check if initialized? No → call ensureInitialized()
           ↓
Initialize (connect, handshake)
           ↓
Set initialized = true
           ↓
Execute the operation
           ↓
Return result
```

**Methods:**

```java
<T> T withInitialized(Supplier<T> action)
  // Execute action after ensuring initialization
  // Returns: result of action

void withInitialized(Runnable action)
  // Execute void action after ensuring initialization
```

**Example Usage:**

```java
// Without guard (manual):
if (!client.isInitialized()) {
    client.initialize();
}
JsonNode result = client.tools().listTools();

// With guard (automatic):
JsonNode result = client.tools().listTools();
// Guard handles init automatically!
```

**Why This Pattern?**

```
Benefit: Lazy initialization
  Connection only created when needed
  Doesn't hang test setup waiting for server

Benefit: Idempotent
  Can call initialize() multiple times safely
  No double-init errors

Benefit: Transparent
  Users don't think about initialization
  Just use client, it works

Benefit: Thread-safe
  Guard handles all synchronization
  Multiple threads can call simultaneously
```

---

## Design Patterns

### 1. **Facade Pattern**

Used for: `McpToolDirectory`, `McpResourceDirectory`, `McpPromptDirectory`

**What it does:**
```
Complex subsystem (JSON-RPC, transport, codec)
           ↓
Facade (typed methods for domain)
           ↓
Simple interface for clients
```

**Why:**
- Hides complexity from users
- Provides domain-specific language
- Easy to use and understand

---

### 2. **Factory Pattern**

Used for: `BaseMcpComponentTestSetup`, `McpTestClientUtils.buildComponents()`

**What it does:**
```
All the steps to create and initialize
           ↓
Encapsulated in factory
           ↓
One-liner: get fully initialized object
```

**Why:**
- Reduces boilerplate
- Centralizes creation logic
- Easy to change creation strategy

---

### 3. **Strategy Pattern**

Used for: `McpTransport` interface with `McpSseTransport` implementation

**What it does:**
```
Different transport implementations
           ↓
All implement common interface
           ↓
Client doesn't care which one used
```

**Why:**
- Easy to swap implementations
- Extensible without modifying existing code
- Testable with mocks

---

### 4. **Builder Pattern**

Used for: `RpcExchange`

**What it does:**
```
Set fields one by one
           ↓
Build immutable object at end
           ↓
All-or-nothing semantics
```

**Why:**
- Create immutable objects safely
- Fill fields incrementally as exchange progresses
- Validation at build time

---

### 5. **Lazy Initialization Pattern**

Used for: `McpInitializationGuard`, `McpTestClient`

**What it does:**
```
Initialization deferred until needed
           ↓
Transparent to caller
           ↓
Safe idempotent re-init
```

**Why:**
- Don't initialize until necessary
- Faster test startup
- Automatic transparent behavior

---

### 6. **Template Method Pattern**

Used for: `McpSseTransport.connect()` flow

**What it does:**
```
Overall algorithm in super (interface)
           ↓
Specific steps in subclass
           ↓
Subclass fills in details
```

**Why:**
- Consistent flow for all transports
- Each transport implements details
- Easy to add new transports

---

## Why Each Component Exists

### Problem → Solution Mapping

| Problem | Solution | Component |
|---------|----------|-----------|
| Complexity of MCP protocol | High-level facade | `McpTestClient` |
| Boilerplate in tests | Factory methods | `BaseMcpComponentTestSetup` |
| Transport abstraction needed | Interface + implementation | `McpTransport` + `McpSseTransport` |
| HTTP/SSE communication | Async transport implementation | `McpSseTransport` |
| JSON-RPC marshaling | Low-level RPC client | `McpRpcClient` |
| Exchange inspection for tests | Immutable record | `RpcExchange` |
| Tracking all exchanges | Thread-safe tracker | `RpcExchangeTracker` |
| Tool operations safety | Type-safe facade | `McpToolDirectory` |
| Resource operations safety | Type-safe facade | `McpResourceDirectory` |
| Prompt operations safety | Type-safe facade | `McpPromptDirectory` |
| JSON serialization | Utility wrapper | `McpJsonCodec` |
| Magic strings in code | Constants | `McpTestClientConstants` |
| Scattered creation logic | Centralized utilities | `McpTestClientUtils` |
| Repeated null checks | Validation helpers | `McpValidation` |
| Need for lazy init | Transparent initializer | `McpInitializationGuard` |

---

## Complete Usage Example

```java
import mcp.toolkit.testing.framework.McpTestClient;
import mcp.toolkit.testing.framework.BaseMcpComponentTestSetup;
import mcp.toolkit.testing.framework.client.rpc.RpcExchange;
import com.fasterxml.jackson.databind.JsonNode;

@Test
void testMcpServerIntegration() {
    // 1. Create and initialize client
    McpTestClient client = BaseMcpComponentTestSetup
        .initializeMcpTestClient("http://localhost:8080");
    
    try {
        // 2. Discover tools
        JsonNode toolList = client.tools().listTools();
        assertTrue(toolList.has("tools"));
        
        // 3. Get specific tool
        JsonNode calcTool = client.tools().toolDefinition("calculator");
        assertEquals("calculator", calcTool.path("name").asText());
        
        // 4. Call tool
        JsonNode result = client.tools().callTool("calculator", 
            Map.of("operation", "add", "a", 5, "b", 3));
        assertEquals(8, result.path("result").asInt());
        
        // 5. Check resources
        JsonNode resources = client.resources().listResources();
        assertTrue(resources.has("resources"));
        
        // 6. Read resource
        JsonNode content = client.resources()
            .readResource("file:///data.json");
        assertNotNull(content.path("contents"));
        
        // 7. Get prompts
        JsonNode prompts = client.prompts().listPrompts();
        assertTrue(prompts.has("prompts"));
        
        // 8. Assert on exchanges
        RpcExchangeTracker tracker = client.exchangeTracker();
        assertEquals(6, tracker.size());
        
        // Verify all succeeded
        assertEquals(6, tracker.withStatus(RpcExchange.Status.SUCCESS).size());
        
        // Check performance
        tracker.all().forEach(exchange -> 
            assertTrue(exchange.latency().toMillis() < 5000)
        );
        
        // Inspect specific exchange
        RpcExchange lastExchange = tracker.last().get();
        assertEquals("prompts/list", lastExchange.method());
        
    } finally {
        // 9. Cleanup
        client.close();
    }
}
```

---

## Conclusion

Each class serves a specific purpose in the architecture:

- **Higher layers** provide user-friendly interfaces
- **Middle layers** handle domain-specific logic
- **Lower layers** handle protocol details
- **Utilities** provide shared functionality

This layered design provides:
- ✅ Clean, discoverable API
- ✅ Comprehensive testing capabilities
- ✅ Thread-safe operations
- ✅ Easy to extend
- ✅ Easy to mock for testing
- ✅ Production-ready error handling

The framework is complete and ready for testing MCP servers!


# MCP Testing Framework - Implementation Complete

## Overview

A comprehensive Java testing framework for interacting with MCP (Model Context Protocol) servers over HTTP/SSE (Server-Sent Events). The framework provides high-level typed facades for tool invocation, resource management, and prompt retrieval, along with complete JSON-RPC request/response tracking.

## Architecture

### Core Layers

```
┌─────────────────────────────────────┐
│       McpTestClient                 │  ← High-level entry point
│  (Tools, Resources, Prompts)        │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│   Domain Facades                    │
│  - McpToolDirectory                 │
│  - McpResourceDirectory             │
│  - McpPromptDirectory               │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│   McpRpcClient                      │  ← JSON-RPC client with tracking
│   RpcExchangeTracker                │
└─────────────────────────────────────┘
           ↓
┌─────────────────────────────────────┐
│   McpSseTransport                   │  ← HTTP/SSE transport
│   (McpTransport interface)          │
└─────────────────────────────────────┘
```

## Key Components

### 1. **McpTestClient** - Main Entry Point
- Located: `src/main/java/mcp/toolkit/testing/framework/McpTestClient.java`
- High-level client managing SSE connection, initialization, and JSON-RPC calls
- Features:
  - Lazy, idempotent initialization
  - Domain-specific accessors (tools, resources, prompts)
  - Generic RPC method calling
  - Exchange tracking for test assertions

**Usage:**
```java
McpTestClient client = new McpTestClient("http://localhost:8080");
client.initialize();
JsonNode tools = client.tools().listTools();
```

### 2. **BaseMcpComponentTestSetup** - Factory Helpers
- Located: `src/main/java/mcp/toolkit/testing/framework/BaseMcpComponentTestSetup.java`
- Simplifies test setup with pre-initialized clients
- Two overloaded factory methods for default and custom SSE endpoints

**Usage:**
```java
McpTestClient client = BaseMcpComponentTestSetup.initializeMcpTestClient("http://localhost:8080");
```

### 3. **Transport Layer**

#### McpTransport (Interface)
- Located: `src/main/java/mcp/toolkit/testing/framework/transport/McpTransport.java`
- Abstracts message transport mechanism
- Core operations:
  - `connect()` - Establish connection
  - `sendRequest(String payload, long requestId)` - Send RPC request
  - `sendNotification(String payload)` - Send notification
  - `close()` - Close connection

#### McpSseTransport (Implementation)
- Located: `src/main/java/mcp/toolkit/testing/framework/transport/McpSseTransport.java`
- Server-Sent Events (SSE) implementation
- Features:
  - Persistent SSE stream connection
  - Dynamic endpoint discovery via SSE events
  - Request/response matching by ID
  - Timeout handling
  - Thread-safe operation using ConcurrentHashMap

### 4. **JSON-RPC Client Layer**

#### McpRpcClient
- Located: `src/main/java/mcp/toolkit/testing/framework/client/rpc/McpRpcClient.java`
- Low-level JSON-RPC client
- Capabilities:
  - Request/response marshaling
  - Automatic ID sequencing
  - Error detection and handling
  - Complete exchange recording

#### RpcExchange
- Located: `src/main/java/mcp/toolkit/testing/framework/client/rpc/RpcExchange.java`
- Immutable record of single request/response exchange
- Tracks:
  - Request/response payloads
  - Timestamps and latency
  - Status (SUCCESS, ERROR, TIMEOUT, FAILED)
  - Error details

#### RpcExchangeTracker
- Located: `src/main/java/mcp/toolkit/testing/framework/client/rpc/RpcExchangeTracker.java`
- Thread-safe tracker recording all exchanges
- Query methods:
  - `all()` - All exchanges
  - `last()` - Most recent exchange
  - `forMethod(String)` - Exchanges for specific method
  - `byId(long)` - Exchange by request ID
  - `withStatus(Status)` - Exchanges with specific status

### 5. **Domain Facades**

#### McpToolDirectory
- Located: `src/main/java/mcp/toolkit/testing/framework/client/tools/McpToolDirectory.java`
- Tool discovery and invocation
- Methods:
  - `listTools()` / `listTools(Object params)` - List available tools
  - `allToolDefinitions()` - Get tool definition array
  - `toolDefinition(String name)` - Find specific tool
  - `callTool(String name, Object args)` - Invoke tool

#### McpResourceDirectory
- Located: `src/main/java/mcp/toolkit/testing/framework/client/resources/McpResourceDirectory.java`
- Resource management
- Methods:
  - `listResources()` / `listResources(Object params)` - List resources
  - `readResource(String uri)` - Read resource content

#### McpPromptDirectory
- Located: `src/main/java/mcp/toolkit/testing/framework/client/prompts/McpPromptDirectory.java`
- Prompt management
- Methods:
  - `listPrompts()` / `listPrompts(Object params)` - List prompts
  - `getPrompt(String name)` / `getPrompt(String name, Object args)` - Retrieve prompt

### 6. **Utility & Core Classes**

#### McpJsonCodec
- Located: `src/main/java/mcp/toolkit/testing/framework/core/codec/McpJsonCodec.java`
- JSON serialization/deserialization helpers
- Features:
  - Object-to-JSON conversion
  - JSON-to-object parsing
  - Fluent params building

#### McpTestClientConstants
- Located: `src/main/java/mcp/toolkit/testing/framework/core/constants/McpTestClientConstants.java`
- Centralized configuration constants
- Categories:
  - Defaults (timeout: 10s, protocol version: 2024-11-05)
  - Endpoints (/sse, /mcp/message)
  - SSE event types
  - HTTP headers
  - JSON-RPC methods
  - Notification names

#### McpTestClientUtils
- Located: `src/main/java/mcp/toolkit/testing/framework/core/util/McpTestClientUtils.java`
- Component building and configuration
- Key methods:
  - `buildComponents()` - Wire all client components
  - `buildInitializeParams()` - Create MCP handshake payload
  - `resolveEndpoints()` - Resolve URIs
  - `normalizeBaseUri()` / `normalizePath()` - URL normalization

#### McpValidation
- Located: `src/main/java/mcp/toolkit/testing/framework/core/util/McpValidation.java`
- Argument validation helpers
- Methods:
  - `requireNonNull()` - Null check
  - `requireNotBlank()` - Blank string check

#### McpInitializationGuard
- Located: `src/main/java/mcp/toolkit/testing/framework/client/lifecycle/McpInitializationGuard.java`
- Ensures initialization before operations
- Pattern: Automatically initializes client on first operation

## Project Structure

```
mcp-testing/
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── mcp/toolkit/testing/framework/
│       │       ├── McpTestClient.java
│       │       ├── BaseMcpComponentTestSetup.java
│       │       ├── client/
│       │       │   ├── lifecycle/
│       │       │   │   └── McpInitializationGuard.java
│       │       │   ├── rpc/
│       │       │   │   ├── McpRpcClient.java
│       │       │   │   ├── RpcExchange.java
│       │       │   │   └── RpcExchangeTracker.java
│       │       │   ├── tools/
│       │       │   │   └── McpToolDirectory.java
│       │       │   ├── resources/
│       │       │   │   └── McpResourceDirectory.java
│       │       │   └── prompts/
│       │       │       └── McpPromptDirectory.java
│       │       ├── core/
│       │       │   ├── codec/
│       │       │   │   └── McpJsonCodec.java
│       │       │   ├── constants/
│       │       │   │   └── McpTestClientConstants.java
│       │       │   └── util/
│       │       │       ├── McpTestClientUtils.java
│       │       │       └── McpValidation.java
│       │       └── transport/
│       │           ├── McpTransport.java
│       │           └── McpSseTransport.java
│       └── resources/
└── target/
```

## Dependencies

The project uses:
- **Jackson DataBind** (2.15.2) - JSON serialization/deserialization
- **Java 25** - Target and source compilation

## Features

### Complete MCP Protocol Support
- ✅ JSON-RPC 2.0 message format
- ✅ SSE (Server-Sent Events) transport
- ✅ Dynamic endpoint discovery
- ✅ Request/response matching
- ✅ Error handling and timeouts
- ✅ Protocol version negotiation

### Testing Capabilities
- ✅ Complete request/response tracking
- ✅ Latency measurement
- ✅ Exchange filtering and querying
- ✅ Status tracking (SUCCESS, ERROR, TIMEOUT, FAILED)
- ✅ Thread-safe concurrent access

### Developer Experience
- ✅ Type-safe domain facades
- ✅ Fluent API design
- ✅ Comprehensive Javadoc
- ✅ Lazy initialization
- ✅ Validation with clear error messages

## Build & Compile

```bash
# Build project
mvn clean compile

# Package
mvn clean package
```

## Next Steps

To use this framework in your tests:

1. Create an instance of `McpTestClient` with your server URL
2. Call `initialize()` or rely on lazy initialization
3. Access domain-specific operations via `tools()`, `resources()`, `prompts()`
4. Use `exchangeTracker()` for test assertions on RPC exchanges

Example:
```java
@Test
void testToolInvocation() {
    McpTestClient client = new McpTestClient("http://localhost:8080");
    
    JsonNode result = client.tools().callTool("my-tool", new MyArgs());
    
    // Assert on response
    assertTrue(result.has("output"));
    
    // Assert on exchange
    RpcExchange exchange = client.exchangeTracker().last().get();
    assertEquals(RpcExchange.Status.SUCCESS, exchange.status());
    assertTrue(exchange.latency().toMillis() < 5000);
}
```

---

**Framework Status**: ✅ Complete - All 15 core classes implemented and ready for testing MCP servers.


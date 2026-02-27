# MCP Testing Framework - Visual Summary & Decision Trees

## 📊 Framework at a Glance

```
15 Classes Organized in 5 Layers
├── Entry Point (2)
│   ├── McpTestClient
│   └── BaseMcpComponentTestSetup
├── Transport (2)
│   ├── McpTransport (interface)
│   └── McpSseTransport
├── RPC & Tracking (3)
│   ├── McpRpcClient
│   ├── RpcExchange
│   └── RpcExchangeTracker
├── Domain Facades (3)
│   ├── McpToolDirectory
│   ├── McpResourceDirectory
│   └── McpPromptDirectory
└── Utilities (5)
    ├── McpJsonCodec
    ├── McpTestClientConstants
    ├── McpTestClientUtils
    ├── McpValidation
    └── McpInitializationGuard
```

---

## 🎯 Decision Tree: Which Class to Use?

### Question 1: What Do I Want to Do?

```
                        ┌─────────────────────────────────────┐
                        │  What am I testing?                 │
                        └────────────────────┬────────────────┘
                                             │
                ┌────────────────────────────┼────────────────────────────┐
                │                            │                            │
                ▼                            ▼                            ▼
        ┌──────────────┐          ┌──────────────────┐         ┌─────────────────┐
        │ Tools        │          │ Resources        │         │ Prompts         │
        └──────┬───────┘          └────────┬─────────┘         └────────┬────────┘
               │                           │                            │
               ▼                           ▼                            ▼
        McpToolDirectory         McpResourceDirectory         McpPromptDirectory
        ├─ listTools()           ├─ listResources()           ├─ listPrompts()
        ├─ toolDefinition()      ├─ readResource()            ├─ getPrompt()
        ├─ callTool()            └─ Advanced: use             └─ Advanced: use
        └─ Advanced: use             client.call()                client.call()
            client.call()
```

### Question 2: Do I Need Lower-Level Access?

```
                    ┌──────────────────────────┐
                    │ Do I need to...          │
                    └──────────────────────────┘
                                  │
                ┌─────────────────┼─────────────────┐
                │                 │                 │
                ▼                 ▼                 ▼
        ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐
        │ Call custom  │  │ Inspect all  │  │ Access full     │
        │ methods?     │  │ exchanges?   │  │ JSON-RPC?       │
        └──────┬───────┘  └──────┬───────┘  └────────┬────────┘
               │                 │                   │
               ▼                 ▼                   ▼
        Use: client.call()  Use:              Use:
             with method      exchangeTracker()  McpRpcClient
             name and params  (get/filter)      (advanced)
```

### Question 3: How Do I Create the Client?

```
                    ┌──────────────────────────┐
                    │ Setup complexity?        │
                    └──────────────────────────┘
                                  │
                ┌─────────────────┴─────────────────┐
                │                                   │
                ▼                                   ▼
        ┌──────────────────────┐        ┌──────────────────────┐
        │ Simple (default)     │        │ Custom (endpoint)    │
        └──────┬───────────────┘        └──────┬───────────────┘
               │                               │
               ▼                               ▼
        BaseMcpComponentTestSetup.       new McpTestClient(
            initializeMcpTestClient(         baseUrl,
                "http://localhost:8080")     sseEndpointPath)
```

---

## 🔄 Request Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  Your Test Code                                             │
│  client.tools().callTool("add", {a: 2, b: 3})              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  McpTestClient                                              │
│  └─ Checks initialization                                   │
│  └─ Delegates to McpToolDirectory                          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  McpToolDirectory                                           │
│  └─ Validates tool name                                    │
│  └─ Builds params: {"name": "add", "arguments": {...}}    │
│  └─ Calls McpRpcClient.callAndRequireResult()             │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  McpRpcClient                                               │
│  ├─ Generate ID: 1                                         │
│  ├─ Build JSON-RPC request                                │
│  ├─ Call McpJsonCodec.toJson()                            │
│  ├─ Create RpcExchange.Builder()                          │
│  └─ Call transport.sendRequest()                          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  McpSseTransport                                            │
│  ├─ POST request to /mcp/message                          │
│  ├─ Store pending request in map                          │
│  ├─ Wait for response with id=1                           │
│  ├─ Receive SSE event: message                            │
│  ├─ Parse JSON, extract id                                │
│  └─ Complete CompletableFuture with response             │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  McpRpcClient (continued)                                   │
│  ├─ Validate response has "result"                        │
│  ├─ Create RpcExchange from response                      │
│  ├─ Record in RpcExchangeTracker                          │
│  └─ Return result                                         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Back to Your Test Code                                     │
│  result = {status: "success", answer: 5}                  │
│  ✓ Test can now assert on result                          │
│  ✓ Can inspect exchange tracker                           │
└─────────────────────────────────────────────────────────────┘
```

---

## 🏗️ Component Dependency Graph

```
                    ┌─────────────────────┐
                    │  Your Test Code     │
                    └──────────┬──────────┘
                               │
                    ┌──────────▼──────────┐
                    │  McpTestClient      │
                    │  - orchestrator     │
                    └─┬────────────────┬──┘
                     │                │
        ┌────────────▼─┐  ┌──────────▼────────────┐
        │ McpTool      │  │ McpInitialization    │
        │ Directory    │  │ Guard                │
        │ McpResource  │  │                      │
        │ Directory    │  │ (lazy init)          │
        │ McpPrompt    │  │                      │
        │ Directory    │  │                      │
        └────────┬─────┘  └──────────┬───────────┘
                 │                   │
                 └────────┬──────────┘
                          │
                 ┌────────▼──────────┐
                 │  McpRpcClient     │
                 │  - JSON-RPC       │
                 └─┬────────────────┬┘
                  │                │
         ┌────────▼──┐  ┌─────────▼────────┐
         │ McpJson   │  │ RpcExchange      │
         │ Codec     │  │ Tracker          │
         └─────┬─────┘  │                  │
               │        │ (thread-safe)    │
               │        │ (CopyOnWrite)    │
               │        └──────────────────┘
               │
        ┌──────┴───────────────────┐
        │                          │
    ┌───▼─────────┐      ┌────────▼───────┐
    │ McpTransport│      │ McpValidation  │
    │ (interface) │      │ McpConstants   │
    └───┬─────────┘      └────────────────┘
        │
        │ implements
        │
    ┌───▼──────────────┐
    │ McpSseTransport  │
    │ - HTTP/SSE       │
    │ - Async requests │
    │ - SSE parsing    │
    └──────────────────┘
```

---

## 🧵 Thread Safety Architecture

```
                    ┌──────────────────────────────┐
                    │  Multiple Test Threads       │
                    │  thread-1, thread-2, ...     │
                    └──────────────────────────────┘
                              │
                              ▼ (all can call simultaneously)
                    ┌──────────────────────────────┐
                    │  McpTestClient               │
                    │  volatile: initialized,      │
                    │  initializeResult            │
                    │  Synchronized: initLock      │
                    └──────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────────────────┐
                    │  McpSseTransport             │
                    │  ConcurrentHashMap:          │
                    │    pendingRequests           │
                    │  volatile: connected, closed │
                    │  Synchronized: connectLock   │
                    └──────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────────────────┐
                    │  McpRpcClient                │
                    │  AtomicLong: idSequence      │
                    │  (thread-safe ID gen)        │
                    └──────────────────────────────┘
                              │
                              ▼
                    ┌──────────────────────────────┐
                    │  RpcExchangeTracker          │
                    │  CopyOnWriteArrayList:       │
                    │    exchanges                 │
                    │  (lock-free reads)           │
                    │  (fast queries)              │
                    └──────────────────────────────┘

Key Techniques:
• volatile fields (visibility)
• synchronized blocks (atomic operations)
• ConcurrentHashMap (thread-safe map)
• AtomicLong (lock-free counter)
• CopyOnWriteArrayList (read-heavy list)
```

---

## 💾 Data Flow: Request to Response

```
REQUEST BUILD PHASE
────────────────────
User provides:     Map.of("a", 2, "b", 3)
                            │
                            ▼
                   McpJsonCodec.toArgumentsNode()
                            │
                            ▼
                   JsonNode: {"a": 2, "b": 3}
                            │
                            ▼
                   McpRpcClient builds:
                   {
                     "jsonrpc": "2.0",
                     "id": 1,
                     "method": "tools/call",
                     "params": {
                       "name": "add",
                       "arguments": {"a": 2, "b": 3}
                     }
                   }
                            │
                            ▼
                   McpJsonCodec.toJson()
                            │
                            ▼
                   String: {"jsonrpc": "2.0", ...}
                            │
                            ▼
                   HTTP POST to server


RESPONSE HANDLING PHASE
──────────────────────
Server receives & processes
                            │
                            ▼
                   Returns SSE event:
                   {
                     "jsonrpc": "2.0",
                     "id": 1,
                     "result": {"answer": 5}
                   }
                            │
                            ▼
                   McpJsonCodec.parseJson()
                            │
                            ▼
                   JsonNode response
                            │
                            ▼
                   Extract id: 1
                   Lookup pending request in map
                            │
                            ▼
                   Validate: has "result" field
                            │
                            ▼
                   Create RpcExchange:
                   • id: 1
                   • method: "tools/call"
                   • sentAt: timestamp
                   • receivedAt: timestamp
                   • latency: calculated
                   • status: SUCCESS
                            │
                            ▼
                   Record in RpcExchangeTracker
                            │
                            ▼
                   Return result to user:
                   {"answer": 5}
```

---

## 🔧 Configuration Constants Map

```
McpTestClientConstants
├── Defaults
│   ├── TIMEOUT: 10 seconds
│   └── PROTOCOL_VERSION: "2024-11-05"
├── Endpoints
│   ├── SSE: "/sse"
│   └── MESSAGE: "/mcp/message"
├── SseEvents
│   ├── ENDPOINT: "endpoint"
│   └── MESSAGE: "message"
├── Headers
│   └── MCP_PROTOCOL_VERSION: "MCP-Protocol-Version"
├── Methods
│   ├── INITIALIZE: "initialize"
│   ├── TOOLS_LIST: "tools/list"
│   ├── TOOLS_CALL: "tools/call"
│   ├── RESOURCES_LIST: "resources/list"
│   ├── RESOURCES_READ: "resources/read"
│   ├── PROMPTS_LIST: "prompts/list"
│   └── PROMPTS_GET: "prompts/get"
└── Notifications
    └── INITIALIZED: "notifications/initialized"

Why Constants?
✓ Single source of truth
✓ No magic strings
✓ Easy to update
✓ IDE autocomplete
✓ Type safety
```

---

## 📈 Class Complexity Pyramid

```
                          ▲
                         ╱ ╲
                        ╱   ╲          Simple, High-Level APIs
                       ╱     ╲         (End User Facing)
                      ╱       ╲
                     ╱─────────╲
                    ╱ McpTest  ╲        Domain Facades
                   ╱   Client   ╲
                  ╱_____________╲
                 ╱               ╲
                ╱ McpRpcClient    ╲     Protocol & Tracking
               ╱ RpcExchange      ╲
              ╱___________________╲
             ╱                     ╲
            ╱ McpSseTransport      ╲   Network Communication
           ╱ McpTransport          ╲
          ╱_________________________╲
         ╱                           ╲
        ╱ Utilities & Constants       ╲ Support
       ╱_____________________________╲
      ▼                               ▼

Each layer:
• Builds on layer below
• Hides complexity
• Provides cleaner interface
• Adds domain knowledge
```

---

## 🎨 Design Pattern Quick Reference

```
┌─────────────────────────────────────────────────────────────┐
│ Pattern: Facade                                             │
├─────────────────────────────────────────────────────────────┤
│ Classes: McpToolDirectory, McpResourceDirectory, ...        │
│ Purpose: Hide complexity, provide domain language          │
│ Example: client.tools().callTool(...) ✓                   │
│          vs client.call("tools/call", ...) ✗              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Pattern: Factory                                            │
├─────────────────────────────────────────────────────────────┤
│ Classes: BaseMcpComponentTestSetup, buildComponents()      │
│ Purpose: Encapsulate object creation                       │
│ Example: initializeMcpTestClient(...) ✓                   │
│          vs manually wiring all components ✗              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Pattern: Strategy                                           │
├─────────────────────────────────────────────────────────────┤
│ Classes: McpTransport (interface), McpSseTransport         │
│ Purpose: Swap implementations without changing code        │
│ Example: Implement WebSocketTransport later ✓             │
│          without modifying existing code ✓                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Pattern: Builder                                            │
├─────────────────────────────────────────────────────────────┤
│ Classes: RpcExchange.Builder                               │
│ Purpose: Construct immutable objects incrementally         │
│ Example: Fill fields as exchange progresses               │
│          Build immutable object at end                     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Pattern: Lazy Initialization                               │
├─────────────────────────────────────────────────────────────┤
│ Classes: McpInitializationGuard, McpTestClient            │
│ Purpose: Defer creation until needed                       │
│ Example: client.tools().listTools()                       │
│          Initializes automatically if needed               │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 Which Class Handles What?

```
INITIALIZATION
├─ McpTestClient
├─ McpInitializationGuard
└─ BaseMcpComponentTestSetup

REQUEST BUILDING
├─ McpRpcClient
├─ McpJsonCodec
└─ McpToolDirectory/ResourceDirectory/PromptDirectory

NETWORK COMMUNICATION
├─ McpSseTransport
├─ McpTransport
└─ (HttpClient from Java)

RESPONSE PROCESSING
├─ McpSseTransport (parsing SSE)
├─ McpRpcClient (validating JSON-RPC)
└─ McpJsonCodec (JSON conversion)

TRACKING & INSPECTION
├─ RpcExchange
├─ RpcExchangeTracker
└─ McpTestClient.exchangeTracker()

VALIDATION
├─ McpValidation
├─ McpRpcClient
└─ McpToolDirectory

CONFIGURATION
├─ McpTestClientConstants
└─ McpTestClientUtils
```

---

## 🚀 Performance Characteristics

```
Class                        Operation              Complexity  Thread-Safe
──────────────────────────   ──────────────────────  ──────────  ────────────
McpSseTransport              connect()               O(1)        Yes (lock)
                             sendRequest()          O(1)        Yes (map)
McpRpcClient                 callAndRequireResult() O(1)        Yes (atomic)
RpcExchangeTracker           record()               O(1)        Yes (list)
                             all()                  O(n)        Yes (copy)
                             forMethod()            O(n)        Yes (filter)
                             withStatus()           O(n)        Yes (filter)
McpJsonCodec                 toJsonNode()           O(n)        Yes
                             parseJson()            O(n)        Yes
                             toJson()               O(n)        Yes
McpToolDirectory             callTool()             O(1)        Yes
McpResourceDirectory         readResource()         O(1)        Yes
McpPromptDirectory           getPrompt()            O(1)        Yes
```

---

## ✅ Testing Checklist

```
Before Writing Tests:
□ Framework installed & dependencies added
□ MCP server running and accessible
□ Correct endpoint URL
□ Understand server capabilities

Creating Test:
□ Create client via factory or direct
□ Initialize (explicit or lazy)
□ Call domain methods
□ Assert on results
□ Inspect exchangeTracker if needed
□ Close client

Performance Testing:
□ Measure latencies via exchangeTracker
□ Check for timeout errors
□ Monitor concurrent access
□ Verify thread safety

Debugging:
□ Check error status in exchange
□ Inspect errorDetail field
□ List available tools/resources
□ Verify server is responding
□ Check network connectivity
```

---

## 📚 Documentation Map

```
DETAILED_CLASS_DOCUMENTATION.md
├─ Architecture Overview
├─ McpTestClient (100+ lines)
├─ BaseMcpComponentTestSetup (50+ lines)
├─ McpTransport (50+ lines)
├─ McpSseTransport (150+ lines)
├─ McpRpcClient (100+ lines)
├─ RpcExchange (100+ lines)
├─ RpcExchangeTracker (100+ lines)
├─ McpToolDirectory (50+ lines)
├─ McpResourceDirectory (50+ lines)
├─ McpPromptDirectory (50+ lines)
├─ McpJsonCodec (50+ lines)
├─ McpTestClientConstants (50+ lines)
├─ McpTestClientUtils (100+ lines)
├─ McpValidation (30+ lines)
├─ McpInitializationGuard (50+ lines)
├─ Design Patterns (100+ lines)
└─ Complete Usage Example

QUICK_REFERENCE_AND_EXAMPLES.md
├─ Quick Start
├─ 9 Real-World Scenarios
├─ Class Selection Guide
├─ Why Each Decision
├─ Performance Tips
└─ Troubleshooting

MCP_FRAMEWORK_GUIDE.md
├─ Overview
├─ Architecture Diagrams
├─ Component Descriptions
├─ Feature List
└─ Build Instructions

DOCUMENTATION_INDEX.md (This File)
└─ Navigation & Cross-References
```

---

## 🎓 Learning Path Visualization

```
START HERE
     │
     ├─→ MCP_FRAMEWORK_GUIDE.md (15 min read)
     │   ├─→ QUICK_REFERENCE_AND_EXAMPLES.md (30 min read)
     │   │   ├─→ Try Scenario 1
     │   │   ├─→ Try Scenario 2
     │   │   └─→ Try Full Integration Test
     │   │       │
     │   │       └─→ Performance Tips (10 min read)
     │   │           └─→ Optimize Your Tests
     │   │
     │   └─→ DETAILED_CLASS_DOCUMENTATION.md (Advanced - Reference)
     │       └─→ Deep Dive: Design Patterns
     │           └─→ Extend Framework
     │
     └─→ Start Writing Tests!
```

---

Great! You now have comprehensive documentation! 📚✅


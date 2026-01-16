# gRPC vs HTTP Long Polling for High-Frequency Server-Side Streaming

## Experiment Overview
This project compares two approaches for server-side streaming in a **high message rate scenario with little to no batching**:

- **gRPC server-streaming**: Using `StreamProducts` with continuous `ProductResponse` messages via `responseObserver.onNext(response)`.
- **HTTP long polling**: Streaming JSON via `application/octet-stream` with repeated writes to the response body.

The goal was to determine which approach performs better for real-time data streaming when messages are sent individually at high frequency.

## Verdict
**HTTP long polling is both simpler and faster in this configuration.**

Anyone can run the included k6 scripts to verify these findings on their own machine.

## Key Findings

### Why HTTP Long Polling Performed Better

**1. Server-side efficiency:**
- **gRPC**: Serializes each message to protobuf wire format on every `onNext()` call, even when reusing the same object.
- **HTTP**: Serializes the JSON record once and repeatedly writes the same prebuilt byte array.

**2. Client-side efficiency:**
- **gRPC**: k6's gRPC layer decodes each message individually with per-message JS callback overhead.
- **HTTP**: k6 handles the response as a bulk buffer with minimal per-record processing.

**3. Implementation simplicity:**
- **HTTP**: Standard HTTP/1.1 streaming with straightforward implementation.
- **gRPC**: Requires protobuf definitions, code generation, and additional setup complexity.

### When This Matters
This difference is significant when:
- Messages are sent individually (no batching)
- Message rate is high (thousands per second)
- Each message is small and consistent

## Running the Tests
Test scripts are provided in the `k6-tests/` directory. Run them to see the performance characteristics on your own machine:

```bash
# HTTP long polling test
k6 run k6-tests/product-http-polling.js

# gRPC streaming test
k6 run k6-tests/product-stream-load.js
```

**Note:** Specific throughput numbers are not included in this README because results vary by machine configuration. The important finding is the relative performance difference and the architectural trade-offs, which you can verify by running the tests yourself.

# Why gRPC Streaming Measured Slower Than HTTP “Pooling” in This k6 Benchmark

## Context
Two k6 tests were compared:

- **gRPC server-streaming**: `StreamProducts` sends a `ProductResponse` continuously via `responseObserver.onNext(response)`.
- **HTTP long-poll streaming** (`application/octet-stream`): server writes a precomputed `byte[]` (`recordBytes`) repeatedly into the response body.

Observed result: **~10 MB/s (gRPC)** vs **~60 MB/s (HTTP)**.
## Key finding: the tests exercise very different client/server work per “record”

### 1) Server-side work per record
**gRPC (Java):**
- Even though the `ProductResponse` object is built once and reused, **gRPC still serializes the message to the protobuf wire format for every `onNext()` call**.
- So the per-record loop is effectively: *encode protobuf → frame on HTTP/2 → write*.

**HTTP (Java):**
- The JSON record is serialized once:
  - `jsonRecord = objectMapper.writeValueAsString(template)`
  - `recordBytes = (jsonRecord + "\n").getBytes(...)`
- The loop is effectively: *write the same prebuilt bytes → flush*.

**Consequence:** the HTTP server avoids per-record serialization cost inside the hot loop; gRPC does not.

### 2) Client-side work per record (k6)
**gRPC (k6 JS):**
- k6’s gRPC layer delivers each message as a JS object, which implies **per-message decode/mapping overhead** on the client.

**HTTP (k6 JS):**
- k6 receives a response body blob and the script:
  - counts bytes once (`response.body.length`)
  - splits lines once per request (`split('\n')`)
- There is **no per-record callback** and **no per-record decoding** in the hot path.

**Consequence:** gRPC is dominated by per-message decode + per-message JS callback overhead, while HTTP is dominated by bulk buffer handling.

## Summary: why gRPC was slower here
gRPC is not “slower on the wire” in general; it was slower in *this benchmark configuration* because it performed **more work per record** on both sides:

- **Server:** protobuf encoding for every streamed message vs HTTP writing the same prebuilt bytes repeatedly.
- **Client:** per-message decode/mapping + per-message JS callback (plus optional stringify) vs HTTP bulk body handling.

## Practical mitigations (if you want higher gRPC throughput in k6)
- **Batch records**: send `repeated ProductResponse` in one streamed message (e.g., 100–1000 records per `onNext`).
- **Remove per-message stringify**: compute bytes-per-record once when the message is constant.
- **Measure raw transport separately**: use a native gRPC benchmark client (Go/C++/Java) if the goal is transport throughput rather than JS-level processing throughput.

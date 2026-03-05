# Conclusion: gRPC vs SSE vs HTTP Polling

## Benchmark Results Summary

| Method       | Records/sec | Notes               |
|--------------|-------------|---------------------|
| gRPC Stream  | 75,667      | 10 errors (benign, see below) |
| SSE          | 61,804      | No errors           |
| HTTP Polling | 6,781       | No errors           |

## Key Findings

- **gRPC and SSE are nearly equivalent** for server-push throughput (~22% difference), both vastly outperforming HTTP polling (~10x faster).
- HTTP polling is significantly less efficient due to the overhead of repeated request/response cycles.

- The 10 gRPC errors are benign: they occur at the end of the test when the k6 VUs close their connections. The server is still mid-stream and attempts one final write, which fails because the client side is already gone. This gets counted as an error by the test harness but does not reflect any instability in the streaming pipeline itself.

## Architecture Decision

- **Backend services → gRPC**: Lower overhead, strong typing via Protobuf, and native support for bidirectional streaming — making it the right choice for service-to-service communication.
- **Browsers → SSE**: Browser-native, no special library needed, and performs at a comparable level to gRPC for server-to-client streaming use cases.

## Future Consideration

gRPC's bidirectional streaming capability leaves the door open for richer real-time interactions between backend services without requiring a protocol change down the line.

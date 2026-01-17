=== Product gRPC Streaming Throughput Test ===
Method: gRPC Streaming
Domain: Product
Test Duration: 75.3s

Total Records Received: 5694639
Record Rate: 75667.98 records/sec
Errors: 10

running (1m15.3s), 00/10 VUs, 10 complete and 0 interrupted iterations
long_streams ✓ [======================================] 10 VUs  50s  10/10 iters, 1 per VU

=== Product SSE Throughput Test ===
Method: Server-Sent Events (SSE)
Domain: Product
Test Duration: 62.9s

Total Records Received: 3886817
Record Rate: 61804.10 records/sec
Errors: 0

running (1m02.9s), 00/10 VUs, 10 complete and 0 interrupted iterations
default ✓ [======================================] 10 VUs  50s
[orion@archlinux k6-tests]$ 


=== Product HTTP Polling Throughput Test ===
Method: HTTP Long Polling
Domain: Product
Test Duration: 50.0s

Total Records Received: 339096
Record Rate: 6781.64 records/sec
Errors: 0

running (0m50.0s), 00/10 VUs, 339096 complete and 0 interrupted iterations
default ✓ [======================================] 10 VUs  50s

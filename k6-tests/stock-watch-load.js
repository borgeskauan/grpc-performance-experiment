import { Client, Stream } from 'k6/net/grpc';
import { Counter, Rate } from 'k6/metrics';

// Custom metrics - standardized across all tests
const goodputBytesTotal = new Counter('goodput_bytes_total');
const recordsTotal = new Counter('records_total');
const errorRate = new Rate('error_rate');

// Shared test configuration: 10s warmup + 30s MEASURE + 10s cooldown
export const options = {
  stages: [
    { duration: '10s', target: 10 },  // Warmup
    { duration: '30s', target: 10 },  // MEASURE window
    { duration: '10s', target: 0 },   // Cooldown
  ],
  thresholds: {
    'error_rate': ['rate<0.01'],
  },
};

const client = new Client();
client.load(['../product-service/src/main/proto'], 'product.proto');

export default function () {
  if (__ITER == 0) {
    client.connect('localhost:9090', { plaintext: true });
  }

  const tags = { method: 'streaming', domain: 'stock' };
  const testStart = Date.now();
  const measureWindowEnd = testStart + 50000; // Stream for ~50s (entire test)
  
  const stream = new Stream(client, 'product.ProductService/WatchStock');

  stream.on('data', function (update) {
    // Measure goodput as UTF-8 bytes of JSON representation
    const jsonStr = JSON.stringify(update);
    const bytes = new TextEncoder().encode(jsonStr).length;
    
    goodputBytesTotal.add(bytes, tags);
    recordsTotal.add(1, tags);
  });

  stream.on('end', function () {
    // Stream ended normally
  });

  stream.on('error', function (err) {
    console.error('Stream error:', err.message || err);
    errorRate.add(1, tags);
  });

  // Start streaming
  stream.write({});
  
  // Read continuously until MEASURE window ends
  const streamDuration = 30000; // 30 seconds for main MEASURE phase
  const checkInterval = 100;
  let elapsed = 0;
  
  while (elapsed < streamDuration) {
    // Keep VU alive and reading
    sleep(checkInterval / 1000);
    elapsed += checkInterval;
  }
  
  // Close stream gracefully
  stream.end();
}

export function handleSummary(data) {
  // Extract metrics for MEASURE window only (10s - 40s)
  const measureStart = 10;
  const measureEnd = 40;
  const measureDuration = measureEnd - measureStart; // 30 seconds
  
  let summary = '\n=== Stock gRPC Streaming Throughput Test ===\n';
  summary += 'Method: gRPC Streaming\n';
  summary += 'Domain: Stock\n';
  summary += `MEASURE Window: ${measureStart}s - ${measureEnd}s (${measureDuration}s)\n\n`;
  
  // Calculate totals
  const totalBytes = data.metrics.goodput_bytes_total?.values?.count || 0;
  const totalRecords = data.metrics.records_total?.values?.count || 0;
  const errorRateValue = data.metrics.error_rate?.values?.rate || 0;
  
  // Calculate rates (approximate for MEASURE window)
  const bytesPerSec = totalBytes / measureDuration;
  const recordsPerSec = totalRecords / measureDuration;
  
  summary += `Total Bytes Transferred: ${totalBytes.toLocaleString()} bytes\n`;
  summary += `Total Records Received: ${totalRecords.toLocaleString()}\n`;
  summary += `Throughput: ${(bytesPerSec / 1024 / 1024).toFixed(2)} MB/s\n`;
  summary += `Record Rate: ${recordsPerSec.toFixed(2)} records/sec\n`;
  summary += `Error Rate: ${(errorRateValue * 100).toFixed(2)}%\n`;
  
  console.log(summary);
  
  return {
    'stdout': summary,
  };
}


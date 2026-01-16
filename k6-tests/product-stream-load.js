import { Client, Stream } from 'k6/net/grpc';
import { sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// Custom metrics - standardized across all tests
const goodputBytesTotal = new Counter('goodput_bytes_total');
const recordsTotal = new Counter('records_total');
const errorRate = new Rate('error_rate');

export const options = {
  vus: 10,
  duration: '50s',
  thresholds: {
    'error_rate': ['rate<0.01'],
  },
};

const client = new Client();
client.load(['../product-service/src/main/proto'], 'product.proto');

// Since server sends same message repeatedly, compute bytes per record once globally
let bytesPerRecord = 0;

export default function () {
  if (__ITER == 0) {
    client.connect('localhost:9090', { plaintext: true });
  }

  const tags = { method: 'streaming', domain: 'product' };
  
  const stream = new Stream(client, 'product.ProductService/StreamProducts');

  stream.on('data', function (product) {
    if (bytesPerRecord === 0) {
      bytesPerRecord = JSON.stringify(product).length;
    }
    
    goodputBytesTotal.add(bytesPerRecord, tags);
    recordsTotal.add(1, tags);
  });

  stream.on('end', function () {
    // Stream ended normally
  });

  stream.on('error', function (err) {
    // Only log unexpected errors (not normal cancellation/completion)
    const errMsg = err.message || err.toString();
    if (!errMsg.includes('canceled') && !errMsg.includes('deadline')) {
      console.error('Stream error:', errMsg);
      errorRate.add(1, tags);
    }
  });

  // Start streaming
  stream.write({});
  
  // Keep stream open for test duration
  sleep(50);
  
  // End stream gracefully when iteration completes
  stream.end();
}

export function handleSummary(data) {
  const testDuration = data.state.testRunDurationMs / 1000;
  
  let summary = '\n=== Product gRPC Streaming Throughput Test ===\n';
  summary += 'Method: gRPC Streaming\n';
  summary += 'Domain: Product\n';
  summary += `Test Duration: ${testDuration.toFixed(1)}s\n\n`;
  
  // Calculate totals
  const totalBytes = data.metrics.goodput_bytes_total?.values?.count || 0;
  const totalRecords = data.metrics.records_total?.values?.count || 0;
  const errorRateValue = data.metrics.error_rate?.values?.rate || 0;
  
  // Calculate rates
  const bytesPerSec = totalBytes / testDuration;
  const recordsPerSec = totalRecords / testDuration;
  
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


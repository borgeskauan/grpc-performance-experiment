import { Client, Stream } from 'k6/net/grpc';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

// Custom metrics - standardized across all tests
const recordsTotal = new Counter('records_total');
const errorCount = new Counter('error_count');

export const options = {
  scenarios: {
    long_streams: {
      executor: "per-vu-iterations",
      vus: 10,
      iterations: 1,
      maxDuration: "60s",
    },
  },
};

const client = new Client();
client.load(['../product-service/src/main/proto'], 'product.proto');

export default function () {
  client.connect('localhost:9090', { plaintext: true });

  const tags = { method: 'streaming', domain: 'product' };
  
  const stream = new Stream(client, 'product.ProductService/StreamProducts');

  stream.on('data', function (product) {
    recordsTotal.add(1, tags);
  });

  stream.on('end', function () {
    // Stream ended normally
  });

  stream.on('error', function (err) {
    const errMsg = err.message || err.toString();

    console.error('Stream error:', errMsg);
    errorCount.add(1, tags);
  });

  // Start streaming
  stream.write({});
  
  // Keep stream open for test duration
  sleep(50);
  
  // Close stream gracefully
  stream.end();
  client.close();
}

export function handleSummary(data) {
  const testDuration = data.state.testRunDurationMs / 1000;
  
  let summary = '\n=== Product gRPC Streaming Throughput Test ===\n';
  summary += 'Method: gRPC Streaming\n';
  summary += 'Domain: Product\n';
  summary += `Test Duration: ${testDuration.toFixed(1)}s\n\n`;
  
  // Calculate totals
  const totalRecords = data.metrics.records_total?.values?.count || 0;
  const errorCount = data.metrics.error_count?.values?.count || 0;
  
  // Calculate rates
  const recordsPerSec = totalRecords / testDuration;
  
  summary += `Total Records Received: ${totalRecords.toLocaleString()}\n`;
  summary += `Record Rate: ${recordsPerSec.toFixed(2)} records/sec\n`;
  summary += `Errors: ${errorCount.toLocaleString()}\n`;
  
  console.log(summary);
  
  return {
    'stdout': summary,
  };
}


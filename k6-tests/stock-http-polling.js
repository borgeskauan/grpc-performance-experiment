import http from 'k6/http';
import { check } from 'k6';
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

const BASE_URL = 'http://localhost:8080';
const BATCH_SIZE = 100; // Request 100 records per poll

export default function () {
  const tags = { method: 'polling', domain: 'stock' };
  
  // Greedy polling - no sleep, request immediately
  const response = http.get(`${BASE_URL}/api/stock?batchSize=${BATCH_SIZE}`, { tags });
  
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
  }, tags);
  
  errorRate.add(!success, tags);
  
  if (success && response.body) {
    // Measure goodput as UTF-8 bytes
    const bodyBytes = new TextEncoder().encode(response.body).length;
    goodputBytesTotal.add(bodyBytes, tags);
    
    // Count records
    try {
      const records = JSON.parse(response.body);
      if (Array.isArray(records)) {
        recordsTotal.add(records.length, tags);
      }
    } catch (e) {
      errorRate.add(1, tags);
    }
  }
}

export function handleSummary(data) {
  // Extract metrics for MEASURE window only (10s - 40s)
  const measureStart = 10;
  const measureEnd = 40;
  const measureDuration = measureEnd - measureStart; // 30 seconds
  
  let summary = '\n=== Stock HTTP Polling Throughput Test ===\n';
  summary += 'Method: HTTP Polling (Greedy)\n';
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


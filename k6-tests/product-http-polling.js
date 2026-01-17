import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// Custom metrics - standardized across all tests
const recordsTotal = new Counter('records_total');
const errorCount = new Counter('error_count');

export const options = {
  vus: 10,
  duration: '50s'
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const tags = { method: 'polling', domain: 'product' };
  
  // Long polling - make request, server streams until timeout, then reconnect
  const response = http.get(`${BASE_URL}/api/products`, { 
    tags,
    timeout: '15s' // Slightly longer than server timeout
  });
  
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
  }, tags);
  
  if (!success) {
    errorCount.add(1, tags);
  }
  
  if (success && response.body) {
    // Process each record individually
    const lines = response.body.trim().split('\n');
    
    for (const line of lines) {
      if (line.length > 0) {
        // Validate record can be serialized (parse JSON)
        const record = JSON.parse(line);
        
        // Increment record counter
        recordsTotal.add(1, tags);
      }
    }
  }
  
  // Immediately reconnect (greedy long polling)
}

export function handleSummary(data) {
  const testDuration = data.state.testRunDurationMs / 1000;
  
  let summary = '\n=== Product HTTP Polling Throughput Test ===\n';
  summary += 'Method: HTTP Long Polling\n';
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


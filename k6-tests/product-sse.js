import sse from "k6/x/sse";
import { check } from "k6";
import { Counter } from "k6/metrics";

const recordsTotal = new Counter("records_total");
const errorCount = new Counter("error_count");

export const options = {
  vus: 10,
  duration: "50s",
};

export default function () {
  const tags = { method: "sse", domain: "product" };

  const res = sse.open("http://localhost:8080/api/products/events", {
    method: "GET",
    headers: { Accept: "text/event-stream" },
    tags,
    http2: true,
  }, (client) => {
    client.on("event", (evt) => {
      if (!evt?.data) return;
      try {
        JSON.parse(evt.data);       // keep parsing
        recordsTotal.add(1, tags);
      } catch {
        errorCount.add(1, tags);
      }
    });

    client.on("error", (e) => {
      errorCount.add(1, tags);
    });

    // Important: no sleep here; just register handlers and return.
    // Close only if you want to stop early, e.g. after N events.
    // client.close();
  });

  check(res, { "status is 200": (r) => r && r.status === 200 }, tags);
}

export function handleSummary(data) {
  const testDuration = data.state.testRunDurationMs / 1000;

  let summary = "\n=== Product SSE Throughput Test ===\n";
  summary += "Method: Server-Sent Events (SSE)\n";
  summary += "Domain: Product\n";
  summary += `Test Duration: ${testDuration.toFixed(1)}s\n\n`;

  // Calculate totals
  const totalRecords = data.metrics.records_total?.values?.count || 0;
  const errorCount = data.metrics.error_count?.values?.count || 0;

  // Calculate rates
  const recordsPerSec = totalRecords / testDuration;

  summary += `Total Records Received: ${totalRecords.toLocaleString()}\n`;
  summary += `Record Rate: ${recordsPerSec.toFixed(2)} records/sec\n`;
  summary += `Errors: ${errorCount.toLocaleString()}\n`;

  return {
    stdout: summary,
  };
}

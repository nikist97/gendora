import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

const successfulRequests = new Counter("successful_requests");
const failedRequests = new Counter("failed_requests");

const localIds = new Set();

export const options = {
  stages: [
    { duration: "15s", target: 100 },
    { duration: "300s", target: 500 },
    { duration: "15s", target: 0 },
  ],
  thresholds: {
    http_req_duration: ["p(99)<20"], // 99% of requests should be below 20ms
    http_req_failed: ["rate<0.001"], // Error rate should be less than 0.1%
    checks: ["rate>0.999"], // 99.9% of checks should pass
  },
};

export default function () {
  const url = "http://localhost/api/generator/ids";

  const response = http.post(url, null, {
    headers: { Accept: "application/json" },
  });

  const statusCheck = check(response, {
    "status is 200": (r) => r.status === 200,
    "response is JSON": (r) =>
      r.headers["Content-Type"] &&
      r.headers["Content-Type"].includes("application/json"),
  });

  if (statusCheck) {
    successfulRequests.add(1);
    const body = response.json();

    const validationCheck = check(body, {
      "response has id field": (r) => r.id !== undefined,
    });

    if (validationCheck) {
      const id = body.id;

      console.log(`[VU: ${__VU}, Iter: ${__ITER}] ID: ${id}`);

      if (localIds.has(id)) {
        console.error(
          `[VU: ${__VU}, Iter: ${__ITER}] Duplicate ID detected within VU: ${id}`
        );
      } else {
        localIds.add(id);
      }
    }
  } else {
    failedRequests.add(1);
  }
}

export function handleSummary(data) {
  const totalRequests = data.metrics.http_reqs.values.count;
  const successRate = data.metrics.checks.values.rate * 100;
  const errorRate = data.metrics.http_req_failed.values.rate * 100;

  return {
    stdout: `
=== Load Test Summary ===
Total Requests: ${totalRequests}
Success Rate: ${successRate.toFixed(2)}%
Error Rate: ${errorRate.toFixed(2)}%
Average Response Time: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms
P95 Response Time: ${data.metrics.http_req_duration.values["p(95)"].toFixed(
      2
    )}ms
Max Response Time: ${data.metrics.http_req_duration.values.max.toFixed(2)}ms
Note: Due to k6's per-VU isolation, cross-VU duplicate detection requires post-test analysis.
=== Result: ${
      errorRate < 0.001 && successRate > 0.999 ? "PASS ✓" : "FAIL ✗"
    } ===
    `,
  };
}

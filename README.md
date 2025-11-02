# Gendora

A distributed unique ID generator API built with Spring Boot, implementing a snowflake-inspired algorithm for generating globally unique 64-bit identifiers across multiple instances.

## Overview

Gendora is a high-performance, distributed ID generation service that ensures global uniqueness without requiring a centralized coordinator. It uses a snowflake-inspired algorithm combined with Redis-based slot allocation to coordinate multiple API instances.

## Algorithm: Snowflake-Inspired ID Generation

Gendora implements a variation of Twitter's Snowflake algorithm for generating unique IDs. Each ID is a 64-bit integer composed of:

- **1 bit** (sign bit - reserved)
- **43 bits** for timestamp (milliseconds since custom epoch)
- **10 bits** for machine ID (0-1023, allowing up to 1024 instances)
- **10 bits** for sequence number (0-1023, allowing up to 1024 IDs per millisecond per machine)

### Bit Layout

```
[Sign: 1 bit] [Timestamp: 43 bits] [MachineID: 10 bits] [Sequence: 10 bits]
```

### Custom Epoch

The timestamp uses a custom epoch of `2025-11-01 00:00:00 GMT+0000` (timestamp: 1761955200000), providing approximately 278 years of unique ID generation capacity.

### Features

- **Distributed Coordination**: Uses Redis for automatic slot allocation across instances
- **No Central Coordinator**: Each instance autonomously claims a unique machine ID slot
- **High Throughput**: Can generate up to 1,024 IDs per millisecond per instance
- **Collision-Free**: Guarantees global uniqueness across all instances
- **Clock Safety**: Detects and prevents clock skew issues

### Slot Allocation

The system uses Redis to manage machine ID slots:

- Up to 1024 instances can run simultaneously
- Each instance automatically claims an available slot (0-1023)
- Slots are maintained with TTL-based heartbeats (30-second intervals, 5-minute TTL)
- Failed instances automatically release their slots after TTL expiration

## Purpose

This API is designed for systems requiring:

- Globally unique identifiers in distributed environments
- High-performance ID generation (sub-20ms response times under load)
- Horizontal scalability without configuration changes
- Stateless operation with automatic instance coordination

## Running Locally with Docker Compose

### Prerequisites

- Docker and Docker Compose installed
- Ports 80, 6379 available (or modify in `docker-compose.yml`)

### Quick Start

1. **Start all services:**

   ```bash
   docker-compose up -d
   ```

2. **Verify services are healthy:**

   ```bash
   docker-compose ps
   ```

3. **Test the API:**

   ```bash
   curl -X POST http://localhost/api/generator/ids
   ```

   Example response:

   ```json
   { "id": "1234567890123456789" }
   ```

4. **Generate ID with metadata:**

   ```bash
   curl -X POST "http://localhost/api/generator/ids?include_metadata=true"
   ```

   Example response:

   ```json
   {
     "id": "1234567890123456789",
     "metadata": {
       "timestampDelta": "12345678",
       "epoch": "1761955200000",
       "machineID": 42,
       "sequenceID": 123,
       "algorithm": "snowflake"
     }
   }
   ```

5. **Stop all services:**
   ```bash
   docker-compose down
   ```

### Services

The Docker Compose setup includes:

- **Redis** (port 6379): Slot allocation and coordination
- **API** (internal port 8080): Spring Boot application generating IDs
- **Nginx** (port 80): Reverse proxy and load balancer

## Scaling the API and Restarting Nginx

### Scaling the API Service

To scale the API to multiple instances:

```bash
docker-compose up -d --scale api=5
```

This will start 5 API instances. Each instance will automatically:

1. Connect to Redis
2. Claim an available slot (0-1023)
3. Begin generating unique IDs

**Note**: You can scale up to 1024 instances (the maximum number of available machine ID slots).

### Restarting Nginx

After scaling the API instances, Nginx needs to be reloaded to discover the new instances. The `nginx.local.conf` uses an upstream block that automatically resolves `api:8080` through Docker DNS, but you may need to reload Nginx to refresh its upstream connections:

```bash
# Option 1: Restart the Nginx container
docker-compose restart nginx

# Option 2: Reload Nginx configuration (if using a version with reload support)
docker exec gendora-nginx nginx -s reload

# Option 3: Recreate Nginx to pick up all new instances
docker-compose up -d --force-recreate nginx
```

### Verifying Scaling

1. **Check running instances:**

   ```bash
   docker-compose ps api
   ```

2. **View Nginx upstream status** (if enabled):

   ```bash
   curl http://localhost/nginx-status
   ```

3. **Test load distribution** by making multiple requests and checking metadata for different machine IDs:
   ```bash
   for i in {1..10}; do
     curl -s -X POST "http://localhost/api/generator/ids?include_metadata=true" | jq '.metadata.machineID'
   done
   ```

## Load Testing

### Prerequisites

- [k6](https://k6.io/docs/getting-started/installation/) installed
- API running via Docker Compose

### Running the Load Test

From the `load-testing` directory:

```bash
cd load-testing
./run-load-test.sh
```

Or manually:

```bash
k6 run load-test.js
```

### What the Load Test Does

The load test (`load-test.js`) performs a comprehensive evaluation of the API:

1. **Load Pattern**:

   - Ramps up to 100 virtual users over 15 seconds
   - Maintains 500 virtual users for 5 minutes
   - Ramps down to 0 over 15 seconds

2. **Performance Thresholds**:

   - **99th percentile response time**: Must be below 20ms
   - **Error rate**: Must be less than 0.1%
   - **Check success rate**: Must be greater than 99.9%

3. **Validation**:

   - Verifies HTTP 200 status codes
   - Validates JSON response format
   - Checks for `id` field presence
   - Tracks duplicate IDs within each virtual user (per-VU tracking)

4. **Metrics Collected**:

   - Total requests
   - Success and error rates
   - Response time statistics (average, P95, P99, max)
   - Request duration distribution

5. **Uniqueness Analysis**:
   After the load test completes, `analyze-uniqueness.sh` automatically:
   - Extracts all generated IDs from the test output
   - Verifies global uniqueness across all requests
   - Reports any duplicates found
   - Provides a pass/fail result

### Output

The load test generates:

- Real-time console output with metrics
- Detailed summary with performance statistics
- A log file in `output/load-test-output-<timestamp>.log`
- Uniqueness analysis report

### Expected Results

Under normal conditions, the load test should show:

- Sub-20ms response times for 99% of requests
- Error rate below 0.1%
- 100% unique IDs generated (no duplicates)
- All checks passing

## API Endpoints

### Generate ID

```http
POST /api/generator/ids
```

**Query Parameters:**

- `include_metadata` (optional, default: `false`): Include ID decomposition metadata

**Response:**

```json
{
  "id": "1234567890123456789"
}
```

Or with metadata:

```json
{
  "id": "1234567890123456789",
  "metadata": {
    "timestampDelta": "12345678",
    "epoch": "1761955200000",
    "machineID": 42,
    "sequenceID": 123,
    "algorithm": "snowflake"
  }
}
```

### Health Check

```http
GET /api/actuator/health
```

Returns service health status.

## Architecture

```
┌─────────┐
│  Client │
└────┬────┘
     │
     ▼
┌─────────┐
│  Nginx  │ (Load Balancer/Reverse Proxy)
└────┬────┘
     │
     ├──────────┬──────────┬──────────┐
     ▼          ▼          ▼          ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│ API #1  │ │ API #2  │ │ API #3  │ │ API #N  │
│ Slot:42 │ │ Slot:7  │ │ Slot:99 │ │ Slot:...│
└────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
     │          │          │          │
     └──────────┴──────────┴──────────┘
                   │
                   ▼
              ┌─────────┐
              │  Redis  │ (Slot Allocation)
              └─────────┘
```

## Technology Stack

- **Java 21**: Application runtime
- **Spring Boot 3.4.1**: Web framework
- **Redis**: Distributed slot allocation
- **Nginx**: Reverse proxy and load balancing
- **k6**: Load testing
- **Docker & Docker Compose**: Containerization and orchestration

## License

MIT License

Copyright (c) 2025 Gendora Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

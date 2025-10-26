#!/bin/bash

set -e

echo "Starting k6 load test..."
echo ""

TIMESTAMP=$(date +%Y%m%d%H%M%S)
k6 run load-test.js 2>&1 | tee output/load-test-output-${TIMESTAMP}.log

echo ""
echo "Load test completed. Analyzing ID uniqueness..."
echo ""

./analyze-uniqueness.sh output/load-test-output-${TIMESTAMP}.log


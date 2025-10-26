#!/bin/bash

if [ $# -eq 0 ]; then
  echo "Usage: $0 <k6-output-file>"
  echo "Example: $0 output/load-test-output.log"
  exit 1
fi

INPUT_FILE="$1"
TEMP_FILE=$(mktemp)

grep -o '\[VU: [0-9]*, Iter: [0-9]*\] ID: [0-9]*' "$INPUT_FILE" | sed 's/.*ID: \([0-9]*\)/\1/' > "$TEMP_FILE"

TOTAL_IDS=$(wc -l < "$TEMP_FILE")
UNIQUE_IDS=$(sort -u "$TEMP_FILE" | wc -l)
DUPLICATES=$((TOTAL_IDS - UNIQUE_IDS))

echo "=== ID Uniqueness Analysis ==="
echo "Total IDs collected: $TOTAL_IDS"
echo "Unique IDs: $UNIQUE_IDS"
echo "Duplicate IDs found: $DUPLICATES"

if [ $DUPLICATES -gt 0 ]; then
  echo ""
  echo "Duplicates found: $DUPLICATES"
  sort "$TEMP_FILE" | uniq -d
  echo ""
  echo "Result: FAIL ✗"
  rm "$TEMP_FILE"
  exit 1
else
  echo ""
  echo "Result: PASS ✓"
  rm "$TEMP_FILE"
  exit 0
fi


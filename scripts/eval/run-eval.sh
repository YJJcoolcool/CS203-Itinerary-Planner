#!/usr/bin/env bash
#
# Replan evaluation harness.
#
# For each scenario this triggers a replan at a pinned time, then reads the
# suggestions created by that run and prints the outcomes the report needs:
#
#   sugg       suggestions produced by that replan
#   aiPick     suggestions where the chooser's pick was used (vs fallback)
#   osmValid   proposed places carrying an OSM identity
#   indoorBad  rainy swaps that proposed an outdoor place (must be 0)
#   latencyMs  end-to-end replan + read time
#
# Requirements: curl, jq.
# Usage:
#   TOKEN=<clerk bearer token> TRIP_ID=<trip id> ./scripts/eval/run-eval.sh
#   API_URL=http://localhost:8080 TOKEN=... ./scripts/eval/run-eval.sh path/to/scenarios.json
#
# Caveats:
#   - Relevance (1-5) is a manual rating; this script cannot judge it.
#   - Without OPENAI_API_KEY the chooser is disabled, so aiPick will be 0 and
#     every suggestion comes from the deterministic fallback. That is still a
#     useful baseline row.
#   - A scenario only produces suggestions when the weather source says it is
#     raining in that window. The dry scenario should produce none.
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
TOKEN="${TOKEN:?Set TOKEN to a Clerk bearer token for a user who can edit the trip}"
TRIP_ID="${TRIP_ID:-}"
SCENARIOS="${1:-$(dirname "$0")/scenarios.json}"

command -v jq >/dev/null 2>&1 || { echo "jq is required" >&2; exit 1; }
[ -f "$SCENARIOS" ] || { echo "Scenario file not found: $SCENARIOS" >&2; exit 1; }

total=0
ai_total=0
osm_total=0
bad_total=0

printf '%-30s %6s %7s %9s %10s %10s\n' "scenario" "sugg" "aiPick" "osmValid" "indoorBad" "latencyMs"

count=$(jq '.scenarios | length' "$SCENARIOS")

for ((i = 0; i < count; i++)); do
  name=$(jq -r ".scenarios[$i].name" "$SCENARIOS")
  at=$(jq -r ".scenarios[$i].at" "$SCENARIOS")
  trip=$(jq -r ".scenarios[$i].tripId // empty" "$SCENARIOS")
  expect_indoor=$(jq -r ".scenarios[$i].expectIndoor // false" "$SCENARIOS")

  if [ -z "$trip" ]; then trip="$TRIP_ID"; fi
  if [ -z "$trip" ]; then
    echo "Scenario '$name' has no tripId and TRIP_ID is unset" >&2
    exit 1
  fi

  since=$(date -u +%Y-%m-%dT%H:%M:%S)
  start=$(python3 -c 'import time; print(int(time.time() * 1000))')

  curl -sS -o /dev/null -X POST "$API_URL/api/trips/$trip/replan" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"at\":\"$at\"}"

  curl -sS "$API_URL/api/trips/$trip/suggestions" \
    -H "Authorization: Bearer $TOKEN" > "/tmp/replan-eval-$i.json"

  end=$(python3 -c 'import time; print(int(time.time() * 1000))')
  latency=$((end - start))

  filter="[.[] | select(.status == \"PENDING\" and .createdAt >= \"$since\")]"

  sugg=$(jq "$filter | length" "/tmp/replan-eval-$i.json")
  ai=$(jq "$filter | map(select(.aiPick)) | length" "/tmp/replan-eval-$i.json")
  osm=$(jq "$filter | map(select(.proposedLocation.osmId != null)) | length" "/tmp/replan-eval-$i.json")

  bad=0
  if [ "$expect_indoor" = "true" ] && [ "$sugg" -gt 0 ]; then
    bad=$(jq "$filter | map(select(.proposedLocation.indoorClass != \"INDOOR\")) | length" "/tmp/replan-eval-$i.json")
  fi

  printf '%-30s %6d %7d %9d %10d %10d\n' "$name" "$sugg" "$ai" "$osm" "$bad" "$latency"

  total=$((total + sugg))
  ai_total=$((ai_total + ai))
  osm_total=$((osm_total + osm))
  bad_total=$((bad_total + bad))
done

echo
echo "totals: suggestions=$total aiPicks=$ai_total osmVerified=$osm_total indoorViolations=$bad_total"

if [ "$bad_total" -ne 0 ]; then
  echo "FAIL: rainy swaps proposed outdoor places" >&2
  exit 1
fi

#!/usr/bin/env bash
#
# build-sg-pois.sh - turn a Singapore OSM extract into the POI GeoJSON used to
# curate backend/src/main/resources/seed/sg_pois.json.
#
# The raw extract and generated files live in data/osm/ and are gitignored.
# Only the curated seed JSON is committed.
#
# Usage:
#   ./scripts/build-sg-pois.sh [path/to/singapore-latest.osm.pbf]
#
# Requires osmium-tool: brew install osmium-tool
#
# Data: © OpenStreetMap contributors, ODbL 1.0.
# Source extract: https://download.openstreetmap.fr/extracts/asia/singapore-latest.osm.pbf
# (Geofabrik alternative: asia/malaysia-singapore-brunei-latest.osm.pbf, clip first
#  with `osmium extract -b 103.6,1.15,104.1,1.48 ...`.)
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OSM_DIR="$ROOT_DIR/data/osm"
INPUT="${1:-$OSM_DIR/singapore-latest.osm.pbf}"
FILTERED="$OSM_DIR/sg-pois.osm.pbf"
OUTPUT="$OSM_DIR/sg-pois.geojson"

command -v osmium >/dev/null 2>&1 || {
  echo "osmium not found. Install it with: brew install osmium-tool" >&2
  exit 1
}

[ -f "$INPUT" ] || {
  echo "Extract not found: $INPUT" >&2
  echo "Download https://download.openstreetmap.fr/extracts/asia/singapore-latest.osm.pbf" >&2
  echo "and place it in $OSM_DIR/." >&2
  exit 1
}

mkdir -p "$OSM_DIR"

# Keep only the POI categories the planner can use. Everything else (roads,
# buildings, landuse, ...) is dropped to keep the output small.
osmium tags-filter --overwrite "$INPUT" \
  nwr/tourism=attraction,museum,gallery,viewpoint,zoo,theme_park \
  nwr/shop=mall,department_store \
  nwr/leisure=park,bowling_alley \
  nwr/amenity=restaurant,cafe,food_court,marketplace,cinema,library \
  -o "$FILTERED"

# Export with stable ids ("@type"/"@id") so the curated seed can reference the
# exact OSM element. The seed builder dedupes node/way/relation duplicates.
osmium export --overwrite -u type_id -a type,id \
  "$FILTERED" -f geojson -o "$OUTPUT"

printf 'Wrote %s\n' "$OUTPUT"
printf 'Next: curate places into backend/src/main/resources/seed/sg_pois.json\n'

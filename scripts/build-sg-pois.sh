#!/usr/bin/env bash
#
# build-sg-pois.sh - turn a country OSM extract into the POI GeoJSON used to
# curate backend/src/main/resources/seed/<country>_pois.json.
#
# The raw extract and generated files live in data/osm/ and are gitignored.
# Only the curated seed JSON is committed.
#
# Usage:
#   ./scripts/build-sg-pois.sh [country-code] [path/to/extract.osm.pbf]
#
# Country defaults to sg, so existing commands keep working.
# Requires osmium-tool: brew install osmium-tool
#
# Data: © OpenStreetMap contributors, ODbL 1.0.
# Source extract (sg): https://download.openstreetmap.fr/extracts/asia/singapore-latest.osm.pbf
# (Geofabrik alternative: asia/malaysia-singapore-brunei-latest.osm.pbf, clip first
#  with `osmium extract -b 103.6,1.15,104.1,1.48 ...`.)
# Other countries can override OSM_EXTRACT_BASE to point at the right continent.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OSM_DIR="$ROOT_DIR/data/osm"
COUNTRY="${1:-sg}"
COUNTRY="${COUNTRY,,}"

case "$COUNTRY" in
  sg) EXTRACT_NAME="singapore" ;;
  *)  EXTRACT_NAME="$COUNTRY" ;;
esac

EXTRACT_BASE="${OSM_EXTRACT_BASE:-https://download.openstreetmap.fr/extracts/asia}"
EXTRACT_URL="$EXTRACT_BASE/$EXTRACT_NAME-latest.osm.pbf"
INPUT="${2:-$OSM_DIR/$EXTRACT_NAME-latest.osm.pbf}"
FILTERED="$OSM_DIR/$COUNTRY-pois.osm.pbf"
OUTPUT="$OSM_DIR/$COUNTRY-pois.geojson"

command -v osmium >/dev/null 2>&1 || {
  echo "osmium not found. Install it with: brew install osmium-tool" >&2
  exit 1
}

[ -f "$INPUT" ] || {
  echo "Extract not found: $INPUT" >&2
  echo "Download $EXTRACT_URL" >&2
  echo "and place it in $OSM_DIR/." >&2
  exit 1
}

mkdir -p "$OSM_DIR"

# Keep only the POI categories the planner can use. Everything else (roads,
# buildings, landuse, ...) is dropped to keep the output small.
osmium tags-filter --overwrite "$INPUT" \
  nwr/tourism=attraction,museum,gallery,viewpoint,zoo,theme_park \
  nwr/shop=mall,department_store \
  nwr/leisure=park,garden,nature_reserve,bowling_alley \
  nwr/amenity=restaurant,cafe,food_court,marketplace,cinema,library,bar,nightclub,place_of_worship \
  nwr/historic \
  -o "$FILTERED"

# Export with stable ids ("@type"/"@id") so the curated seed can reference the
# exact OSM element. The seed builder dedupes node/way/relation duplicates.
osmium export --overwrite -u type_id -a type,id \
  "$FILTERED" -f geojson -o "$OUTPUT"

printf 'Wrote %s\n' "$OUTPUT"
printf 'Next: curate places into backend/src/main/resources/seed/%s_pois.json\n' "$COUNTRY"

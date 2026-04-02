#!/usr/bin/env bash
set -euo pipefail

AEM_HOST="${AEM_HOST:-http://localhost:4502}"
ASSET_PATH="${ASSET_PATH:-/content/dam/semanticdam/asset.jpg}"

echo "Querying JSON metadata for ${ASSET_PATH}"
curl -s "${AEM_HOST}/bin/aemassets/metadata.json?path=${ASSET_PATH}" | sed 's/^/JSON: /'
echo
echo "Querying Turtle metadata for ${ASSET_PATH}"
curl -s "${AEM_HOST}/bin/aemassets/metadata.ttl?path=${ASSET_PATH}" | sed 's/^/TTL: /'

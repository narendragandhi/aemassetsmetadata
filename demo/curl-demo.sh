#!/usr/bin/env bash
set -euo pipefail

AEM_HOST="${AEM_HOST:-http://localhost:4502}"
AEM_USER="${AEM_USER:-admin}"
AEM_PASSWORD="${AEM_PASSWORD:-admin}"
ASSET_PATH="${ASSET_PATH:-/content/dam/semanticdam/asset.jpg}"
auth=(-u "${AEM_USER}:${AEM_PASSWORD}")

echo "== Context document for ${ASSET_PATH}"
curl -s "${auth[@]}" "${AEM_HOST}/bin/aemassets/context.json?path=${ASSET_PATH}"
echo
echo "== Assets of the same brand"
curl -s "${auth[@]}" "${AEM_HOST}/bin/aemassets/related.json?predicate=Brand&value=Summit"
echo
echo "== Turtle export"
curl -s "${auth[@]}" "${AEM_HOST}/bin/aemassets/metadata.ttl?path=${ASSET_PATH}"
echo
echo "== MCP: tools an agent can call"
curl -s "${auth[@]}" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' "${AEM_HOST}/bin/aemassets/mcp"
echo

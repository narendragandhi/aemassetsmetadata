#!/usr/bin/env bash
set -euo pipefail

qdrant_url="${QDRANT_URL:-http://localhost:6333}"
collection="${QDRANT_COLLECTION:-semanticdam_verify_$$}"
cleanup() {
  /usr/bin/curl -fsS -X DELETE "$qdrant_url/collections/$collection" >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Checking Qdrant at $qdrant_url"
/usr/bin/curl -fsS "$qdrant_url/healthz" >/dev/null
/usr/bin/curl -fsS -X PUT "$qdrant_url/collections/$collection" \
  -H 'Content-Type: application/json' \
  --data '{"vectors":{"size":3,"distance":"Cosine"}}' >/dev/null
/usr/bin/curl -fsS -X PUT "$qdrant_url/collections/$collection/points?wait=true" \
  -H 'Content-Type: application/json' \
  --data '{"points":[{"id":"11111111-1111-1111-1111-111111111111","vector":[1,0,0],"payload":{"assetId":"/content/dam/demo-a.jpg","modelId":"demo-model","modelVersion":"1"}},{"id":"22222222-2222-2222-2222-222222222222","vector":[0,1,0],"payload":{"assetId":"/content/dam/demo-b.jpg","modelId":"demo-model","modelVersion":"1"}}]}' >/dev/null

search_response="$(/usr/bin/curl -fsS -X POST "$qdrant_url/collections/$collection/points/search" \
  -H 'Content-Type: application/json' \
  --data '{"vector":[1,0,0],"limit":2,"with_payload":true}')"
if [[ "$search_response" != *"/content/dam/demo-a.jpg"* || "$search_response" != *"\"score\":1.0"* ]]; then
  echo "Qdrant search verification failed" >&2
  exit 1
fi

/usr/bin/curl -fsS -X POST "$qdrant_url/collections/$collection/points/delete?wait=true" \
  -H 'Content-Type: application/json' \
  --data '{"points":["22222222-2222-2222-2222-222222222222"]}' >/dev/null
echo "Qdrant verification passed: health, collection, upsert, search, delete"

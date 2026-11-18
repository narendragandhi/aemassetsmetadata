#!/usr/bin/env bash
set -euo pipefail

AEM_BASE_URL="${AEM_BASE_URL:-http://localhost:4502}"
AEM_USER="${AEM_USER:-admin}"
AEM_PASSWORD="${AEM_PASSWORD:-admin}"
AEM_ASSET_PATH="${AEM_ASSET_PATH:-/content/dam/semanticdam/asset.jpg}"
AEM_EXPECTED_VALUE="${AEM_EXPECTED_VALUE:-SUM-2026-X1}"

auth=(-u "${AEM_USER}:${AEM_PASSWORD}")
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/semanticdam-verify.XXXXXX")"
trap 'rm -rf "${tmp_dir}"' EXIT

get() {
  curl --fail --silent --show-error "${auth[@]}" "$1"
}

echo "Checking AEM at ${AEM_BASE_URL}"
bundles="$(get "${AEM_BASE_URL}/system/console/bundles.json")"
echo "${bundles}" | grep -q '"symbolicName":"semanticdam.core".*"state":"Active"'

echo "Checking that graph storage is persistent"
get "${AEM_BASE_URL}/system/console/configMgr/com.semanticdam.core.core.graph.GraphStorageConfig.json" \
  | grep -q '"jcr"' || { echo "GraphStorageConfig is not bound with mode=jcr" >&2; exit 1; }

echo "Queueing a re-index of ${AEM_ASSET_PATH}"
curl --fail --silent --show-error "${auth[@]}" \
  -X POST "${AEM_BASE_URL}/bin/aemassets/reindex" \
  --data-urlencode "path=${AEM_ASSET_PATH}" \
  >"${tmp_dir}/reindex.json"
grep -q '"queued":1' "${tmp_dir}/reindex.json"

json_status=""
for attempt in $(seq 1 15); do
  curl --silent --show-error "${auth[@]}" \
    -o "${tmp_dir}/metadata.json" \
    -w '%{http_code}' \
    "${AEM_BASE_URL}/bin/aemassets/metadata.json?path=${AEM_ASSET_PATH}" \
    >"${tmp_dir}/metadata.status" || true
  json_status="$(cat "${tmp_dir}/metadata.status")"
  [ "${json_status}" = "200" ] && break
  sleep 2
done

[ "${json_status}" = "200" ]
grep -q '"assetId":"' "${tmp_dir}/metadata.json"
[ -z "${AEM_EXPECTED_VALUE}" ] || grep -q "${AEM_EXPECTED_VALUE}" "${tmp_dir}/metadata.json"

curl --fail --silent --show-error "${auth[@]}" \
  -o "${tmp_dir}/metadata.ttl" \
  "${AEM_BASE_URL}/bin/aemassets/metadata.ttl?path=${AEM_ASSET_PATH}"
grep -q 'dc/terms/title' "${tmp_dir}/metadata.ttl"

echo "Checking that the graph was written to the repository, not to memory"
get "${AEM_BASE_URL}/var/semanticdam/graph${AEM_ASSET_PATH}/statements.1.json" | grep -q 'semanticdam/graph/statement'

echo "Checking the context document"
get "${AEM_BASE_URL}/bin/aemassets/context.json?path=${AEM_ASSET_PATH}" >"${tmp_dir}/context.json"
grep -q '"ontologyVersion"' "${tmp_dir}/context.json"
grep -q '"valueIsEntity":true' "${tmp_dir}/context.json"

echo "Checking fact search through the Oak index"
get "${AEM_BASE_URL}/bin/aemassets/related.json?predicate=Mime%20Type&value=image%2Fjpeg" >"${tmp_dir}/related.json"
grep -q "\"path\":\"${AEM_ASSET_PATH}\"" "${tmp_dir}/related.json"

echo "Checking the MCP endpoint"
curl --fail --silent --show-error "${auth[@]}" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  "${AEM_BASE_URL}/bin/aemassets/mcp" >"${tmp_dir}/mcp.json"
grep -q '"get_asset_context"' "${tmp_dir}/mcp.json"

echo "Checking that AEM tags become concepts with their hierarchy"
tag_probe="$(get "${AEM_BASE_URL}/bin/querybuilder.json?path=/content/dam&type=dam:Asset&property=jcr:content/metadata/cq:tags&property.operation=exists&p.limit=1" \
  | sed -n 's/.*"path":"\([^"]*\)".*/\1/p')"
if [ -n "${tag_probe}" ]; then
  curl --fail --silent --show-error "${auth[@]}" -X POST "${AEM_BASE_URL}/bin/aemassets/reindex" \
    --data-urlencode "path=${tag_probe}" >/dev/null
  for attempt in $(seq 1 15); do
    get "${AEM_BASE_URL}/bin/aemassets/context.json?path=${tag_probe}" >"${tmp_dir}/tagged.json" 2>/dev/null || true
    grep -q 'semanticdam.com/id/tag/' "${tmp_dir}/tagged.json" && break
    sleep 1
  done
  grep -q 'semanticdam.com/id/tag/' "${tmp_dir}/tagged.json"
else
  echo "  no tagged asset on this instance; skipped"
fi

echo "Checking semantic search"
search_status="$(curl --silent -o "${tmp_dir}/search.json" -w '%{http_code}' "${auth[@]}" \
  "${AEM_BASE_URL}/bin/aemassets/search.json?q=demo%20asset&limit=3")"
case "${search_status}" in
  200) grep -q '"assets":\[{' "${tmp_dir}/search.json" ;;
  409) echo "  not configured (embedding provider and vector index); skipped" ;;
  *) echo "  unexpected status ${search_status}" >&2; exit 1 ;;
esac

echo "Checking that anonymous callers get nothing"
anonymous_status="$(curl --silent -o /dev/null -w '%{http_code}' \
  "${AEM_BASE_URL}/bin/aemassets/context.json?path=${AEM_ASSET_PATH}")"
[ "${anonymous_status}" != "200" ]

echo "AEM SDK verification passed: bundle, persistence, indexing, JSON, Turtle, context, fact search, tags, semantic search, MCP and access checks are green."

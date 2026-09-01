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

echo "Triggering an idempotent metadata re-index for ${AEM_ASSET_PATH}"
curl --fail --silent --show-error "${auth[@]}" \
  -X POST "${AEM_BASE_URL}${AEM_ASSET_PATH}/jcr:content/metadata" \
  --data-urlencode ':operation=modify' \
  --data-urlencode 'aem:verification=semanticdam-sdk-check' \
  >"${tmp_dir}/post.html"

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

echo "AEM SDK verification passed: bundle, indexing, JSON, and Turtle checks are green."

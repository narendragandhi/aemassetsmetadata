#!/usr/bin/env bash
# SemanticDAM guided demo: tells the story act by act against a local AEM author.
#
#   bash demo/run-demo.sh            pause after every act (press Enter)
#   bash demo/run-demo.sh --auto     run straight through
#
# Env: AEM_HOST (http://localhost:4502), AEM_USER/AEM_PASSWORD (admin/admin).
# Needs: the project deployed (mvn clean install -PautoInstallSinglePackage), curl, python3.
# Optional: WKND shared content (tags act), semantic search configured (agents act).
# The demo writes only its own data: /content/dam/semanticdam/demo-*.jpg, their vocabulary entries,
# and a text component on /content/semanticdam/us/en. Rerunning resets them.
set -euo pipefail

AEM="${AEM_HOST:-http://localhost:4502}"
AUTH="${AEM_USER:-admin}:${AEM_PASSWORD:-admin}"
AUTO=false; [ "${1:-}" = "--auto" ] && AUTO=true
SRC=/content/dam/semanticdam/asset.jpg
HERO=/content/dam/semanticdam/demo-hero.jpg
BANNER=/content/dam/semanticdam/demo-banner.jpg
PAGE=/content/semanticdam/us/en
TAGGED=/content/dam/wknd-shared/en/adventures/cycling-tuscany/cycling-tuscany

bold=$'\e[1m'; dim=$'\e[2m'; cyan=$'\e[36m'; green=$'\e[32m'; yellow=$'\e[33m'; off=$'\e[0m'
act()   { echo; echo "${bold}${cyan}== $1${off}"; echo "${dim}$2${off}"; }
say()   { echo "  $*"; }
proof() { echo "  ${green}PROVES:${off} $*"; }
pause() { $AUTO || { read -r -p "  ${dim}[Enter for next act]${off} " _ < /dev/tty; }; }
get()   { curl -sf -u "$AUTH" "$AEM$1"; }
post()  { curl -sf -o /dev/null -u "$AUTH" -X POST "$AEM$1" "${@:2}"; }
json()  { python3 -c "$1"; }
wait_for() { # path, python condition on parsed context json
  for _ in $(seq 1 20); do
    if get "/bin/aemassets/context.json?path=$1" 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); sys.exit(0 if ($2) else 1)" 2>/dev/null; then return 0; fi
    sleep 1
  done
  echo "  ${yellow}(timed out waiting for $1)${off}"
}
entity_path() { # IRI -> JCR path of the entity node
  local h; h=$(printf '%s' "$1" | shasum -a 256 | cut -c1-64); echo "/var/semanticdam/entities/${h:0:2}/$h"
}
facts() { # print a context document's facts as a table
  json "
import json,sys
d=json.load(sys.stdin)
for f in d['facts']:
    v=(f.get('valueLabel') or f['value'])[:34]
    print('    %-20s %-34s %-17s %s' % (f['label'], v[:34], f['source'], 'valid' if f['valid'] else 'INVALID'))
for v in d['violations']: print('    \033[33mviolation:\033[0m', v['message'])
print('    ready for:', ', '.join(d['readyFor']) or '-')"
}

# ------------------------------------------------------------------------------------------------
act "Setup" "Resetting the demo's own assets, vocabulary entries and page component."
get /system/console/bundles.json | grep -q '"symbolicName":"semanticdam.core".*"state":"Active"' \
  || { echo "SemanticDAM bundle is not active on $AEM"; exit 1; }
for a in "$HERO" "$BANNER"; do post "$a" --data-urlencode ":operation=delete" || true; done
for iri in https://semanticdam.com/id/brand/northwind https://semanticdam.com/id/brand/nortwind; do
  post "$(entity_path "$iri")" --data-urlencode ":operation=delete" || true
done
post "$PAGE/jcr:content/root/container/container/semanticdam_demo" --data-urlencode ":operation=delete" || true
sleep 2
say "ready."
pause

# ------------------------------------------------------------------------------------------------
act "Act 0 - The picture" "An accelerator inside AEM: a governed knowledge layer under Adobe's AI, not beside it."
cat <<'PICTURE'
     PIM   Workfront   DAM authors   AEM tags                 Information architect
       \        |           |          /                        (Turtle, no code)
        v       v           v         v                                |
   +--------------------------------------------------------------------v-----------+
   | AEM Assets   jcr:content/metadata          SemanticDAM (one bundle, author)     |
   |                 |  change -> Sling job     domain ontology + application profile|
   |                 v                                                              |
   |   map -> controlled vocabulary -> validate (SHACL) -> infer -> governed graph   |
   |          (steward reviews candidates)                     |  facts + provenance  |
   |                 ^                                          |  entities, schemes  |
   |   written back as semanticdam:* metadata <-----------------+                     |
   +-----------------|--------------------------------------------|------------------+
                     | replicates with the asset                  |
         Sites pages ((semantic_properties.*))       context API, MCP tools, JSON-LD, events
         full-page cache, targeted invalidation       (filtered by the caller's access)
                     |                                            |
          visitors, AEM AI Search, Content Hub         agents next to Adobe's AEM MCP Server
PICTURE
say "${bold}Value in one line:${off} Adobe's AI finds and acts on assets; SemanticDAM makes the facts"
say "underneath consistent, validated, explainable and connected - and owned by the customer."
pause

# ------------------------------------------------------------------------------------------------
act "Act 1 - The problem" "Metadata arrives from authors, PIM and Workfront under different names, unvalidated."
post "$SRC" --data-urlencode ":operation=copy" --data-urlencode ":dest=$HERO"
post "$HERO/jcr:content/metadata" \
  --data-urlencode "dc:title=Trail runner hero" \
  --data-urlencode "aem:brand=Northwind" \
  --data-urlencode "pim:sku=NW-TR-2026" \
  --data-urlencode "pim:category=Organic" \
  --data-urlencode "workfront:projectID=PRJ-42" \
  --data-urlencode "workfront:campaignName=Summer Promo 2026"
say "Raw DAM metadata on $HERO:"
get "$HERO/jcr:content/metadata.json" | json "
import json,sys
d=json.load(sys.stdin)
for k in ('dc:title','aem:brand','pim:sku','pim:category','workfront:projectID','workfront:campaignName'):
    print('    %-24s %s' % (k, d.get(k)))"
say "Adobe's AI Search and agents work on exactly this. Which brand is official? Is PRJ-42 a valid"
say "Workfront ID? Is 'Organic' a category we govern? Nothing says."
pause

# ------------------------------------------------------------------------------------------------
act "Act 2 - Governed facts" "The same asset after SemanticDAM: every fact has a source, trust and validity."
wait_for "$HERO" "any(f['label']=='SKU' for f in d['facts'])"
get "/bin/aemassets/context.json?path=$HERO" | facts
proof "facts carry provenance (PROV agent + trust); 'Organic' resolved to the governed concept"
proof "'Organic Cotton' (an alternative label); 'Summer Promo 2026' to the official campaign entry;"
proof "the bad Workfront ID is flagged with the shape's own message instead of silently accepted."
pause

# ------------------------------------------------------------------------------------------------
act "Act 3 - Controlled vocabulary" "Authors are not the authority for brands: 'Northwind' waits for a steward."
for _ in $(seq 1 20); do # the statement index is asynchronous; wait until the usage is visible
  get "/bin/aemassets/vocabulary/candidates.json?class=Brand" | grep -q '"assetCount":[1-9]' && break; sleep 1
done
get "/bin/aemassets/vocabulary/candidates.json?class=Brand" | json "
import json,sys
for c in json.load(sys.stdin)['candidates']: print('    candidate %-14s used by %d asset(s)' % (c['label'], c['assetCount']))"
say "A second asset arrives with a typo:"
post "$SRC" --data-urlencode ":operation=copy" --data-urlencode ":dest=$BANNER"
post "$BANNER/jcr:content/metadata" --data-urlencode "aem:brand=Nortwind" --data-urlencode "dc:title=Trail banner"
wait_for "$BANNER" "any(f['label']=='Brand' and 'nortwind' in f['value'] for f in d['facts'])"
for _ in $(seq 1 20); do
  get "/bin/aemassets/vocabulary/candidates.json?class=Brand" | grep -q 'Nortwind.*"assetCount":[1-9]' && break; sleep 1
done
say "Steward approves 'Northwind' and merges 'Nortwind' into it:"
post /bin/aemassets/vocabulary/review --data-urlencode "entity=https://semanticdam.com/id/brand/northwind" \
  --data-urlencode "decision=approve" --data-urlencode "reason=official brand"
post /bin/aemassets/vocabulary/review --data-urlencode "entity=https://semanticdam.com/id/brand/nortwind" \
  --data-urlencode "decision=merge" --data-urlencode "target=https://semanticdam.com/id/brand/northwind" \
  --data-urlencode "reason=typo"
say "${dim}(re-ingest of the affected assets runs as jobs; waiting for them)${off}"
wait_for "$HERO" "any(f['label']=='Brand' and f['valid'] for f in d['facts'])"
wait_for "$BANNER" "any(f['label']=='Brand' and f['value'].endswith('/northwind') and f['valid'] for f in d['facts'])"
for a in "$HERO" "$BANNER"; do
  get "/bin/aemassets/context.json?path=$a" | json "
import json,sys
d=json.load(sys.stdin); b=[f for f in d['facts'] if f['label']=='Brand'][0]
print('    %-16s brand -> %-10s %s' % (d['path'].split('/')[-1], b['valueLabel'], 'valid' if b['valid'] else 'INVALID'))"
done
proof "one governed brand entry; the misspelling is now a hidden label and resolves automatically."
pause

# ------------------------------------------------------------------------------------------------
act "Act 4 - Taxonomy from AEM tags" "Tags become SKOS concepts; assets are indexed at the most specific one."
if get "/bin/aemassets/context.json?path=$TAGGED" >/dev/null 2>&1; then
  get "/bin/aemassets/context.json?path=$TAGGED" | json "
import json,sys
d=json.load(sys.stdin)
for f in d['facts']:
    if 'Subject' in f['label']: print('    %-16s %s' % (f['label'], f['valueLabel']))"
  say "Search 'Subject = EMEA' finds assets tagged Italy or France, without tagging them EMEA:"
  curl -sf -G -u "$AUTH" "$AEM/bin/aemassets/related.json" --data-urlencode "predicate=Subject" \
    --data-urlencode "value=https://semanticdam.com/id/tag/wknd-shared/region/emea" | json "
import json,sys
print('    ', [a['path'].split('/')[-1] for a in json.load(sys.stdin)['assets']])"
  proof "AEM's own tags become a governed taxonomy with schemes; specificity is preserved."
else
  say "${yellow}WKND shared content not installed; skipping.${off}"
fi
pause

# ------------------------------------------------------------------------------------------------
act "Act 5 - Connected assets" "Related means 'shares a governed entity', with the reason."
get "/bin/aemassets/context.json?path=$HERO&related=3" | json "
import json,sys
for r in json.load(sys.stdin)['related']:
    print('    %.2f %-22s because: %s' % (r['score'], r['path'].split('/')[-1], ', '.join(v['entityLabel'] for v in r['via'])))"
proof "relationships are explainable; rare shared entities count more than common ones."
pause

# ------------------------------------------------------------------------------------------------
act "Act 6 - Facts on a Sites page" "((semantic_properties.*)) in authored text; full-page caching kept."
post "$PAGE/jcr:content" --data-urlencode "semanticdam:asset=$HERO"
post "$PAGE/jcr:content/root/container/container/semanticdam_demo" \
  --data-urlencode "sling:resourceType=semanticdam/components/text" --data-urlencode "textIsRich=true" \
  --data-urlencode 'text=<p>DEMO: Shop ((semantic_properties.brand)) - SKU ((semantic_properties.sku)), part of ((semantic_properties.partOfCampaign)).</p>'
for _ in $(seq 1 20); do get "$HERO/jcr:content/metadata.json" | grep -q '"semanticdam:brand"' && break; sleep 1; done
for _ in $(seq 1 20); do out=$(curl -s -u "$AUTH" "$AEM$PAGE.html?wcmmode=disabled" || true); echo "$out" | grep -q '<p>DEMO:' && break; sleep 2; done
say "Visitor sees:  $(echo "$out" | grep -o '<p>DEMO:[^<]*</p>' | head -1)"
say "Author sees:   $(curl -s -u "$AUTH" "$AEM$PAGE.html?wcmmode=edit" | grep -o '<p>DEMO:[^<]*</p>' | head -1)"
post /bin/aemassets/invalidate --data-urlencode "asset=$HERO" || true
proof "values come from the asset's own metadata (replicates with it); tokens stay editable;"
proof "on republish only pages naming this asset are invalidated (needs a publish agent)."
pause

# ------------------------------------------------------------------------------------------------
act "Act 7 - Agents" "Adobe's AEM MCP Server acts on assets; SemanticDAM's MCP tools explain them."
tools=$(curl -sf -u "$AUTH" -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' "$AEM/bin/aemassets/mcp" \
  | json "import json,sys; print(', '.join(t['name'] for t in json.load(sys.stdin)['result']['tools']))")
say "MCP tools: $tools"
curl -sf -u "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"explain_relationship\",\"arguments\":{\"from\":\"$BANNER\",\"to\":\"$HERO\"}}}" \
  "$AEM/bin/aemassets/mcp" | json "
import json,sys
p=json.load(sys.stdin)['result'].get('structuredContent',{}).get('path',[])
print('    explain_relationship:', ' -> '.join(s.get('label') or s['id'].split('/')[-1] for s in p))"
if echo "$tools" | grep -q semantic_search; then
  curl -sf -u "$AUTH" -H 'Content-Type: application/json' \
    -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"semantic_search","arguments":{"query":"surfing at the beach","limit":3}}}' \
    "$AEM/bin/aemassets/mcp" | json "
import json,sys
for a in json.load(sys.stdin)['result']['structuredContent']['assets']: print('    semantic_search: %.2f %s' % (a['score'], a['path'].split('/')[-1]))"
fi
proof "an agent gets governed facts with provenance, filtered by the calling user's access."
pause

act "The value, by audience" "What the last seven acts showed, and who gets what."
cat <<'VALUE'
    DAM / content operations   One trusted value per fact, validation with readable messages, a
                               steward queue instead of spreadsheets (acts 2, 3).
    Information architect      The model is Turtle they own: vocabularies, taxonomy from AEM tags,
                               shapes and rules, changed without a release (acts 3, 4).
    Marketing / campaigns      Assets connected to products and campaigns with reasons; readiness
                               signals (e.g. ready for AJO) from the model (acts 2, 5).
    Experience developers      Facts in pages via tokens, no duplicate authoring, caching kept (act 6).
    AI / agent teams           Grounded, permission-filtered context with provenance over MCP (act 7).
    Architects / ops           One bundle on AEMaaCS, narrow service users, job queues with retry,
                               open standards (SKOS, SHACL, PROV-O, SPARQL) - no lock-in.

    With Adobe:  AI Search, Content Discovery agent and the AEM MCP Server work on better metadata;
                 GenAI metadata gets validation and review. SemanticDAM complements, never replaces.
VALUE
pause

act "The end" "Story: Adobe's AI finds and acts on assets; SemanticDAM makes the facts underneath trustworthy."
say "Demo data left in place for questions: $HERO, $BANNER, a component on $PAGE."
say "Rerun this script to reset it."

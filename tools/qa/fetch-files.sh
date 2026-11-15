#!/bin/bash
# Re-fetches the public test PDFs into tools/qa/files/ (git-ignored). Run from anywhere.
# The McDonald's Ireland allergen chart cannot be fetched by URL here: the owner supplied it (leaflet code
# "ROI_p09_LEAF_33204_DIGI1", the Republic of Ireland allergen guide). Ask for it again or download it from the
# McDonald's Ireland website's allergen page and save it as tools/qa/files/mcdonalds-ireland-allergen-chart.pdf.
set -e
D="$(cd "$(dirname "$0")" && pwd)/files"; mkdir -p "$D"
curl -sS -L --max-time 90 -A "Intake-QA/1.0 (personal QA of an Android app; public document)" \
  -o "$D/osu-mcdonalds-usa-nutrition-facts.pdf" https://fcs.osu.edu/sites/fcs/files/imce/PDFs/mcdonalds-nutrition-facts.pdf
file "$D/osu-mcdonalds-usa-nutrition-facts.pdf"
[ -f "$D/mcdonalds-ireland-allergen-chart.pdf" ] || echo "missing: mcdonalds-ireland-allergen-chart.pdf (see the comment at the top of this script)"

#!/usr/bin/env bash
# Ne garde dans Nexus (dépôt docker-hosted) que les N versions les plus récentes d'une image.
# Usage : ci/nexus-docker-retention.sh <nom de l'image> [N=3]
# Variables attendues : NEXUS_URL, NEXUS_USER, NEXUS_PASSWORD
set -euo pipefail

python3 - "$1" "${2:-3}" <<'PY'
import base64, json, os, sys, urllib.parse, urllib.request

name, keep = sys.argv[1], int(sys.argv[2])
base = os.environ["NEXUS_URL"].rstrip("/") + "/service/rest/v1"
auth = "Basic " + base64.b64encode(f"{os.environ['NEXUS_USER']}:{os.environ['NEXUS_PASSWORD']}".encode()).decode()

def call(method, url):
    request = urllib.request.Request(url, method=method, headers={"Authorization": auth, "Accept": "application/json"})
    with urllib.request.urlopen(request) as response:
        body = response.read()
        return json.loads(body) if body else None

items, token = [], None
while True:
    query = {"repository": "docker-hosted", "format": "docker", "name": name}
    if token:
        query["continuationToken"] = token
    page = call("GET", f"{base}/search?{urllib.parse.urlencode(query)}")
    items += page["items"]
    token = page.get("continuationToken")
    if not token:
        break

def order(version):
    return [(0, int(p), "") if p.isdigit() else (1, 0, p) for p in version.replace("-", ".").split(".")]

versions = sorted(items, key=lambda item: order(item["version"]), reverse=True)
for item in versions[keep:]:
    call("DELETE", f"{base}/components/{item['id']}")
    print(f"Supprimée de Nexus : {name}:{item['version']}")
print(f"Conservées dans Nexus : {', '.join(i['version'] for i in versions[:keep]) or 'aucune'}")
PY

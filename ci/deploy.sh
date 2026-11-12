#!/usr/bin/env bash
# Déploie une image de l'application sur le Docker sans root de l'agent Jenkins.
# Usage : ci/deploy.sh <image:version>
# - conteneur « socle-testapp » publié sur le port 8085, mémoire limitée à 512 Mo ;
# - si l'application ne répond pas, retour automatique à l'image précédente ;
# - seules les 3 images locales les plus récentes sont conservées.
set -euo pipefail

IMAGE="$1"
NAME=socle-testapp
PORT=8085
KEEP=3
REPO="${IMAGE%:*}"

previous="$(docker inspect -f '{{.Config.Image}}' "$NAME" 2>/dev/null || true)"

start() {
    docker run -d --name "$NAME" --restart unless-stopped -p "$PORT:8080" --memory 512m "$1" >/dev/null
}

healthy() {
    for _ in $(seq 1 60); do
        if curl -fsS "http://127.0.0.1:$PORT/api/ping?delay=0" >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    return 1
}

echo "Version en place : ${previous:-aucune}"
docker rm -f "$NAME" >/dev/null 2>&1 || true
start "$IMAGE"

if healthy; then
    echo "Déployé : $IMAGE"
else
    echo "ERREUR : $IMAGE ne répond pas, journaux du conteneur :" >&2
    docker logs --tail 100 "$NAME" >&2 || true
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    if [[ -n "$previous" ]]; then
        echo "Retour à la version précédente : $previous" >&2
        start "$previous"
        healthy && echo "Version précédente rétablie" >&2
    fi
    exit 1
fi

# Conserve les KEEP images locales les plus récentes (retour arrière rapide)
docker images "$REPO" --format '{{.Tag}}' | sort -V -r | tail -n +$((KEEP + 1)) | while read -r tag; do
    docker rmi "$REPO:$tag" >/dev/null && echo "Image locale supprimée : $REPO:$tag"
done
docker image prune -f >/dev/null

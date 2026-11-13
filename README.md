# socle-testapp

Application web **factice** servant de cible de validation à un socle d'automatisation web
(Selenium / Serenity BDD). Les règles de gestion n'ont aucun intérêt : l'objectif est de proposer, dans un
seul JAR autonome, un catalogue d'actions utilisateur (clics, saisies, fenêtres, iframes, fichiers…) et
des requêtes asynchrones qui éprouvent les mécanismes de synchronisation.

## Démarrage

Prérequis : JDK 21, Maven 3.9+.

```bash
mvn clean package
java -jar target/socle-testapp.jar
```

L'application écoute sur `http://localhost:8080` (propriété `server.port`).

| URL | Contenu |
|---|---|
| `/` | Page d'accueil listant les fronts disponibles |
| `/classic/` | Front **HTML + jQuery (Ajax)** — disponible |
| `/react/`, `/vue/`, `/angular/` | Fronts à venir (mêmes scénarios, mêmes identifiants) |
| `/api/...` | API REST factice (JSON) |

Tout est embarqué dans le JAR, y compris jQuery 3.7.1 (`classic/js/jquery-3.7.1.min.js`) : aucun CDN, fonctionnement hors ligne.

## Convention de localisation

* L'attribut **`id`** fait foi pour localiser les éléments ; il est stable et identique d'un front à l'autre.
* Le script `common.js` recopie chaque `id` dans **`data-testid`** (y compris pour les éléments ajoutés dynamiquement).
* Les listes dynamiques utilisent des suffixes numériques : `row-12`, `delete-12`, `suggestion-0`, `page-link-3`…

## Catalogue des pages (front classic)

| Page | Scénarios |
|---|---|
| `login.html` | Formulaire + appel asynchrone ; `test`/`test` valide, `locked` → HTTP 423, autre → 401 |
| `clicks.html` | Compteur, double-clic, clic droit, survol, bouton désactivé puis activé, bouton révélé, bouton mobile (animation), **bouton recouvert par un calque pendant 2 s**, **bouton recouvert en permanence** (calque jamais retiré : clic natif toujours intercepté, cible des branches d'erreur ; seul un clic JavaScript l'atteint), lignes à libellé identique, clic avec appel serveur |
| `forms.html` | Saisies, radios (dont radios personnalisés à input masqué), cases à cocher (« tout sélectionner » avec état indéterminé), listes natives (pays chargés par le serveur, ville dépendante, multiple avec optgroup), soumission avec validation serveur (422), champ en **lecture seule déverrouillé en différé**, **calendrier JavaScript** (non natif, week-ends indisponibles), **liste à saisie pilotée au clavier** (↓ ↑ Entrée Échap) |
| `dynamic.html` | Chargement différé, calque bloquant temporaire, **élément ré-rendu (références périmées)**, auto-complétion avec anti-rebond, polling, tâche longue avec barre de progression, service instable (2 échecs puis succès), « charger plus », notification éphémère, codes HTTP, requêtes parallèles |
| `tables.html` | Tableau serveur : recherche, tri, pagination, taille de page, sélection, suppression avec modale, édition en ligne, ajout |
| `windows.html` / `popup.html` | `window.open` (un ou deux pop-ups, ouverture différée), nouvel onglet, `postMessage` vers l'ouvreur, `alert` / `confirm` / `prompt` (dont alerte différée), redirection différée |
| `frames.html` | Iframe simple, **imbriquée**, ajoutée en différé, sans id ni nom, `srcdoc`, formulaire dans une frame |
| `files.html` | Téléchargements (txt, csv, json, zip, binaire 1 Mo, fichier lent, téléchargement par script/blob), upload (champ visible, champ **masqué**, barre de progression, zone de dépôt) |
| `widgets.html` | Onglets, accordéon animé, modale, menu au survol, liste personnalisée, glisser-déposer HTML5, curseur à la souris, **Shadow DOM**, clavier, zone défilante |
| `gestes.html` | **Appui long**, liste triable à la souris (sans HTML5), **molette** et défilement horizontal, **canvas** (dessin et cibles cliquables par coordonnées), éléments **SVG**, navigation **Tab / Maj+Tab** avec focus et perte de focus, copier-coller au clavier |
| `synchro.html` | **Défilement infini** (chargement serveur à l'approche du bas), **bandeau fixe qui recouvre** les éléments, animation longue, **flux temps réel SSE** poussé par le serveur, action disponible à partir d'un seuil, **Shadow DOM fermé** et Shadow DOM imbriqué différé |
| `calque.html` | **Calque plein écran permanent** (« chargement en cours » jamais terminé, curseur normal) au-dessus d'un bouton et d'un lien : clic natif toujours intercepté, attente de « cliquable » toujours expirée — branches d'erreur du socle ; seul un clic JavaScript passe |

## Latence et pilotage

* **Latence globale** : 300 ms par défaut (`testapp.latency.default-ms`, `testapp.latency.jitter-ms` dans `application.properties`).
* **Par appel** : tout endpoint accepte `?delay=N` (ms).
* **Par page** : `/classic/dynamic.html?delay=1500` impose 1500 ms à tous les appels API de la page.
* **À chaud** : `PUT /api/admin/settings` avec `{"defaultDelayMs": 800, "jitterMs": 400}` ; `GET /api/admin/settings` pour lire.
* **Remise à zéro** : `POST /api/admin/reset` (articles, compteurs d'échecs, tâches) — à appeler en début de scénario pour des tests reproductibles.
* **Espaces de données isolés** (exécution en parallèle) : avec le cookie `testapp-espace=<identifiant>` (lettres, chiffres, `-`, `_`, 64 caractères au plus), une session travaille dans son propre espace : articles, compteurs, tâches et latence. `POST /api/admin/reset` et `PUT /api/admin/settings` ne touchent alors que cet espace. Sans cookie : espace commun. Le cookie étant renvoyé par le navigateur à chaque appel, aucune page n'a besoin d'être modifiée. Un espace inactif depuis 1 heure est supprimé.

## API

| Méthode et chemin | Rôle |
|---|---|
| `GET /api/version` | Version déployée (`version`, `buildTime`, `commit`), affichée en permanence dans l'en-tête de chaque page (`#app-version`) |
| `GET /api/ping`, `GET /api/clock` | Santé et heure serveur |
| `GET /api/slow?delay=N` | Répond après N ms |
| `POST /api/login` | `test`/`test` → 200 ; `locked` → 423 ; sinon 401 |
| `GET /api/countries`, `GET /api/countries/{code}/cities` | Listes dépendantes (FR, DE, ES, IT) |
| `GET /api/search?q=` | Auto-complétion (fruits) |
| `GET /api/flaky?key=K&failures=2` | 503 pour les N premiers appels d'une clé, puis succès |
| `GET /api/status/{code}` | Répond avec le code HTTP demandé (200-599) |
| `POST /api/forms/submit` | Validation : `name` obligatoire, `email` avec « @ » (sinon 422) |
| `POST /api/jobs?duration=N`, `GET /api/jobs/{id}` | Tâche longue et progression |
| `GET/POST /api/items`, `PUT/DELETE /api/items/{id}` | 57 articles ; paramètres `q`, `sort`, `dir`, `page`, `size` |
| `GET /api/files/download/{txt,csv,json,zip,bin}` | Téléchargements (`delay`, `size` pour `bin`) |
| `POST /api/files/upload` | Champ multipart `file` → nom, taille, SHA-256 |
| `GET /api/stream/events?count=N&interval=ms` | Flux SSE : N événements `message` puis un événement `end` |
| `GET/PUT /api/admin/settings`, `POST /api/admin/reset` | Pilotage (limité à l'espace de l'appelant) |
| `GET /api/admin/espace` | Espace de l'appelant et nombre d'espaces isolés actifs |

## Structure

```
src/main/java/fr/socle/testapp/
  TestAppApplication.java
  config/WebConfig.java          # /classic → /classic/index.html (à étendre pour react, vue, angular)
  api/                           # contrôleurs REST
  service/                       # latence, état des scénarios, jeu de données
src/main/resources/
  application.properties
  static/index.html              # accueil
  static/classic/                # front HTML + jQuery (pages, js/, css/, frames/)
```

## Suite prévue

Ajout des fronts React, Vue.js et Angular (même catalogue, mêmes identifiants), compilés pendant
`mvn package` via `frontend-maven-plugin` et embarqués dans le même JAR sous `/react/`, `/vue/` et `/angular/`.

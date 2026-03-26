# 🚇 Transport Dashboard — Récap Projet

## C'est quoi ?
Une webapp HTML/JS affichée sur un vieux Realme GT Master Edition accroché au mur du couloir.
Elle affiche les **prochains passages en temps réel** des transports depuis 3 stations, avec un décalage "temps de marche" ajustable.

---

## Stack
- **Frontend** : HTML + CSS + JS vanilla (un seul fichier `index.html`)
- **API** : PRIM — Île-de-France Mobilités (prim.iledefrance-mobilites.fr)
- **Hébergement** : GitHub Pages
- **Affichage** : Fully Kiosk Browser sur Android (Realme GT Master Edition)

---

## Repo GitHub
- Le fichier principal s'appelle `index.html` à la racine
- GitHub Pages activé sur `main` → `/` (root)
- URL de l'app : `https://TON_USERNAME.github.io/NOM_DU_REPO/`

---

## Fonctionnalités actuelles ✅
- 3 stations switchables via des pills en haut
- Slider "temps de marche" (1-15 min) qui soustrait du compte à rebours
- Compte à rebours coloré (vert / orange / rouge selon urgence)
- Badge de ligne qui change de couleur selon la station
- Section perturbations temps réel
- Dark/Light mode avec toggle ☀️/🌙 (auto-détecte la préférence système)
- Auto-refresh toutes les 30 secondes
- Wake lock (empêche l'écran de s'éteindre)
- Clé API saisie une fois au premier lancement, stockée en localStorage

---

## Les 3 stations configurées

### 1. Laplace (station principale)
- **Ligne** : RER B
- **StopArea ID** : `STIF:StopArea:SP:43607:`
- **Line ID** : `STIF:Line::C01743:`
- **Direction** : Nord (vers Paris / CDG) — exclut Robinson, Massy, Palaiseau...

### 2. Hôpital Bicêtre
- **Ligne** : Métro 14
- **StopPoint ID** : `STIF:StopPoint:Q:490849:`
- **Line ID** : `STIF:Line::C01384:`
- **Direction** : Nord (vers Saint-Lazare / Mairie de Saint-Ouen)

### 3. Barbara
- **Ligne** : Métro 4
- **StopPoint ID** : `STIF:StopPoint:Q:483313:`
- **Line ID** : `STIF:Line::C01374:`
- **Direction** : Nord (vers Clignancourt / Gare du Nord)

---

## API PRIM — Endpoints utilisés

### Prochains passages
```
GET https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring
  ?MonitoringRef=STIF:StopArea:SP:43607:
  &LineRef=STIF:Line::C01743:
Header: apiKey: TA_CLE_API
```

### Perturbations
```
GET https://prim.iledefrance-mobilites.fr/marketplace/general-message
  ?LineRef=STIF:Line::C01743:
Header: apiKey: TA_CLE_API
```

La clé API est stockée côté client en `localStorage` sous la clé `prim_api_key`.

---

## Ce qui reste à faire / bugs connus 🐛

1. **Filtre direction Bicêtre et Barbara** : approximatif, à affiner une fois qu'on voit les vraies destinations retournées par l'API (checker la console)
2. **Perturbations** : le parsing du format SIRI peut varier selon les lignes, à tester sur métro 4 et 14
3. **IDs Bicêtre/Barbara** : à confirmer en production (les IDs viennent du dataset `arrets-lignes` IDFM mais pas filtrés sur RER B — potentiellement mauvais quai)
4. **Wake lock** : peut se désactiver si le téléphone passe en économie d'énergie, à coupler avec Fully Kiosk Browser qui gère ça mieux
5. **Fully Kiosk** : pas encore configuré sur le téléphone

---

## Setup Fully Kiosk Browser (à faire)
1. Installer **Fully Kiosk Browser** sur le Realme
2. URL de démarrage : `https://TON_USERNAME.github.io/NOM_DU_REPO/`
3. Activer **Motion Detection** (caméra frontale) pour allumer l'écran quand on passe devant
4. Activer **Keep Screen On**
5. Désactiver la barre de navigation Android
6. Mettre en mode kiosque (empêche de sortir de l'app)

---

## Instructions pour Claude Code (VS Code)

### Setup initial
```bash
# Cloner le repo
git clone https://github.com/TON_USERNAME/NOM_DU_REPO.git
cd NOM_DU_REPO

# Le projet est un fichier unique : index.html
# Pas de build, pas de dépendances, pas de package.json
```

### Workflow
```bash
# Après chaque modif
git add index.html
git commit -m "description du changement"
git push origin main
# GitHub Pages se met à jour automatiquement en ~1 minute
```

### Prompt de contexte pour Claude Code
Colle ce texte au début de chaque session Claude Code :

---

> **Contexte projet :**
> Ce projet est une webapp HTML/JS vanilla en fichier unique (`index.html`) hébergée sur GitHub Pages.
> Elle affiche les prochains passages RATP/IDFM en temps réel pour 3 stations (Laplace RER B, Hôpital Bicêtre métro 14, Barbara métro 4), destinée à être affichée en permanence sur un Android mural via Fully Kiosk Browser.
> L'API utilisée est PRIM (prim.iledefrance-mobilites.fr), authentifiée par clé API en header `apiKey`, stockée en localStorage côté client.
> Le code est en HTML/CSS/JS vanilla, pas de framework, pas de build step.
> Après chaque modification, faire un `git add index.html && git commit -m "..." && git push`.

---

## Structure du fichier index.html
```
<style>         → Tous les styles (dark + light theme via CSS variables)
<body>          → HTML de l'interface
<script>
  STATIONS      → Config des 3 stations (IDs, lignes, filtres direction)
  fetchData()   → Appels API PRIM (passages + perturbations)
  renderTrains()         → Affichage des trains
  renderPerturbations()  → Affichage des perturbations
  switchStation()        → Switch entre les 3 stations
  toggleTheme()          → Dark/Light mode
  init()                 → Démarrage (restore préférences, fetch initial)
```

# Appli Android + widget

APK qui embarque le tableau de bord (`index.html` servi par Vercel, affiché dans une WebView)
et ajoute un **widget d'écran d'accueil** : prochains passages d'un des trois arrêts.

## Le widget

- **Tous formats**, redimensionnable de 1×1 à plein écran ; l'affichage s'adapte :
  - 1 colonne : logo de ligne + minutes avant le prochain passage + heure de mise à jour ;
  - 1 ligne de haut (2×1, 4×1…) : prochain passage, les deux suivants, destination ;
  - 2 lignes et plus : liste de 2 à 6 passages (heure, destination, minutes).
- **Mise à jour au déverrouillage** du téléphone : le chargement part dès l'allumage de l'écran
  (au plus une fois toutes les 20 s).
- **Bouton ↻** pour actualiser à la main (en 1×1, tout le widget sert de bouton).
- **Heure de mise à jour** affichée (`màj 08:12`) ; préfixée de ⚠ si la dernière tentative a échoué.
- Toucher le **nom de l'arrêt** permet d'en changer ; toucher la liste ouvre l'appli.
- Suit le thème clair/sombre du téléphone.

Pas d'actualisation périodique : en dehors du déverrouillage et du bouton, le widget ne fait
aucun appel réseau.

## Installer l'APK

1. Récupérer `transport-screen.apk` :
   - depuis le site : bouton de téléchargement en bas de page, à gauche du thème (dans l'appli,
     le même bouton installe la dernière version, via le navigateur) ;
   - depuis `main` : <https://github.com/Cyba26/transport_screen/releases/download/android-latest/transport-screen.apk>
   - depuis une autre branche : onglet **Actions** → workflow **APK Android** → artefact `transport-screen-apk` (un zip qui contient l'APK).
2. L'ouvrir sur le téléphone et autoriser l'installation d'applis inconnues quand Android le demande.
3. Appui long sur l'écran d'accueil → **Widgets** → **Transport** → choisir l'arrêt.

Chaque APK de la CI a un numéro de version plus grand que le précédent et la même signature,
donc il s'installe par-dessus l'ancien sans désinstaller.

### Mise à jour au déverrouillage : à vérifier sur le téléphone

Android ne prévient plus une appli d'un déverrouillage si elle ne tourne pas. L'appli garde donc
un petit service actif tant qu'au moins un widget est posé. Il se manifeste par une notification
« Widget transport actif » en priorité minimale (rien du tout sur Android 13+, l'appli ne
demandant pas le droit de notifier).

Sur Realme / Oppo / Xiaomi, le système tue ce genre de service. Pour l'éviter :

- accepter **Autoriser** dans l'écran de choix de l'arrêt (exemption d'optimisation de batterie) ;
- Realme : Paramètres → Batterie → Transport → autoriser l'**activité en arrière-plan** et le
  **démarrage automatique**.

Si le service est quand même tué, il redémarre au prochain appui sur ↻ ou à l'ouverture de l'appli.

## Compiler soi-même

JDK 17 et Android SDK (API 35) :

```bash
cd android
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

`-PtransportBaseUrl=https://…` permet de viser un autre déploiement que
`https://transport-screen.vercel.app`.

`app/debug.keystore` est commité exprès : tous les APK ont la même signature et s'installent les
uns par-dessus les autres. C'est une clé de debug (mot de passe `android`) pour une appli perso,
pas pour le Play Store.

## Code

`app/src/main/java/io/github/cyba26/transport/` :

| Fichier | Rôle |
| --- | --- |
| `Station.java` | Les 3 arrêts (IDs, filtre de direction), miroir de `STATIONS` dans `index.html` |
| `TransportApi.java` | Appel de `/api/proxy` (SNCF pour Laplace, PRIM pour les métros) et parsing |
| `WidgetStore.java` | Arrêt de chaque widget + derniers passages connus (SharedPreferences) |
| `WidgetUpdater.java` | Chargement réseau (un seul à la fois) puis redessin des widgets |
| `WidgetRenderer.java` | Les mises en page selon la taille du widget |
| `TransportWidgetProvider.java` | Le widget : ajout, suppression, redimensionnement, bouton ↻ |
| `UnlockService.java` | Service au premier plan qui écoute le déverrouillage |
| `BootReceiver.java` | Relance le service après un redémarrage ou une mise à jour |
| `WidgetConfigActivity.java` | Choix de l'arrêt |
| `MainActivity.java` | Le tableau de bord web en plein écran |

Si les IDs ou les filtres d'un arrêt changent dans `index.html`, il faut reporter le changement
dans `Station.java`.

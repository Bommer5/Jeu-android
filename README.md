# Auto Clicker 👆

Application Android qui clique automatiquement à ta place, **sans root**.

## Fonctions

- **Mode Cadre** : un cadre flottant que tu places et redimensionnes (glisser pour déplacer,
  tirer un coin pour agrandir/réduire, ou curseurs largeur/hauteur dans l'app). Les clics tombent
  **partout dans le cadre** :
  - *Aléatoire* : chaque clic à un endroit au hasard du cadre ;
  - *Balayage (grille)* : le cadre est parcouru ligne par ligne en serpentin, chaque case est
    cliquée puis on recommence (espacement réglable de 8 à 200 dp).
- **Clics simultanés** : jusqu'à 10 doigts à la fois à chaque cycle.
- **Mode Point** : une cible déplaçable pour cliquer toujours au même endroit.
- **Vitesse** : intervalle de 1 ms à 10 s, durée d'appui réglable, rythme irrégulier (±25 %).
- **Arrêt automatique** après un nombre de clics ou une durée.
- **Panneau flottant** déplaçable : ▶/⏸, compteur de clics, changement de mode, réglages, fermeture.
  Le panneau n'est jamais cliqué, même s'il est dans le cadre.
- Tailles rapides : plein écran, moitié haute/basse, petit carré, centrer.
- Pendant les clics, le cadre devient « traversable » et affiche une onde à chaque clic.

## Comment ça marche

L'app utilise un **service d'accessibilité** (`AutoClickService`) : c'est l'API officielle
d'Android pour simuler des gestes (`dispatchGesture`) et afficher des fenêtres flottantes
(`TYPE_ACCESSIBILITY_OVERLAY`, aucune permission « superposition » nécessaire).

1. Ouvre l'app → **Activer le service** → choisis « Auto Clicker » dans Accessibilité.
2. Le panneau flottant apparaît. Place le cadre, appuie sur ▶.

```
app/src/main/java/com/bommer/autoclicker/
├── AutoClickService.kt   service d'accessibilité : fenêtres flottantes + envoi des clics
├── ClickPlanner.kt       choix des points dans le cadre (aléatoire / grille, exclusions) — testé
├── ClickConfig.kt        réglages partagés (SharedPreferences)
├── MainActivity.kt       écran de réglages (Jetpack Compose)
└── overlay/              cadre redimensionnable, cible, panneau flottant
```

## Compiler

Chaque push lance GitHub Actions : tests, APK de debug (artefact `autoclicker-debug-apk`) et AAB.

## Publication sur le Play Store

Google encadre strictement l'API d'accessibilité : il faut remplir la **déclaration
d'utilisation de l'accessibilité** dans la Play Console, avec une vidéo montrant la fonction,
et garder l'écran d'information affiché avant l'activation (déjà présent dans l'app).

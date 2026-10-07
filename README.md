# Stack Tower 🏗️

Jeu Android **hyper-casual en 3D isométrique** : des blocs glissent alternativement sur deux
axes, touchez l'écran pour les poser sur la tour. Ce qui dépasse tombe. Un placement **parfait**
déclenche un combo (note qui monte, particules, flash) et, à partir de 3 parfaits d'affilée, le
bloc s'agrandit. Plus la tour monte, plus le ciel s'assombrit vers l'espace.

## Contenu

| | |
|---|---|
| **5 modes** | Classique · Défi du jour (même tour pour tous, ×1,5 pièces) · Zen (sans fin) · Chrono (60 s, débloqué niv. 3) · Expert (débloqué niv. 6, ×2 pièces) |
| **Bonus** | Ralenti, Bouclier (annule une erreur), Départ lancé (+10 étages) |
| **Progression** | Niveaux et XP avec récompenses, 26 succès, 3 missions quotidiennes, statistiques détaillées, records par mode |
| **Fidélisation** | Cadeau quotidien sur 7 jours (série), roue de la fortune (1 tour gratuit/jour + 3 avec vidéo) |
| **Boutique** | 12 thèmes de couleurs, 5 styles de blocs (Classique, Verre, Néon, Rayures, Cristal), 6 décors animés (étoiles, bulles, neige, lucioles, confettis), bonus, packs de pièces |
| **Finitions** | Tour qui se construit toute seule derrière le menu, marqueur « RECORD » dans la tour, vue d'ensemble en fin de partie, tutoriel, pause automatique, musique et sons générés, vibrations, partage du score, demande d'avis Play Store |
| **Réglages** | Sons, musique, vibrations, particules, secousses d'écran, tutoriel, confidentialité RGPD, restauration des achats, réinitialisation |

## Comment l'app gagne de l'argent

| Source | Où | Détails |
|---|---|---|
| **Vidéo récompensée** (AdMob) | Fin de partie, roue, cadeau du jour, boutique | « Continuer », « Pièces ×2 », tours de roue bonus, doubler le cadeau, pièces gratuites. C'est le format qui rapporte le plus, et le joueur le choisit lui-même. |
| **Interstitiel** (AdMob) | Entre deux parties | Au plus 1 fois toutes les 3 parties **et** toutes les 90 s (`AdsManager`). |
| **Bannière adaptative** (AdMob) | Menus | Jamais pendant la partie. |
| **« Zéro pub »** | Accueil, boutique | Achat non consommable `remove_ads`. |
| **Packs de pièces** | Boutique | Consommables `coins_500`, `coins_1500`, `coins_5000`. |

L'économie (prix des thèmes jusqu'à 3 000 pièces, bonus consommables) crée la demande de pièces
qui alimente les vidéos et les achats. Le **consentement RGPD** (Google UMP) est géré.

## Structure

```
app/src/main/java/com/bommer/stacktower/
├── game/StackGame.kt          moteur 3D isométrique, modes, bonus (Kotlin pur, testé)
├── game/Cosmetics.kt          thèmes, styles de blocs, décors
├── game/SoundFx.kt            sons + musique synthétisés (aucun fichier audio)
├── data/Profile.kt            progression : niveaux, succès, missions, roue, boutique (testé)
├── data/ProfileStore.kt       sauvegarde JSON (DataStore + sauvegarde Android)
├── monetization/              AdMob, consentement UMP, Google Play Billing 8
└── ui/
    ├── GameRenderer.kt        rendu Canvas (blocs 3D, effets, décors, aperçus)
    ├── AppController.kt       lien interface ↔ données / pubs / achats / son
    ├── Root.kt                navigation + boucle d'animation
    ├── design/Design.kt       design system (polices, boutons 3D, cartes…)
    └── screens/               accueil, jeu, boutique, progression, roue, réglages, cadeau
```

Polices : Lilita One et Nunito (licence SIL OFL, voir `docs/licenses/`).

## Compiler

Chaque push lance GitHub Actions (`.github/workflows/android.yml`) : tests unitaires, APK de debug
et bundle release. L'APK est téléchargeable dans l'onglet **Actions** → dernier run →
*Artifacts* → `stack-tower-debug-apk`, à installer directement sur un téléphone.

En local : Android Studio (ouvrir le dossier), ou `./gradlew assembleDebug` avec le SDK Android.

## Publier et encaisser : la marche à suivre

1. **Compte Google Play Console** (25 $ une fois) : https://play.google.com/console.
   Les nouveaux comptes personnels doivent faire un **test fermé avec 12 testeurs pendant 14 jours**
   avant la production.
2. **AdMob** (https://admob.google.com) : créer l'app, puis 3 blocs d'annonces (bannière,
   interstitiel, récompensé). Copier `monetization.properties.example` en
   `monetization.properties` et y mettre vos identifiants.
   ⚠️ Ne cliquez jamais sur vos propres pubs : les identifiants de test sont utilisés par défaut
   tant que ce fichier est absent.
3. **Consentement RGPD** : dans AdMob → *Confidentialité et messagerie*, créer un message RGPD
   pour l'app (sinon aucun formulaire ne s'affichera dans l'UE).
4. **Achats intégrés** : dans la Play Console → *Monétiser* → *Produits intégrés*, créer
   `remove_ads` (ex. 2,99 €), `coins_500` (0,99 €), `coins_1500` (1,99 €) et `coins_5000`
   (4,99 €). Lier un profil de paiement.
5. **Signer** : générer une clé
   `keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias stacktower`,
   remplir `keystore.properties` (voir l'exemple), puis `./gradlew bundleRelease`
   et téléverser `app/build/outputs/bundle/release/app-release.aab`.
6. **Politique de confidentialité** : compléter `docs/privacy-policy.md` et la publier
   (GitHub Pages, Google Sites…), puis mettre son URL dans `AppController.PRIVACY_URL`.
   L'URL est obligatoire dans la fiche Play Store et dans AdMob.
7. **Fiche Play Store** : questionnaire de classification du contenu, section *Sécurité des
   données* (déclarer l'identifiant publicitaire et les données collectées par AdMob), cocher
   « contient des annonces ». Captures d'écran et une courte vidéo de gameplay aident beaucoup
   le téléchargement.
8. **`app-ads.txt`** : publier le fichier fourni par AdMob sur le site déclaré comme site du
   développeur, sinon une partie des annonceurs ne paiera pas.

## Pistes pour augmenter les revenus

- Faire varier `GAMES_BETWEEN_INTERSTITIALS` et les prix, et mesurer (Firebase Analytics / Remote Config).
- Activer la **médiation AdMob** (AppLovin, Unity, Meta…) pour faire monter les enchères.
- Classement Google Play Games, défis quotidiens, nouveaux thèmes saisonniers.
- Vérifier les achats côté serveur (Google Play Developer API) si le volume grandit.

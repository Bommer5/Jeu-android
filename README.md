# Stack Tower 🏗️

Jeu Android **hyper-casual** à un doigt : des blocs glissent de gauche à droite, touchez l'écran
pour les poser sur la tour. Ce qui dépasse tombe, le bloc rétrécit. Un placement **parfait**
déclenche un combo (son qui monte, particules) et, à partir de 3 parfaits d'affilée, le bloc
s'agrandit à nouveau. La vitesse augmente avec la hauteur.

Le genre (Stack, Tower Bloxx…) a fait ses preuves : parties courtes, rejouabilité infinie et
nombreuses occasions naturelles de montrer une pub, d'où un bon revenu par joueur.

## Comment l'app gagne de l'argent

| Source | Où | Détails |
|---|---|---|
| **Vidéo récompensée** (AdMob) | Fin de partie, boutique | « Continuer » (1 fois par partie), « Pièces ×2 », « +50 pièces gratuites ». C'est le format qui paie le mieux, et le joueur le choisit lui-même. |
| **Interstitiel** (AdMob) | Entre deux parties | Au plus 1 fois toutes les 3 parties **et** toutes les 90 s (`AdsManager`), pour ne pas faire fuir les joueurs. |
| **Bannière adaptative** (AdMob) | Menu, fin de partie, boutique | Jamais pendant la partie. |
| **Achat « Supprimer les pubs »** | Boutique | Non consommable : supprime bannières et interstitiels, garde les vidéos volontaires. |
| **Pack de 1000 pièces** | Boutique | Consommable, pour acheter plus vite les thèmes. |

Rétention : **cadeau du jour** avec série (20 → 80 pièces), **7 thèmes** à débloquer avec les
pièces, record personnel. Le **consentement RGPD** (Google UMP) est géré, ce qui est obligatoire
pour diffuser des pubs en France et dans l'UE.

## Structure

```
app/src/main/java/com/bommer/stacktower/
├── game/StackGame.kt          moteur du jeu (Kotlin pur, testé)
├── game/Themes.kt             thèmes de la boutique
├── game/SoundFx.kt            sons synthétisés (aucun fichier audio ni licence)
├── data/PlayerRepository.kt   sauvegarde (DataStore) : record, pièces, thèmes, cadeau du jour
├── monetization/AdsManager.kt       AdMob : interstitiels plafonnés + vidéos récompensées
├── monetization/ConsentManager.kt   consentement RGPD (UMP)
├── monetization/BillingManager.kt   Google Play Billing 8
└── ui/                        écrans Jetpack Compose + rendu Canvas
```

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
   `remove_ads` (ex. 2,99 €) et `coins_1000` (ex. 0,99 €). Lier un profil de paiement.
5. **Signer** : générer une clé
   `keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias stacktower`,
   remplir `keystore.properties` (voir l'exemple), puis `./gradlew bundleRelease`
   et téléverser `app/build/outputs/bundle/release/app-release.aab`.
6. **Politique de confidentialité** : compléter `docs/privacy-policy.md` et la publier
   (GitHub Pages, Google Sites…). L'URL est obligatoire dans la fiche Play Store et dans AdMob.
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

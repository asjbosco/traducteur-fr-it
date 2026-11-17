# Traducteur FR ↔ IT

Application Android qui traduit le français vers l'italien (et l'inverse), **hors ligne**.

## Fonctions

| Fonction | État |
|---|---|
| Saisie au clavier | ✅ |
| Traduction hors ligne (modèles téléchargés une fois, ~60 Mo) | ✅ |
| Historique des traductions (recherche, favoris, suppression) | ✅ |
| Saisie vocale (micro) | à venir |
| Traduction depuis une photo (OCR) | à venir |
| Lecture à voix haute (synthèse vocale) | à venir |

## Technique

- Kotlin + Jetpack Compose (Android 8.0 et plus)
- Traduction : [ML Kit Translation](https://developers.google.com/ml-kit/language/translation), modèles exécutés sur le téléphone
- Historique : base Room locale

## Installer l'APK

1. Onglet **Actions** du dépôt → dernière exécution réussie de « Construire l'APK ».
2. Télécharger l'artefact `traducteur-fr-it-apk` (fichier zip) et l'extraire.
3. Ouvrir `app-release.apk` sur le téléphone (autoriser l'installation d'applications inconnues si demandé).

Au premier lancement, appuyer sur « En Wi-Fi » pour télécharger le français et l'italien. Ensuite, tout fonctionne sans connexion.

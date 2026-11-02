# AfterLog — Journal de soirées Android

Application Android personnelle : historique de soirées, événements importés par lien Shotgun et suivi de consommation par soirée.

## Version 0.1.0

- Ajouter, modifier et supprimer des soirées manuellement
- Importer les **informations publiques** d'un événement à partir d'un lien `https://shotgun.live/...` (le titre, l'affiche et la description selon les données proposées par Shotgun)
- Saisir les détails de l'événement (date, heure, lieu, artistes, genres, organisateur, description et notes)
- Enregistrer des consommations personnelles (catégorie, nom, quantité, unité, date, heure, coût, notes)
- Voir les consommations par soirée et des statistiques globales
- Exporter et restaurer les données au format JSON

**Non disponible dans cette version :** connexion au compte Shotgun et récupération automatique de « Mes billets → Passés ». L'import par URL publique peut être partiel selon la page.

## Télécharger l'APK sur téléphone

1. Ouvrir **Releases** : https://github.com/Elikto/Afterlog/releases
2. Sélectionner **AfterLog v0.1.0 (test)** puis télécharger `AfterLog-v0.1.0.apk`.
3. Autoriser l'installation depuis le navigateur Android pour installer l'APK.

L'APK de test est compilé automatiquement via **GitHub Actions** lors des envois sur `main`. Si la release n'est pas encore disponible, vérifier l'onglet Actions.

## Confidentialité

Les soirées et consommations sont stockées localement sur l'appareil et ne sont pas publiées sur GitHub. L'application ne demande pas d'identifiants Shotgun ou Gmail. Les sauvegardes JSON exportées ne sont **pas chiffrées** : les garder privées.

**Attention :** un APK debug est signé avec une clé temporaire de GitHub Actions. Une future version peut demander une désinstallation pour être installée : exporter la sauvegarde JSON avant. Projet expérimental à tester sur appareil Android.

## Développement

- Android SDK 36, Java 17, AGP 8.13.2, Gradle 8.13
- Compilation : `gradle :app:assembleDebug`
- Fichier : `app/build/outputs/apk/debug/app-debug.apk`

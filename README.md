# AfterLog — Journal personnel de soirées Android

Version de test **0.2.0**.

## Téléchargement de l'APK

https://github.com/Elikto/Afterlog/releases

**Pour mettre à jour depuis l'APK de test 0.1.0** : exporter les données au format JSON dans les réglages AVANT toute désinstallation. Les APK de test GitHub Actions peuvent être signés avec des clés différentes ; si Android refuse la mise à jour, désinstaller la 0.1.0, installer la nouvelle APK puis réimporter la sauvegarde.

## Ce que permet la version 0.2

- **Connexion au site officiel Shotgun via un navigateur WebView intégré** (adresse e-mail et mot de passe saisis sur la page Shotgun, jamais dans un formulaire AfterLog).
- Depuis Shotgun, ouvrir **Mes billets → Passés**, puis cliquer **Importer les visibles** pour récupérer les liens d'événements présents dans le document, dans la mesure où Shotgun les fournit.
- Ou ouvrir une fiche d'événement Shotgun et utiliser **Importer cet événement** pour importer titre, affiche, description et éventuelles données structurées publiées sur la page.
- Ajouter ou modifier des soirées manuellement, noter des consommations, heures, quantités et dépenses, consulter des statistiques, exporter/restaurer en JSON.
- Se déconnecter depuis le navigateur Shotgun intégré.

## Limites

- **Ce n'est pas une intégration API officielle Shotgun ni une synchronisation automatique de tout le compte.** L'accès complet à l'historique privé ne dispose pas d'une API participant publiquement documentée.
- La récupération depuis les liens visibles dépend de la structure du site Shotgun et peut donner 0 résultat. Aucune requête HTTP automatisée en boucle n'est envoyée pour contourner un 429.
- L'authentification WebView peut ne pas fonctionner si Shotgun ou son système anti-bot bloque les navigateurs intégrés ; dans ce cas, utiliser la page Shotgun normale et l'ajout manuel.
- **Ne pas importer de QR codes ou de billets personnels** : l'application lit seulement les métadonnées publiques d'une fiche d'événement ou les liens visibles à la demande.
- Les données de consommation restent **locales**. Les exports JSON sont **non chiffrés** et doivent rester privés.
- La session du navigateur intégré est sauvegardée par WebView sur le téléphone. Le bouton Déconnecter efface ces cookies ; l'application ne les lit pas.

## Technique

Java 17, Gradle 8.13, Android SDK 36, AGP 8.13.2, Android 8+.
Compilation et publication automatique de l'APK avec GitHub Actions.

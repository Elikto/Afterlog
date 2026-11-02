# AfterLog — Application Android

Version expérimentale **0.3.0**, avec bouton de mise à jour intégré.

## Installation
- APK : https://github.com/Elikto/Afterlog/releases
- Code : https://github.com/Elikto/Afterlog
- Une fois installé, utiliser **↻ Rechercher une mise à jour** depuis l'accueil ou les réglages.
- AfterLog vérifie les **Releases GitHub**, propose l'APK, le télécharge et ouvre le programme d'installation Android. Android demande toujours l'approbation de l'utilisateur.

## Fonctionnement du système de mise à jour
- Utilise l'API GitHub `/repos/Elikto/Afterlog/releases` (les préversions sont incluses).
- Compare les versions et utilise uniquement les fichiers APK du dépôt officiel.
- Télécharge via HTTPS, vérifie l'empreinte SHA-256 si GitHub la fournit.
- Valide l'identifiant du paquet et le numéro de version, puis **vérifie le certificat de signature installé avant de proposer la mise à jour**.
- La mise à jour n'installe rien silencieusement : Android doit confirmer l'installation.
- Aucun APK ni donnée de consommation n'est envoyé à un serveur externe.

### IMPORTANT : signature de l'APK de test
Les versions 0.1 et 0.2 ont été signées avec des **clés de debug temporaires de GitHub Actions**. La nouvelle 0.3.0 utilise encore une signature debug provisoire. Android **refusera l'installation par-dessus une version signée différemment**. Avant de passer de la 0.2.0 à la 0.3.0 :
1. Dans AfterLog 0.2, exporter les données depuis **Réglages et sauvegarde → Exporter toutes mes données (JSON)** et conserver ce fichier.
2. Télécharger l'APK 0.3 sur GitHub dans le navigateur.
3. Désinstaller l'ancienne AfterLog (cela efface les données locales de l'application).
4. Installer la 0.3, puis importer la sauvegarde JSON.

**Pour rendre les futures mises à jour réellement installables en un clic**, configurer une clé de signature Android *permanente et privée* dans les secrets de GitHub Actions, et l'utiliser pour toutes les releases suivantes. Ne jamais publier la clé privée ni son mot de passe dans ce dépôt public. La mise à jour intégrée détecte et bloque les signatures incompatibles jusqu'à cette configuration.

## Autres fonctions
- Navigateur Shotgun intégré pour ouvrir la session sur le site officiel.
- Import volontaire des pages événements et liens visibles, selon le DOM de Shotgun.
- Ajout manuel de soirées, suivi des consommations, des heures et du coût.
- Statistiques simples, export/import JSON.

La synchronisation privée de l'historique de comptes Shotgun n'est pas officiellement prise en charge.

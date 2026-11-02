# AfterLog — Journal personnel de soirées Android

## Version 0.4.0
La version 0.4 remplace l'import Shotgun limité aux titres par une recherche plus complète dans le navigateur Shotgun connecté.

### Importer mes billets passés
1. Ouvrir **Connexion Shotgun · Mes billets**.
2. Se connecter sur le site Shotgun.
3. Aller dans **Mes billets → Passés** et afficher les événements souhaités.
4. Appuyer sur **Tout importer + détails**.
5. AfterLog ouvre **une par une** les pages correspondant aux liens visibles. Si un lien mène à une fiche de billet, l'application essaie de suivre **Voir l'événement** (ou **Plus → Voir l'événement**).
6. Pour chaque fiche publique accessible, l'application récupère selon ce que Shotgun expose : **nom, affiche, description, date, heures de début et de fin, adresse, ville, lieu, organisateur, artistes et genres musicaux**.

La progression est affichée en haut du navigateur. Tu peux arrêter l'import. Si Shotgun renvoie **HTTP 429**, l'import s'arrête sans multiplication des tentatives ; les fiches déjà collectées peuvent être enregistrées.

### Compléter mes soirées déjà importées
L'import **ajoute les champs manquants** dans les soirées identifiées par leur URL Shotgun ou un titre et une date correspondants. Il **ne supprime ni les soirées, ni les notes personnelles, ni les consommations**. Il n'écrase pas les informations déjà saisies.

### Limites
La structure des pages Shotgun peut changer. L'accès aux pages privées et au bouton **Voir l'événement** dépend de ce que le site permet au navigateur connecté. Les événements sans lien accessible ne seront pas importés automatiquement ; ils peuvent être ajoutés manuellement. Les billets et QR codes ne sont pas extraits.

Ce n'est pas une synchronisation API officielle : il n'existe pas d'API participant officiellement documentée ici. Ce fonctionnement doit encore être testé sur téléphone Android connecté à Shotgun.

## Installation et mises à jour
Les APK de test sont sur https://github.com/Elikto/Afterlog/releases .

L'application comprend un bouton « Rechercher une mise à jour », mais **les builds de test sont signés avec une clé debug temporaire**. Pour passer de la 0.3 à la 0.4, si Android indique que le certificat est incompatible :
- **Avant la désinstallation**, exporter une sauvegarde JSON dans AfterLog → Réglages et sauvegarde.
- Installer l'APK 0.4 depuis les Releases.
- Réimporter le fichier JSON.

Pour permettre de vraies mises à jour directes sans désinstallation, une clé de signature permanente doit être configurée en secret dans GitHub Actions, jamais dans ce dépôt public.

## Données et confidentialité
AfterLog garde les notes et consommations **localement** sur le téléphone. L'export JSON n'est pas chiffré ; conserve-le à l'abri. Le navigateur intégré ne récupère pas le mot de passe, les cookies, les QR codes ou les détails des billets. Il utilise les pages Shotgun une à une à la demande de l'utilisateur.

## Compilation
Android SDK 36, Java 17, Gradle 8.13, Android Gradle Plugin 8.13.2.
Le workflow GitHub Actions compile automatiquement et crée une Release `AfterLog-v0.4.0.apk`.

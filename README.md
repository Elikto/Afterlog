# AfterLog — Journal de soirées Android

## Version 0.6.0 (bêta)

### Connexion Shotgun, événements à venir et passés
- Une fois connecté au site Shotgun dans le navigateur interne, AfterLog essaie de détecter la session et de lancer **une synchronisation en service Android de premier plan**.
- L'outil lit les liens d'événements disponibles dans **Mes billets**, y compris les onglets à venir et passés, suit « Voir l'événement » et extrait les métadonnées accessibles : affiche, date, heures, description, lieu, artistes, genres et organisateurs.
- La synchronisation peut continuer après retour à l'accueil. Elle affiche **une barre de progression et une estimation** après récupération des premiers événements. Une notification Android permet d'arrêter la tâche.
- Les soirées déjà importées sont complétées **sans remplacer les valeurs personnelles ni effacer les consommations**.
- Un contrôle supplémentaire est planifié avec **WorkManager**, à un intervalle minimal de 15 minutes tant que la synchronisation automatique est activée, avec une connexion réseau. Cela **n'est pas instantané** : Android peut retarder ou empêcher l'exécution. À chaque contrôle, seules un nombre limité de fiches manquantes sont inspectées.
- En cas de blocage **HTTP 429**, les vérifications périodiques sont suspendues plusieurs heures : aucun contournement de la limitation Shotgun.

### Soirées en cours
Les événements importés, même futurs ou en cours, peuvent être ouverts dans AfterLog et utilisés pour enregistrer des consommations avec heure précise ou la case « Pendant toute la soirée ». Les saisies restent sur le téléphone.

### Fiche événement
Affiche large, titre et organisateur, dates et horaires, lieu, section À propos, line-up, genres musicaux et localisation ; journal personnel en dessous. **Il s'agit d'une adaptation dans AfterLog**, pas d'une reproduction exacte en tous points de l'interface propriétaire Shotgun.

### Confidentialité
Pas de lecture de mot de passe, QR code ni extraction privée des billets. Le navigateur Android conserve la session sur l'appareil. La synchronisation n'envoie pas le journal personnel vers Shotgun ou GitHub. L'export JSON reste non chiffré.

### Fiabilité à tester
La navigation Shotgun repose sur la structure des pages web et non sur une API participant officielle. La synchronisation au second plan peut être perturbée par des modifications du site, la fin de session ou les restrictions d'énergie d'Android. Ne pas considérer le résultat comme une synchronisation garantie.

### Télécharger ou mettre à jour
Version Android compilée et publiée sur https://github.com/Elikto/Afterlog/releases .
Le bouton **Rechercher une mise à jour** détecte la nouvelle version. **Attention : les builds précédents sont signés avec une clé de debug temporaire** ; Android peut refuser la mise à jour en place. Avant toute désinstallation, exporter les données JSON (Réglages et sauvegarde), puis les restaurer après installation. Une clé de signature permanente privée doit être configurée dans GitHub Actions pour les futures mises à jour sans désinstallation.

### Contribuer
Java 17 · Android SDK 36 · WorkManager 2.12.0 · Gradle 8.13 · AGP 8.13.2.

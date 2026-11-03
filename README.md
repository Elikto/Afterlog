# AfterLog — Application Android

## Nouveautés de la version 0.8.0

- **Ecstasy : modification des fractions.** Choisis Quart, Demi ou Entier, règle le compteur, puis clique sur **✓ OK**. Pour corriger « 2 quarts », retouche Quart : le compteur affiche 2, puis tu peux changer la quantité et valider. Les valeurs sont remplacées, jamais additionnées par erreur.
- **Correction de la connexion Shotgun :** la recherche des billets s'effectue dans le navigateur intégré **visible et connecté**, afin d'éviter l'échec de la navigation automatique dans un navigateur en arrière-plan. En cas d'impossibilité, tu peux ouvrir « Mes billets → Passés » manuellement puis choisir **Lire mes billets passés**.
- **Accueil :** message d'échec de synchronisation compact et bouton pour le masquer.

**À tester :** le parcours Shotgun dépend de l'interface du site et n'est pas une intégration officielle garantie. Les APK bêta utilisent encore des certificats de signature temporaires : sauvegarde ton journal JSON avant toute éventuelle réinstallation.


**Version 0.8.0 (bêta expérimentale)**

[![Télécharger AfterLog](https://img.shields.io/badge/T%C3%89L%C3%89CHARGER-AfterLog%20Android-7c3aed?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Elikto/Afterlog/releases/download/v0.8.0-debug/AfterLog-v0.8.0.apk)

## Une page d'accueil plus simple
- Un petit bouton **↻ MAJ** à gauche du titre.
- Un **engrenage ⚙** à droite pour les paramètres et sauvegardes.
- Les boutons **＋ Soirée**, **Shotgun**, **RDR** et **Statistiques**.
- Les soirées affichées en cartes compactes avec leur affiche.
- La carte de synchronisation disparaît après une synchronisation réussie sans erreur. En cas d'échec, elle reste visible avec les événements non importés et la possibilité de réessayer.

## Synchronisation Shotgun
La session ouverte dans le navigateur AfterLog peut être réutilisée. Après connexion, AfterLog tente de consulter **Mes billets → Passés**. Une liste de cartes d'événements apparaît avec **toutes les cases cochées par défaut**, et permet de décocher certaines soirées avant l'import.

L'importation des informations Shotgun se fait après la sélection, via un service Android présentant une barre de progression et une estimation lorsque celle-ci est disponible. Les échecs sont détaillés (raison, bouton réessayer, ajout manuel). Les contrôles périodiques prévus dépendent des limitations d'Android et de Shotgun.

> **Limite importante :** AfterLog n'utilise aucune API participant officielle de Shotgun. La détection de session et l'extraction des pages peuvent échouer si le site change ou bloque son navigateur ; le résultat n'est pas garanti.

## Des fiches événement plus complètes
- **Journal personnel placé en haut de chaque événement.**
- Informations publiques : affiche, titre, « À propos », date, horaires, adresse, line-up et genres lorsque Shotgun les expose.
- **Artistes :** affichage de leur photo si elle existe dans la fiche ; clic sur le profil Spotify vérifié ou, à défaut, sur une recherche Spotify (sans promettre de correspondance exacte).
- **Galerie de photos personnelles** pour chaque soirée.
- **Nouvel éditeur visuel** de création de soirée, organisé par sections et incluant l'ajout de photos multiples.

## Consommations, rapides à renseigner
- Interface tactile de pictogrammes par boisson ou substance et compteur + / −.
- Horaires **Exacte**, **Entre deux heures** et **Toute la soirée**.
- Ecstasy : compteur séparé pour **quart, demi et entier** (par exemple **2 demi + 1 quart**).
- Modes de prise documentés, y compris **Autre** avec saisie libre.
- **Quantité mesurée facultative** et unité adaptée (mg, g, buvards, gouttes, mL selon la catégorie), sinon simple nombre de prises.
- Option subjective **Petit / Moyen / Grand** (sans estimation de dosage pharmacologique).
- Les données restent locales et peuvent être modifiées.

## RDR · Réduction des risques
Une section séparée de l'accueil contient les signes d'urgence, les numéros d'aide, des informations synthétiques sur les substances et les liens vers leurs fiches de référence. L'annuaire est initialisé sur **Arles**, avec des contacts vérifiés (CSAPA La Maison Jaune, CAARUD mobile Camargue, Mas-Thibert) et un lien vers **Analyse ton prod**. L'adresse peut être changée manuellement **sans accès GPS** ; la recherche des structures d'autres villes ouvre un annuaire ou une carte externe.

Les informations de prévention sont résumées d'après des ressources spécialisées et ne déterminent ni la composition réelle ni le dosage d'une substance.

## Données et installation
- Le journal est enregistré localement, pas transmis à GitHub ou Shotgun.
- **Attention : la sauvegarde JSON n'inclut pas encore les fichiers JPEG des photos personnelles.** Garde les photos originales séparément.
- Le bouton MAJ vérifie les versions sur GitHub. Les APK de test GitHub Actions sont encore signés avec une **clé debug temporaire** : la mise à jour en place peut être refusée, malgré le bouton.
- Avant toute désinstallation, **exporte le journal JSON**, puis restaure-le après installation. Une signature Android permanente est nécessaire pour garantir les futures mises à jour sans désinstallation.

Projet Android Java · WorkManager · GitHub Actions · Android 8.0+.

**AfterLog est indépendant de Shotgun et n'est pas une application officielle Shotgun.**

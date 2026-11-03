# NØCTIS v0.17.0 — Nouvelle identité

AfterLog devient **NØCTIS**, le journal personnel des soirées, festivals et souvenirs.
La nouvelle identité conserve l'icône rave/cyberpunk rose et cyan. Le nom de l'application
(Android et interface), les notifications et les dialogues personnalisés adoptent
NØCTIS. L'identifiant Android `fr.afterlog.app` et les chemins de mise à jour
historiques restent inchangés pour préserver les installations et les données.

**Mise à jour :** le numéro de version passe à 0.17.0 (code 17).
L'APK stable reste distribué à l'adresse historique `AfterLog.apk`
par compatibilité avec les versions déjà installées ; ce nom technique
ne correspond plus au nom affiché par l'application.

---

# Historique — AfterLog v0.16.0 — Risques visibles et fiche soirée épurée

- **Cartes des soirées :** les pictogrammes des substances restent affichés sans légende ; un petit **⚠ orange** apparaît quand le journal contient une situation de vigilance documentée, **⚠ rouge** uniquement lorsque l'horaire déclaré est compatible avec un chevauchement de GHB/GBL et d'alcool. Un simple enregistrement sans horaire ne suffit pas à attribuer un danger rouge.
- Un appui sur le triangle affiche la **fiche RDR détaillée**, avec les sources officielles. Ces couleurs **ne constituent ni diagnostic ni mesure de surdosage**.
- **Fiche de soirée :** affiche, titre, date et lieu en premier ; ensuite les bulles de prises, la petite vigilance RDR et les informations détaillées.
- **Journal :** toujours une **bulle ronde +**, même si aucune consommation n'est enregistrée. Cinq petits cercles par rangée, avec les détails en fenêtre moderne au toucher.
- **Catégorie « Sans alcool »** masquée de la saisie ; les éventuels anciens enregistrements sont conservés et restent visibles.
- **Galerie photos/vidéos et fenêtre de modification stylisée :** conservées telles qu'introduites en 0.15, sans revenir au sélecteur de fichiers.
- **Profils Spotify directs :** ajout de deux liens vérifiés supplémentaires, **A5KM** et **Hortense de Beauharnais** ; les autres liens déjà confirmés restent actifs. Pour **RDØ**, l'identification n'est pas certifiée : l'application permet de renseigner manuellement l'URL d'artiste, sans renvoyer vers une recherche ou Shotgun.

**Limites à noter :** il n'est pas possible de déduire une dose toxique d'un simple nombre de verres ou d'un adjectif « petit/moyen/grand », ni de garantir les portraits Shotgun depuis une compilation. Une application installée avec une signature différente ne pourra pas être mise à jour en place.

Sources RDR : [GHB/GBL et alcool, Drogues Info Service](https://www.drogues-info-service.fr/Actualites/Prise-de-GBL-GHB-en-soiree-savoir-limiter-les-risques) ; [MDMA, Drogues Info Service](https://drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Ecstasy-MDMA).

---

# AfterLog v0.15.0 — Galerie, bulles et Spotify

**Mises à jour de cette version :**

- Le bouton **+ Médias** ouvre le sélecteur de **photos et vidéos** Android avec sa galerie et ses albums, à la place de l'explorateur de fichiers. Sélection multiple.
- **Bulles du journal** : vrais cercles de ~56–72 dp, cinq par rangée sur écran mobile, avec un court résumé. Les détails complets et l'heure restent visibles au toucher.
- **Fiche de prise moderne** : fenêtre sombre et arrondie, liste des prises détaillées, actions Modifier / Supprimer avec confirmation personnalisée.
- **Artistes Spotify** : tous les clics dans une carte visent désormais uniquement un profil **Spotify direct** confirmé, jamais Shotgun ou une recherche. Liens directement confirmés pour Perceval, Acidpach, Amygdala (DJ espagnol), Lekkerfaces et ZAPRAVKA.
- Si un lien n'a pas été identifié avec certitude, la carte propose d'associer une adresse directe d'artiste Spotify plutôt que d'ouvrir une recherche ou de pointer vers le mauvais homonyme. La résolution automatique MusicBrainz reste active.
- L'extraction des portraits Shotgun demeure indépendante et peut toujours être incomplète selon le site : il n'est pas garanti que les huit portraits soient présents.

**Sauvegardes et mises à jour :** l'APK est conçu pour être signé avec la clé Android permanente configurée dans les secrets du dépôt. Conserver les photos et vidéos originales : elles ne sont pas comprises dans l'export JSON.

# AfterLog — Android

## Version 0.14.0 — Correctifs de l'interface et des artistes

- **Bulles de prises plus petites** : trois vrais cercles de diamètre identique par rangée, bouton ＋ dans la dernière cellule.
- **Fiche RDR sur mesure** : une fenêtre sombre arrondie, défilante et cohérente avec AfterLog ; détails des vigilances, liens de sources, accès au suivi.
- **Line-up Shotgun** : lecture élargie aux cartes interactives, aux images différées, aux arrière-plans CSS et aux données publiques structurées de la page.
- **Récupération automatique des portraits** lorsqu'une fiche événement Shotgun contient des artistes sans photos, avec une seule tentative temporaire afin d'éviter les requêtes répétées.
- **Spotify** : vérification du nom officiel et des relations musicales publiques, avec un vrai lien direct lorsqu'il est confirmé ; sinon recherche Spotify. En l'absence de photo Shotgun, affichage d'un portrait provenant d'un profil Spotify vérifié, s'il est disponible.
- **Ce qui reste à confirmer sur ton téléphone :** l'extraction des images Shotgun et la correspondance réelle des comptes Spotify. Le fonctionnement n'est pas prouvé par la compilation.

La mise à jour est construite via la **clé permanente GitHub** configurée précédemment.

## Nouveautés 0.13.0
- **Bulles en vrais cercles** et bouton + de même taille.
- **Logos des produits** directement sur chaque carte de soirée.
- **Partage Android** après importation de médias, notamment vers les applications et messageries installées, avec option Story Instagram si elle est prise en charge.
- **Photos des artistes Shotgun** : extraction élargie aux cartes en images d'arrière-plan et aux images différées ; résultat à vérifier sur l'événement réel.
- **RDR compact** : pastille d'avertissement, sans alerte automatique pour seulement deux verres saisis.
- **Graphiques de suivi** en courbes, colorés par catégorie de substance.
- **Mises à jour** : prise en charge de la signature permanente via un secret GitHub.

[Lire le cahier des charges et les limites](docs/APPLICATION_V013.md).

# AfterLog — Application Android

## Mise à jour 0.12.0 : bouton MAJ et signature permanente

Le système de mise à jour reprend le circuit **CyberQuiz** : une release stable fixe `apk-latest`, un fichier de métadonnées `update.json` avec SHA-256, et une clé de signature permanente.

**Important :** la clé privée n'est pas publiée. Elle doit être placée **une seule fois** dans le secret GitHub Actions `AFTERLOG_SIGNING_BUNDLE` pour activer les mises à jour installables. [Configuration et consignes de migration](SIGNING_SETUP.md).

Avant cette configuration, aucune release stable n'est annoncée au téléphone. Android refusera toujours une mise à jour sur une ancienne installation signée avec une clé différente. Les consommations et les médias doivent être préservés avant une éventuelle migration.


## Nouveautés de la version 0.12.0

- **Soirées visibles sans redémarrer :** l'accueil repère chaque nouvel événement enregistré par Shotgun et rafraîchit automatiquement ses cartes pendant la synchronisation.
- **Événements décochés :** ils ne comptent pas comme échecs. Dès que toutes les soirées choisies sont présentes, le bandeau de fin de synchronisation disparaît. Les vrais problèmes d'import restent consultables.
- **Artistes façon Shotgun :** portraits ronds (jusqu'à trois par ligne), noms sous les photos et pictogramme Spotify.
- **Spotify vert :** ouvre directement le profil d'artiste si un lien Spotify a été explicitement fourni par Shotgun et qu'un contrôle du nom et de la similarité des vignettes est concluant.
- **Spotify violet :** ouvre une recherche si aucun lien direct fiable ne peut être confirmé. Un nom semblable ne suffit pas à valider un profil.
- Le réglage des fractions **Quart, Demi, Entier**, avec confirmation **✓ OK** et correction, reste inclus.



- **Ecstasy : modification des fractions.** Choisis Quart, Demi ou Entier, règle le compteur, puis clique sur **✓ OK**. Pour corriger « 2 quarts », retouche Quart : le compteur affiche 2, puis tu peux changer la quantité et valider. Les valeurs sont remplacées, jamais additionnées par erreur.
- **Correction de la connexion Shotgun :** la recherche des billets s'effectue dans le navigateur intégré **visible et connecté**, afin d'éviter l'échec de la navigation automatique dans un navigateur en arrière-plan. En cas d'impossibilité, tu peux ouvrir « Mes billets → Passés » manuellement puis choisir **Lire mes billets passés**.
- **Accueil :** message d'échec de synchronisation compact et bouton pour le masquer.

**À tester :** le parcours Shotgun dépend de l'interface du site et n'est pas une intégration officielle garantie. Les APK bêta utilisent encore des certificats de signature temporaires : sauvegarde ton journal JSON avant toute éventuelle réinstallation.


**Version 0.11.0 (bêta expérimentale)**

[![Télécharger AfterLog](https://img.shields.io/badge/T%C3%89L%C3%89CHARGER-AfterLog%20Android-7c3aed?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Elikto/Afterlog/releases/download/v0.17.0/NOCTIS-v0.17.0.apk)

## Mise à jour 0.11.0 — Galerie et Suivi

**Journal de soirée**
- Les consommations s'affichent en bulles de **dimensions identiques**, sur deux colonnes.
- Les informations longues sont tronquées dans la bulle et restent accessibles au clic.
- Le bouton **＋** occupe la cellule immédiatement après les bulles.
- Après l'enregistrement d'une prise, le journal se reconstruit depuis la mémoire locale automatiquement : plus besoin de quitter et rouvrir la soirée.

**Galerie privée**
- Un seul bouton **＋ Médias** pour photos et vidéos, sur les fiches et dans l'éditeur.
- Une visionneuse **plein écran** permet de faire défiler les photos et vidéos ; zoom tactile sur photo et commandes de lecture Media3 pour les vidéos.
- Vidéos importables dans divers conteneurs (MP4, MOV, WebM, MKV, AVI, WMV, MPEG, TS, etc.), selon les décodeurs disponibles sur Android. Le lecteur propose une ouverture dans une application externe compatible si nécessaire.
- **Chromecast :** un raccourci permet de passer le fichier à une application compatible ; la diffusion Cast native de médias stockés localement *n'est pas encore intégrée* ni garantie. La lecture directe de tous les formats n'est pas techniquement garantie.
- 30 vidéos maximum par soirée, 1 Go par vidéo. **L'export JSON ne contient pas les médias** : conserver les originaux indépendamment.

**Mon Suivi**
- « Statistiques » est remplacé par **Suivi** ; périodes jour, semaine, mois et année avec navigation entre périodes, graphiques simples et répartition par boisson / substance.
- Les alertes RDR signalent certaines situations consignées (verres d'alcool au-delà des repères, prises répétées, mélanges signalés le même jour). Elles donnent les **sources vérifiables** et les numéros **15, 112 et 0 800 23 13 13**.
- Pour l'alcool : repères officiels Santé publique France de 2 verres **standards** par jour et 10 par semaine. Une quantité exprimée en « verres » dans le journal n'est pas automatiquement un verre standard.
- Pour les autres substances : **aucune limite sûre en mg inventée**. Une prise notée « petit/moyen/grand » ou « toute la soirée » ne permet pas un diagnostic. Les prises répétées ou rapprochées déclenchent seulement des messages de vigilance selon les données horaires disponibles, et ne constituent pas un seuil de sécurité.
- En cas de perte de conscience, douleur thoracique, respiration anormale ou convulsions, appeler immédiatement les secours.

Sources : [Santé publique France](https://www.santepubliquefrance.fr/alcool/comment-reduire-les-risques-de-la-consommation-dalcool), [Drogues Info Service — Ecstasy/MDMA](https://drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Ecstasy-MDMA), [Kétamine](https://drogues-info-service.fr/Tout-savoir-sur-les-drogues/Le-dico-des-drogues/Ketamine) et [Drogues Info Service](https://www.drogues-info-service.fr/).


## Nouveautés v0.11.0 — Journal compact, vidéos et synchronisation

- **Synchronisation Shotgun :** seuls les échecs des soirées réellement cochées restent visibles. Les billets décochés volontairement ne maintiennent plus le bandeau « fiche non récupérée ».
- **Journal de soirée :** petites bulles avec substance, quantité, taille, mode et heure éventuelle ; rien n'est affiché pour l'heure quand « Toute la soirée » est sélectionné. Un petit bouton ＋ remplace le texte d'ajout lorsqu'il existe déjà des prises.
- **Détails :** toucher une bulle ouvre les renseignements, le bouton Modifier et le bouton Supprimer avec confirmation.
- **Saisie :** fenêtre de consommation superposée à la soirée, plutôt qu'une nouvelle page plein écran.
- **Comptage :** différentes prises d'une même substance peuvent être cumulées par taille et mode (ex. 2 petits para et 1 grand para), avec quantité mesurée toujours facultative. Pas de dosage inventé à partir de la taille.
- **Modes de prise :** révisés d'après Drogues Info Service, notamment pour la kétamine (voie nasale prédominante) ; le champ « Autre » reste disponible.
- **Galerie :** photos agrandissables et vidéos personnelles importées, consultables et lisibles dans AfterLog (jusqu'à 12 vidéos de 200 Mo par soirée, selon formats Android).
- **Artistes :** tentative élargie d'extraction des portraits Shotgun et recherche prudente d'un profil Spotify via MusicBrainz. Une comparaison du nom et de la photo est nécessaire avant d'afficher un lien direct. Sinon l'icône violette ouvre une recherche.

**Limite importante :** ni l'extraction Shotgun ni la recherche Spotify n'ont pu être validées directement sur ton compte. Certaines photos ou correspondances peuvent encore manquer.

### Mises à jour en place : signature Android permanente

Le bouton MAJ sait déjà vérifier GitHub, télécharger l'APK et ouvrir l'installation Android. Pour qu'une nouvelle version s'installe **par-dessus une ancienne**, Android exige la **même clé de signature**. Le workflow GitHub accepte désormais quatre secrets de signature (AFTERLOG_KEYSTORE_BASE64, AFTERLOG_KEYSTORE_PASSWORD, AFTERLOG_KEY_ALIAS, AFTERLOG_KEY_PASSWORD), mais **ces secrets ne sont pas encore configurés**.

Tant que la configuration n'est pas achevée, les APK de test sont signés avec une clé debug temporaire. Une installation à signature différente nécessite une migration unique avant de permettre les mises à jour futures en place. Ne jamais publier la clé privée dans un dépôt public.

**Sauvegarde :** l'export JSON ne comprend pas encore les fichiers photo et vidéo. Conserve leurs originaux séparément avant toute désinstallation.


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

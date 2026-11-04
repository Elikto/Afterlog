# NØCTARIA 0.18.9 — Synchronisation des soirées et artistes

- Connexion Shotgun Android : après authentification, le navigateur disparaît. Un traitement discret identifie les événements des trois catégories de billets, puis une liste avec miniature d'affiche et case cochée par défaut permet de retenir les soirées souhaitées.
- Après validation, l'accueil présente la barre de progression et le délai restant estimé. Chaque événement et ses fiches artistes sont suivis ensemble. 100 % n'apparaît que pour un import complet : les pages Spotify ou portraits manquants sont signalés comme incomplets.
- Nouvelle page « Tous les artistes » dans Android et iPhone PWA : artistes regroupés de toutes les soirées, avec portraits et liens Spotify disponibles.
- La rubrique « À propos » des événements Shotgun est importée lorsqu'elle est disponible, sans écraser les souvenirs personnels.
- Un lien vers le lecteur musical officiel d'un artiste n'apparaît que si une URL de morceau ou de musique publique est présente. Aucune piste audio n'est copiée.
- La PWA iPhone conserve sa limitation d'accès direct aux billets personnels Shotgun.

---

# NØCTARIA 0.18.8 — Shotgun invisible, artistes intégrés et villes proposées (Android + iPhone)

- **Android :** après connexion sur le site officiel Shotgun dans NØCTARIA, trois choix : **Tout synchroniser**, **Choisir des soirées** ou **Plus tard**.
- Si tu synchronises, la lecture des trois catégories de billets (tous, à venir, passés) s'effectue dans un **service Android en arrière-plan**, sans navigation visible dans le navigateur de connexion. La progression apparaît sur l'accueil et dans une notification Android.
- **Tout synchroniser :** l'import des événements commence après leur découverte. **Choisir des soirées :** l'application propose une liste à cocher une fois les billets trouvés ; seules les soirées retenues sont importées. Les exclusions sont conservées pour les recherches périodiques.
- **Les artistes sont vérifiés pendant cette même synchronisation**, à partir des line-ups collectés sur Shotgun et des liens Spotify publics. La synchronisation attend la fin des enrichissements en cours avant d'être marquée terminée. Un artiste sans profil Spotify fiable reste sans lien.
- **Android et PWA iPhone :** dans « Structures et associations », saisir une ville déclenche des **suggestions de communes françaises** issues de l'IGN. Après sélection et validation, les CAARUD, CSAPA et autres lieux cartographiés sont recherchés autour de la **nouvelle ville**, triés par distance. Aucun GPS demandé.
- **Identité : NØCTARIA** sur les écrans, le suivi et les noms de sauvegardes. L'identifiant Android, les clés de stockage et les URL d'anciens téléchargements restent techniquement inchangés pour préserver les installations et les journaux.
- **Limites :** la synchronisation d'un compte personnel Shotgun via la PWA iPhone n'est pas opérationnelle sans accès autorisé. Le comportement Android dépend des pages Shotgun ; tester sur un compte réel est nécessaire. L'annuaire OSM n'est pas exhaustif.

---

# NØCTARIA 0.18.7 — Shotgun : connexion et synchronisation sans navigation (Android)

- Le bouton **Connecter Shotgun** présente désormais uniquement la page officielle de connexion et un état de progression : suppression des boutons NØCTARIA **Retour**, **Précédent**, **Actualiser**, **Importer la soirée** et **Synchroniser mes billets** sur cet écran.
- Dès la connexion à Shotgun, NØCTARIA examine automatiquement **Tous les billets**, **À venir** et **Passés**, rassemble les liens accessibles, dédoublonne les soirées et déclenche directement leur import, sans case à cocher.
- L'ancienne liste de sélection sur l'accueil est supprimée. Une sélection enregistrée par une version précédente est reprise automatiquement. Le bouton Retour **système** ferme simplement la page de connexion au lieu de remonter l'historique Shotgun.
- L'outil distinct d'import manuel via un lien de soirée reste disponible ailleurs dans l'application, sans apparaître dans le parcours de connexion.
- La vérification du comportement réel de connexion exige un compte Shotgun personnel ; le fonctionnement dépend du site et de ses protections. La PWA iOS n'a pas encore d'accès autorisé à ces pages privées.

---

# NØCTARIA 0.18.6 — Spotify sur toutes les soirées (Android + PWA iPhone)

- Recherche des comptes Spotify à partir des **artistes de toutes les soirées enregistrées**, y compris anciennes soirées et nouveaux événements importés. Aucune donnée de consommation n'est envoyée aux services de recherche.
- Les **15 profils Spotify déjà confirmés dans Android** sont désormais aussi disponibles dans la PWA iPhone, via une liste générée depuis le code Android. Chaque correspondance connue est propagée aux occurrences de l'artiste dans toutes les soirées.
- Sur iPhone, les candidats découverts via Wikidata sont ajoutés **uniquement lorsque le nom d'artiste est confirmé par l'oEmbed officiel Spotify**. Les homonymes et candidats non vérifiables restent sans lien ; aucune photo arbitraire n'est attribuée.
- Les nouvelles soirées ou sauvegardes importées déclenchent une nouvelle vérification, sans attendre le délai habituel de 24 heures. Recherche web progressive par groupes limités, avec reprise au prochain lancement si iOS interrompt l'application.
- Android conserve sa recherche MusicBrainz/Wikidata et l'enrichissement de tous les line-ups ; le statut indique maintenant combien de soirées ont été examinées et combien n'ont pas d'artistes renseignés. Restaurer une sauvegarde Android relance la recherche Spotify.
- Limite : il n'est pas possible de garantir le compte de **chaque** artiste. Certaines soirées ne contiennent pas de line-up, et Spotify peut ne pas fournir d'identification publique fiable. L'application laisse alors le lien non renseigné plutôt que d'inventer une identité.

---

# NØCTARIA 0.18.5 — Captures d’écran Shotgun (Android)

- Les captures d’écran et l’enregistrement de l’écran sont désormais autorisés dans le navigateur Shotgun intégré à NØCTARIA Android. Le blocage venait du drapeau Android `FLAG_SECURE` appliqué à cette fenêtre.
- Cela ne change pas l’authentification Shotgun, la synchronisation des événements ou le fonctionnement de la PWA iOS.
- Attention aux captures de billets personnels : masque QR codes et informations privées avant tout partage.

---

# NØCTARIA 0.18.4 — Billets personnels Shotgun (Android)

- Après la connexion dans le navigateur Shotgun **intégré à Android**, NØCTARIA visite automatiquement les trois pages de billets : `/fr/account/tickets`, `/fr/account/tickets/pending`, `/fr/account/tickets/past`.
- Les cartes de soirées accessibles sont dédoublonnées et la synchronisation démarre **sans sélection manuelle** ; les vérifications périodiques explorent maintenant les mêmes trois listes.
- La synchronisation dépend du chargement et de la structure des pages Shotgun ; elle peut échouer en cas de session expirée, de connexion via un fournisseur externe, de contrôle anti-automatisation ou de changement d'interface. Le code ne récupère pas les mots de passe ni les QR codes.
- **iPhone/PWA : cette synchronisation des billets personnels n'est pas disponible.** Safari interdit à la PWA NØCTARIA de lire les pages privées d'un autre domaine. Aucun faux état « connecté/synchronisé » ne doit être affiché.
- Pour les évolutions futures, Android et iOS doivent être traités ensemble. Les écarts réels sont consignés dans [le contrat multiplateforme](docs/CROSS_PLATFORM.md) et vérifiés automatiquement pour les fonctionnalités communes.

---

# NØCTARIA 0.18.3 — Portraits Spotify dans toutes les soirées

- Si Spotify ne fournit pas `thumbnail_url` via oEmbed, NØCTARIA cherche désormais le **portrait de l'artiste sur les pages officielles Spotify et son lecteur intégré**, en vérifiant le nom. Aucune photo arbitraire n'est attribuée à un homonyme.
- La récupération des **photos manquantes pour les comptes Spotify déjà identifiés** passe avant la découverte de nouveaux profils.
- La mise à jour relance automatiquement un balayage sur **tous les événements sauvegardés**, même si d'anciens échecs étaient encore en cache. Le bouton « Compléter les artistes » permet aussi un nouvel essai immédiat.
- Lorsqu'une URL Spotify ne renvoie pas d'image affichable, l'application privilégie le portrait Shotgun existant s'il est disponible.
- Les photos chargées sont gardées brièvement en mémoire pour éviter que l'écran ne revienne aux initiales durant les mises à jour du journal.
- Il reste possible qu'un artiste ne possède pas de portrait public accessible. L'application ne marque pas une photo non confirmée comme officielle.

---

# NØCTARIA v0.18.2 — Recherche de structures par adresse

- La section **Structures et associations** ne dépend plus d'une liste fixe limitée à Arles. Une adresse, une ville ou un code postal est géocodé avec le service public **Géoplateforme/IGN**.
- Recherche asynchrone de structures cartographiées **CSAPA**, **CAARUD** et autres lieux d'addictologie autour des coordonnées demandées, via **OpenStreetMap Overpass** : liste triée par proximité, distance à vol d'oiseau, contacts et accès à une carte quand ces données sont disponibles.
- Les résultats sont **recalculés à chaque changement d'adresse**, avec état de chargement, gestion des erreurs et bouton pour relancer.
- L'**annuaire officiel Drogues Info Service** reste accessible et est mis en avant lorsque la base cartographique ne contient pas de résultats : les données OpenStreetMap ne sont pas exhaustives.
- L'application n'utilise pas le GPS : la saisie d'adresse part au géocodeur public de l'IGN, et les coordonnées sont utilisées pour la recherche publique de structures. Les données de consommation ne sont pas envoyées.
- La recherche des profils Spotify dans toutes les soirées se poursuit par **petits lots chaînés**, plutôt qu'avec des délais de reprise croissants entre les lots.
- Version Android 0.18.2, identifiant `fr.afterlog.app` inchangé.

---

# NØCTARIA v0.18.1 — Cartes d'artistes circulaires, affiches et couleurs

- Chaque **carte d'artiste entière** est un vrai cercle, au diamètre identique (3 colonnes responsives), avec photo, nom et statut Spotify intégrés. Les noms longs sont limités à deux lignes pour conserver l'alignement.
- Les consommations possèdent chacune une **couleur visuelle stable** : bulles du journal, choix des substances, fiche de prise et courbes de suivi. Les avertissements RDR restent indépendants (orange/rouge).
- Chaque événement, même une **soirée privée chez un ami**, affiche automatiquement une affiche NØCTARIA illustrée et disponible hors ligne. L'éditeur permet de choisir une photo d'affiche personnelle et de revenir à l'illustration par défaut. Les affiches Shotgun existantes restent prioritaires lorsqu'aucune photo personnelle n'est choisie.
- Le moteur Spotify propage immédiatement les liens déjà identifiés à tous les événements et enrichit les autres progressivement. Sept profils d'artistes de festival supplémentaires ont été vérifiés à partir de pages Spotify publiques. Les homonymes non confirmés ne sont jamais inventés.
- L'identifiant Android `fr.afterlog.app` et les chemins de mise à jour historiques ne changent pas.

---

# NØCTARIA v0.18.0 — Nouveau nom, même journal

**NØCTIS devient NØCTARIA.** Le logo néon, la galerie multimédia, les
souvenirs de soirées et la recherche de profils Spotify sont conservés.
Le nom apparaît sur l'écran d'accueil, les fenêtres, les notifications
et l'icône Android. L'identifiant `fr.afterlog.app`, la signature Android,
les données locales et l'URL historique de mise à jour `AfterLog.apk`
restent inchangés, pour permettre la mise à jour de l'application installée.

La nouvelle release comporte `NOCTARIA-v0.18.9.apk` ainsi que des alias de
compatibilité pour les anciennes versions.

---

## NØCTIS v0.17.1 — Sélection des médias dans la fiche événement

- Le bouton **Sélectionner** et l'**appui long** sur une vignette permettent d'activer la sélection directement dans « Ma galerie », sans passer par le lecteur plein écran.
- Une fois le mode sélection activé, chaque vignette se coche ou se décoche d'un toucher. **Tout / Aucun** permet de gérer le lot complet.
- Les actions **Supprimer**, **Partager** et **Story Instagram** sont disponibles dans la fiche de soirée, avec confirmation personnalisée avant une suppression. La copie NØCTIS est retirée, pas l'original dans la galerie du téléphone.
- Le partage multiple utilise `ACTION_SEND_MULTIPLE` et les URI temporaires de `FileProvider`; un fichier unique utilise le partage Story Instagram standard. Instagram n'offre pas de garantie qu'une sélection mixte de plusieurs photos et vidéos soit importée intégralement dans une story.
- L'ancien dialogue qui proposait uniquement de partager **le dernier fichier importé** est supprimé.
- Aucun changement de l'identifiant Android `fr.afterlog.app` ou du stockage des soirées.

---

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

[![Télécharger NØCTARIA](https://img.shields.io/badge/T%C3%89L%C3%89CHARGER-N%C3%98CTARIA%20Android-7c3aed?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Elikto/Afterlog/releases/download/v0.18.9/NOCTARIA-v0.18.9.apk)

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

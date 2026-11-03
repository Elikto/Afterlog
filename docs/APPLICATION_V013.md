# AfterLog 0.13.0 — cahier des charges et validation

## Accueil
- MAJ discret à gauche, paramètres à droite ; quatre actions principales.
- Les cartes de soirées affichent les seules icônes distinctes des produits notés, sans textes ni quantités.
- Ne laisser la carte de synchronisation visible que pour les opérations en cours ou les erreurs des billets effectivement cochés.

## Journal des soirées
- Deux colonnes de **cercles parfaits, de même diamètre**, pas des rectangles arrondis.
- Icône, nom, quantité ou fractions et heure si elle est connue, centrés et sans débordement.
- Bouton + circulaire de même diamètre juste après les dernières prises.
- Les entrées nouvelles doivent apparaître immédiatement après l'enregistrement.
- Toucher un cercle ouvre le détail, avec Modifier et Supprimer (confirmation obligatoire).

## Médias
- Un seul bouton + Médias.
- Galerie plein écran, zoom image, lecture vidéo Media3 et choix du lecteur externe si nécessaire.
- Action Partager via le sélecteur Android, pour les réseaux et messageries compatibles installés.
- Action Story Instagram disponible seulement lorsqu'elle est acceptée par l'application.
- Proposition de partage après importation, jamais de partage sans clic explicite.
- Les fichiers photo/vidéo ne sont pas inclus dans la sauvegarde JSON : conserver les originaux.

## Shotgun et Spotify
- Cartes d'artistes avec portraits ronds, extraits de la fiche événement Shotgun.
- Extraction DOM améliorée : images chargées au défilement, srcset, fonds CSS et cartes non cliquables.
- Association du portrait au seul artiste dont le nom correspond à la carte.
- Icône Spotify verte uniquement avec identité de profil vérifiée ; recherche violette sinon.
- L'extraction des portraits doit être testée sur l'événement réel ; une compilation réussie n'en garantit pas le résultat.

## Suivi et prévention
- Filtres jour/semaine/mois/année.
- Graphique en courbes par type : violet alcool, turquoise MDMA/ecstasy, orange autres.
- Repères de vigilance orange, rouge pour interactions connues à risque, jamais un diagnostic.
- Petite pastille RDR dans la fiche, détails et sources uniquement au clic.
- Pas d'avertissement pour deux verres simplement saisis : la contenance et le degré d'alcool ne sont pas connus.
- Un enregistrement de plusieurs fractions n'établit pas plusieurs prises dans le temps.
- Les prises successives de MDMA peuvent faire l'objet d'une vigilance basée sur les heures effectivement consignées, sans présenter deux heures comme limite de sécurité officielle.
- Sources : Santé publique France et Drogues Info Service. Urgences : 15/112 ; aide : 0 800 23 13 13.

## Mise à jour Android
- Contrôle du paquet, de la signature et de l'intégrité du fichier avant installation.
- Publication stable lorsque la clé permanente a été transmise par le secret GitHub AFTERLOG_SIGNING_BUNDLE et vérifiée.
- Une ancienne installation avec une autre signature requiert une migration unique ; préserver les données et médias avant toute désinstallation.

**Critères d'acceptation** : deux cercles de même diamètre, apparition immédiate d'une nouvelle entrée, logos sur sa soirée seulement, ouverture du partage Android, graphique en courbes, alertes compactes, compilation et publication stable signée. Les portraits Shotgun et la lecture de médias restent à confirmer sur le téléphone.

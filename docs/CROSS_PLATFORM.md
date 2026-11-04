# NØCTARIA — Contrat Android / iPhone

**Objectif du produit :** une seule application du point de vue de l'utilisateur (même nom,
logo, parcours et fonctions), distribuée en APK sur Android et en PWA gratuite sur iOS.

**Réalité technique (novembre 2026) :** Android est une application Java native,
iOS est une PWA JavaScript. Ce ne sont pas encore une interface unique ni deux
applications exactement identiques. Ne jamais annoncer une parité complète
tant que les écarts ci-dessous subsistent.

## Règle obligatoire pour chaque nouvelle demande

1. Implémenter la fonctionnalité demandée **sur Android et dans la PWA** dans la
   même livraison, avec les comportements correspondants.
2. Ajouter / réviser le contrat dans `shared/features.json`, vérifier les
   cas fonctionnels des deux plateformes et mettre à jour les tests.
3. Déployer la PWA (GitHub Pages) **et** compiler un APK signé/mis à jour si
   l'Android a changé. Les numéros de version, les migrations de données et
   les compatibilités doivent être vérifiés.
4. Comparer l'interface mobile sur un appareil Android et dans Safari iOS :
   tailles, cartes, galeries, formulaires, contenu, navigation et accessibilité.
5. En cas de limitation technique réelle, **signaler la différence**
   au lieu de présenter une version incomplète comme identique.

La commande `node scripts/check-parity.mjs` vérifie un socle de fonctionnalités
présentes des deux côtés. Le test web `node web/smoke-test.mjs` vérifie le
fonctionnement de la PWA, dont le cycle de sauvegarde complet. Les workflows
CI effectuent également une compilation Android de validation.

Les contrôles de présence dans le code et les compilations **ne suffisent pas**
à prouver l'égalité visuelle ou fonctionnelle. Les tests de vrais appareils
restent nécessaires.

## Différences actuellement connues

| Fonction | Android | PWA iPhone |
| --- | --- | --- |
| Nom et logo | NØCTARIA / icône source Android | Même icône générée automatiquement depuis les vecteurs Android |
| Gestion des soirées, journaux, galerie, artistes, suivi, RDR | Implémentée | Implémentée (à valider sur appareil iOS) |
| Sauvegarde JSON | Export/import compatible `afterlog-v1` | Export/import compatible `afterlog-v1` |
| Archive incluant les médias | Non encore implémentée sur Android | Export/import `.noctaria` |
| Connexion à un **compte personnel Shotgun** / synchro des billets | Extraction via WebView native | **Non disponible** : pas d'API personnelle publique autorisée établie |
| Instagram Story | Intent de partage Android | Feuille de partage iOS, options définies par Instagram |
| Mise à jour | APK signé | PWA via service worker |

**Protection des données :** stockage privé local ; il n'existe pas de synchronisation
cloud du journal entre Android et iOS. La sauvegarde n'est pas une synchro
automatique. Ne jamais collecter ni transporter un mot de passe, un cookie
d'authentification ou un jeton Shotgun vers le site sans intégration autorisée.

## Déploiements gratuits

- iOS : https://elikto.github.io/Afterlog/ — Safari, Partager → Sur l'écran d'accueil
- Android : APK GitHub Actions avec identité et signature existantes.

Aucun abonnement développeur Apple nécessaire pour la PWA.

## Spotify — couverture multiplateforme

- Source unique des 15 profils connus : `SpotifyArtistFinder.java` ; génération de
  `web/spotify-trusted.js` par `node scripts/sync-spotify-profiles.mjs`.
- Avant chaque publication, `node scripts/sync-spotify-profiles.mjs --check`
  et `node scripts/spotify-parity-test.mjs` doivent réussir.
- Toute nouvelle soirée sur Android ou iPhone est éligible à la recherche Spotify,
  y compris une soirée importée depuis une sauvegarde. Les homonymes et profils
  impossibles à confirmer ne doivent jamais être attribués automatiquement.
- Un compte Spotify confirmé ne garantit pas de portrait public. Ne pas inventer
  de photo quand Spotify ne renvoie pas de vignette.

# AfterLog — Journal de soirées Android

Version **0.5.0** (bêta de test). Projet : https://github.com/Elikto/Afterlog

## Nouveautés de la 0.5

- **Navigation Android :** le bouton Retour système revient à la page précédente dans AfterLog (soirée → accueil, statistiques → accueil, modification de soirée → soirée). Dans le navigateur Shotgun, le bouton Retour remonte son historique. À l'accueil, Retour ferme normalement l'application.
- **Nouvelle saisie tactile de consommation :** remplacement du formulaire à menus déroulants par des tuiles pictographiques faciles à toucher.
- Catégorie Alcool : 🍺 Bière, 🍷 Vin, 🍸 Vodka, 🥃 Whisky, ➕ Autre.
- Catégorie Autres substances : 💊 Ecstasy, 🧬 MDMA, 🧪 Kétamine, ❄️ Cocaïne, ⚡ Speed, 💨 Poppers, 🌿 Cannabis, 🌈 LSD, 🍄 Champignons, 💧 GHB / GBL, ➕ Autre.
- Catégorie Sans alcool : 💧 Eau, 🥤 Soft et ➕ Autre.
- Toucher une catégorie ouvre un **compteur avec − / +**, puis **Heure de la prise** via sélecteur horaire ou **Pendant toute la soirée** si l'heure est inconnue.
- La date est modifiable pour les soirées se terminant après minuit.
- **Prix et note** sont conservés en option repliée.
- Modifier/supprimer les anciennes consommations reste possible. Les informations sont stockées localement, sans envoi vers GitHub ou Shotgun.

> Les pictogrammes servent à reconnaître une catégorie et non à identifier la composition ou le dosage réel d'un produit. La quantité est ici un nombre d'unités/prises enregistré librement, pas une recommandation de consommation.

## Import Shotgun
Connexion dans le navigateur intégré, accès à « Mes billets → Passés », import séquentiel des détails visibles (selon la structure des pages), possibilité d'ajouter les soirées manuellement.

## Télécharger la version
https://github.com/Elikto/Afterlog/releases

**IMPORTANT — Sauvegarde avant installation :** les builds Android actuels sont signés avec une clé debug temporaire de GitHub Actions. La mise à jour par-dessus l'ancienne version peut être refusée. **Exporte tes données JSON** depuis l'application avant de désinstaller une ancienne version, puis réimporte-les après installation de la nouvelle.

Le bouton « Rechercher une mise à jour » est présent. Pour qu'une mise à jour s'installe sans désinstallation ni perte de données, il reste nécessaire de signer toutes les versions avec une **clé Android permanente**, stockée hors du dépôt public et protégée comme secret.

L'APK est compilé et publié automatiquement par GitHub Actions. La compilation ne remplace pas un test sur téléphone.

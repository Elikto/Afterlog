# AfterLog — mises à jour Android sans réinstallation

## Pourquoi la version de test ne peut pas être remplacée directement

Android impose un **certificat de signature identique** entre l'application installée et son APK de mise à jour. Jusqu'à la v0.11.0, les APK AfterLog étaient signés par la clé debug temporaire d'un runner GitHub Actions, et non par une clé de distribution permanente. Cette ancienne clé ne peut pas être reconstituée à partir d'un APK.

Le système de CyberQuiz utilise, lui, un keystore privé conservé dans GitHub Actions et une publication stable `apk-latest` avec un fichier `update.json` signé par empreinte.

## Mise en place en une seule fois, depuis le téléphone

Une clé privée de distribution AfterLog a été générée pour le propriétaire du dépôt. Elle **ne figure pas dans le dépôt public**.

1. Conserver le ZIP privé de configuration obtenu hors de GitHub.
2. Dans le dépôt GitHub Afterlog : **Settings → Secrets and variables → Actions → New repository secret**.
3. Nom : **`AFTERLOG_SIGNING_BUNDLE`**.
4. Valeur : texte complet du fichier `COPIER_DANS_SECRET_GITHUB_AFTERLOG_SIGNING_BUNDLE.txt` contenu dans le ZIP privé. Conserver les caractères sans ajout ni suppression.
5. Valider, puis ouvrir **Actions → Compiler AfterLog APK → Run workflow** sur `main`.
6. Après réussite de la compilation, la release [apk-latest](https://github.com/Elikto/Afterlog/releases/tag/apk-latest) contiendra `AfterLog.apk` et `update.json`. Ce dernier précise le nom du paquet, la version, la taille, le SHA-256 du fichier et l'identité SHA-256 du certificat.
7. Une fois la première installation stable effectuée, les nouvelles versions se mettront à jour depuis le bouton **MAJ** sans réinstallation, tant que ce secret n'est pas changé.

La signature de distribution attendue est épinglée dans le workflow :

`0eae222a3b88b93b56e3d6b4bbd7f991e65b88030ae3e0f4245662e3f80d4772`

Cette empreinte est **publique** ; elle ne permet pas de reproduire la clé privée.

**Ne jamais** ajouter le ZIP, le keystore, le secret ou le mot de passe à un commit, une issue, une release ou un commentaire GitHub.

## Cas de la version déjà installée

Si AfterLog installé sur le téléphone utilise une **ancienne clé temporaire**, Android interdit de l'écraser par la version signée durablement. L'ancienne clé n'étant pas conservée, il n'existe pas de contournement légitime qui garde les données automatiquement.

**Ne désinstaller que lorsque les données sont sauvegardées.** Depuis l'application actuelle, exporter le journal au format JSON. Ce JSON **n'inclut pas les fichiers photo et vidéo privés** ; il faut donc conserver leurs originaux indépendamment. Après la migration unique vers la version stable, la mise à jour intégrée pourra fonctionner normalement.

## Comportement du bouton MAJ

- Interroge le `update.json` de la release stable `apk-latest` et compare le **versionCode** avec la version installée.
- Vérifie le paquet Android, l'URL de téléchargement, la taille, le SHA-256 de l'APK et le certificat de signature.
- Si le certificat ne correspond pas, affiche une explication et dirige vers les paramètres et sauvegardes, **sans risquer une installation destructrice**.
- Si la signature est compatible, télécharge l'APK sur le téléphone puis ouvre l'installateur système Android (avec autorisation utilisateur si nécessaire).
- En cas d'indisponibilité de la release stable, affiche une erreur explicite, pas un faux « déjà à jour ».

Les APK de tests non signés durablement continuent à pouvoir être compilés dans les artefacts des workflows ; ils ne sont plus publiés comme mises à jour compatibles.

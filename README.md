<div align="center">

# 🌙 AfterLog

### Revivez vos soirées. Gardez une trace de vos expériences.

**AfterLog** est une application Android personnelle pour retrouver ses événements, garder ses souvenirs et suivre ses consommations au fil d'une soirée — sans se perdre dans des formulaires compliqués.

Une seule interface pour vos soirées **techno, festivals, concerts et événements**, de la première affiche au dernier souvenir.

<br>

<a href="https://github.com/Elikto/Afterlog/releases/download/v0.6.0-debug/AfterLog-v0.6.0.apk">
  <img src="https://img.shields.io/badge/T%C3%89L%C3%89CHARGER-AfterLog%20Android-7c3aed?style=for-the-badge&logo=android&logoColor=white" alt="Télécharger AfterLog pour Android (APK)" height="52">
</a>

<br>

**📱 Android 8.0+** · **🧪 Bêta v0.6.0** · **🇫🇷 Interface en français**

[📦 Toutes les versions](https://github.com/Elikto/Afterlog/releases) · [🐛 Signaler un problème](https://github.com/Elikto/Afterlog/issues)

</div>

---

## ✨ Votre soirée, votre histoire

Vous retrouvez un événement auquel vous avez participé ou allez participer, avec sa **grande affiche**, son nom, sa date, son lieu et, lorsque Shotgun les fournit, les horaires, artistes, styles musicaux et sa description.

À partir de cette fiche, vous créez **votre journal personnel** : les boissons et autres substances consommées, les quantités, les horaires, les dépenses et les notes dont vous souhaitez vous souvenir.

**Avant, pendant ou après l'événement :** vous pouvez enregistrer une consommation immédiatement, ou compléter votre journal plus tard.

## 🎟️ Vos événements Shotgun au même endroit

AfterLog intègre un navigateur pour vous connecter **directement sur le site Shotgun**. L'application essaie ensuite de parcourir vos billets accessibles, pour les soirées **passées, à venir et en cours**, et de compléter leurs fiches.

| Fonctionnalité | Ce que fait AfterLog |
| --- | --- |
| **Connexion Shotgun** | Ouvre le site Shotgun dans l'application ; les identifiants restent saisis sur le site |
| **Import d'événements** | Recherche les événements accessibles dans « Mes billets » |
| **Fiches détaillées** | Affiche, titre, dates, horaires, lieu, description, line-up et genres, selon les informations disponibles |
| **Synchronisation** | Tentative d'import en arrière-plan avec progression, estimation du délai et notification Android |
| **Nouveaux billets** | Vérifications périodiques prévues, au plus tôt toutes les 15 minutes ; détection non instantanée |
| **Ajout manuel** | Possibilité de créer et modifier une soirée sans Shotgun |

> **Synchronisation expérimentale :** AfterLog n'utilise pas d'API officielle de compte participant Shotgun. La récupération dépend de la session, du contenu des pages et des restrictions du site. Elle peut être partielle ou interrompue, notamment en cas d'erreur HTTP 429. Android peut également différer ou interrompre les tâches en arrière-plan.

## 🍺 Un journal de consommation pensé pour le téléphone

Plus besoin de remplir un long formulaire : **touchez un pictogramme**, ajustez un compteur avec **+ / −** et enregistrez.

- **Alcool :** bière, vin, vodka, whisky, autres boissons.
- **Autres substances :** ecstasy, MDMA, kétamine, cocaïne, speed, poppers, cannabis, LSD, champignons, GHB/GBL et autres.
- **Sans alcool :** eau, boissons sans alcool et autres.
- **Heure de la prise :** sélection de l'heure ou option **« Pendant toute la soirée »** si vous ne vous en souvenez pas.
- **Compléments facultatifs :** date, prix et notes.

Chaque entrée est rattachée à une soirée, consultable et modifiable. Les pictogrammes servent uniquement au classement : ils ne permettent pas de déterminer la composition d'un produit ni d'en estimer les risques.

## 📍 Une fiche événement immersive

Une présentation inspirée de l'organisation des fiches événementielles, avec :

- Une **affiche en grand format** et le titre de la soirée.
- La **date, les horaires, l'adresse et le lieu**.
- Une section **À propos** avec la description.
- Le **line-up**, les genres et un lien vers la localisation.
- Votre **journal personnel** directement sous les informations de l'événement.

Les soirées sont enregistrées localement et restent consultables même si leur page Shotgun n'est plus disponible, dans la mesure où les informations ont déjà été importées.

## 📊 Historique et statistiques

Retrouvez vos soirées passées, vos consommations par événement, les catégories enregistrées ainsi que les dépenses consignées. Des fonctions de statistiques simples sont présentes dans la version bêta.

## 🔒 Vos données restent personnelles

Les **consommations, notes et dépenses** sont conservées dans le stockage local de l'application, pas publiées sur GitHub et pas envoyées à Shotgun.

Vous pouvez **exporter ou restaurer une sauvegarde JSON**. Attention : ce fichier de sauvegarde n'est **pas chiffré** ; conservez-le dans un emplacement privé. La session du navigateur Shotgun demeure sur l'appareil jusqu'à déconnexion.

## ⬇️ Installer AfterLog

1. Appuyez sur le **bouton violet « TÉLÉCHARGER » en haut de cette page**.
2. Ouvrez le fichier **AfterLog-v0.6.0.apk** sur votre téléphone Android.
3. Si Android le demande, autorisez l'installation d'applications depuis votre navigateur, puis validez.

La version actuelle est une **bêta Android compilée et publiée sur GitHub**, pas une application du Play Store.

### 🔄 Mises à jour dans l'application

AfterLog dispose d'un bouton **« Rechercher une mise à jour »** : il vérifie les versions GitHub, propose leur téléchargement et ouvre l'installation Android.

**Limitation actuelle :** les APK de test sont signés avec des certificats de debug temporaires. Une mise à jour peut donc être refusée par Android en raison d'une signature différente.

**Avant toute désinstallation**, exportez votre sauvegarde depuis **Réglages et sauvegarde → Exporter toutes mes données (JSON)**. Vous pourrez l'importer après réinstallation. Une signature Android permanente reste à mettre en place pour permettre les futures mises à jour directement par-dessus l'application.

## 🛠️ Projet et développement

**AfterLog est un projet indépendant et non officiel**, sans affiliation avec Shotgun.

- **Plateforme :** Android 8.0 et versions ultérieures.
- **Langage :** Java.
- **Construction :** Android SDK, Gradle et GitHub Actions.
- **Données :** stockage local avec export et import JSON.
- **Statut :** bêta, avec synchronisation Shotgun à tester sur différents appareils.

Le code source et les versions publiées sont disponibles dans ce dépôt. Pour proposer une idée ou signaler un problème, utilisez les [Issues GitHub](https://github.com/Elikto/Afterlog/issues).

---

<div align="center">

**AfterLog — Une soirée, des souvenirs, un journal.**

<a href="https://github.com/Elikto/Afterlog/releases/download/v0.6.0-debug/AfterLog-v0.6.0.apk">
  <img src="https://img.shields.io/badge/INSTALLER-AfterLog%20pour%20Android-7c3aed?style=for-the-badge&logo=android&logoColor=white" alt="Installer AfterLog sur Android" height="44">
</a>

</div>

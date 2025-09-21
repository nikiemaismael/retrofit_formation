# Application Flutter avec Retrofit

Une application Flutter de démonstration qui utilise Retrofit pour effectuer des opérations CRUD sur des posts via l'API JSONPlaceholder.

##  Fonctionnalités

- Affichage d'une liste de posts
- Création d'un nouveau post
- Mise à jour d'un post existant
- Suppression d'un post
- Interface utilisateur réactive avec Material Design
- Gestion d'état avec Provider

##  Prérequis

- Flutter SDK (dernière version stable recommandée)
- Dart SDK (compatible avec la version de Flutter)
- Un éditeur de code (VS Code ou Android Studio avec les extensions Dart/Flutter)

## Installation

1. Cloner le dépôt :
   ```bash
   git clone [URL_DU_DEPOT]
   cd retrofit_test
   ```

2. Installer les dépendances :
   ```bash
   flutter pub get
   ```

3. Générer le code de l'API :
   ```bash
   dart pub run build_runner build
   ```

4. Lancer l'application :
   ```bash
   flutter run
   ```

##  Structure du projet

```
lib/
├── main.dart          # Point d'entrée de l'application
├── models/            # Modèles de données
│   └── post_model.dart
├── services/          # Services d'API et clients HTTP
│   ├── api_client_service.dart
│   └── rest_client.dart
├── view/              # Écrans/Pages de l'application
│   └── post_view.dart
└── view_model/        # Gestion d'état avec Provider
    └── post_view_model.dart
```

##  Fonctionnement

L'application se connecte à l'API JSONPlaceholder pour effectuer des opérations CRUD sur des posts. Elle utilise :
- **Retrofit** pour la génération de code client HTTP
- **Provider** pour la gestion d'état
- **Logger** pour le logging des requêtes/réponses

##  Dépendances principales

- `dio`: Client HTTP pour Dart
- `retrofit`: Générateur de code pour les clients REST
- `provider`: Gestion d'état
- `logger`: Journalisation des logs
- `json_annotation`: Annotations pour la sérialisation JSON
- `build_runner`: Outil de génération de code

## Configuration requise

- Android: minSdkVersion 21
- iOS: 11.0 ou supérieur

##  Auteur

 Nikiema Ismael

##  Licence

Ce projet est sous licence MIT - voir le fichier [LICENSE](LICENSE) pour plus de détails.

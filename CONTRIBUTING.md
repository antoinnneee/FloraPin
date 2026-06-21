# Guide du contributeur — FloraPin

Merci de contribuer à FloraPin ! Ce guide résume l'organisation du travail et
les conventions.

## Mise en route

Voir le [README](README.md) pour les prérequis et le lancement local de l'app
Android et du backend.

## Organisation des branches

Le travail est suivi par nœuds (meowtrack). Chaque tâche est développée sur une
branche dédiée puis fusionnée dans `main` :

```
meow/NODE-<n>      # une branche par nœud
```

Workflow type :

1. Créer la branche depuis `main` à jour.
2. Implémenter + ajouter/mettre à jour les tests.
3. Vérifier en local (voir ci-dessous).
4. Commit, merge `--no-ff` dans `main`, push.

## Vérifications avant de pousser

**App Android**

```bash
./gradlew testDebugUnitTest assembleDebug lint
```

**Backend**

```bash
cd backend
npm run build
npm test
```

Tout doit être au vert (BUILD SUCCESSFUL / tests passants) avant de fusionner.

## Conventions

- **Kotlin** : style officiel Kotlin (`kotlin.code.style=official`), composables
  par fonctionnalité dans un package dédié, ViewModels pour l'état d'écran.
- **TypeScript / NestJS** : un module par domaine (controller + service + entité
  + DTO), validation des entrées via `class-validator`, secrets via variables
  d'environnement (jamais commités).
- **Tests** : privilégier des tests unitaires de service avec dépôts factices
  (pas de dépendance à une base réelle).
- **Commits** : message en français, impératif, expliquant le « quoi » et le
  « pourquoi » ; référencer le nœud (`(NODE-x)`).
- **Secrets** : `local.properties` (Android) et `backend/.env` ne sont jamais
  commités.

## Structure du code

Voir la section « Structure du dépôt » du [README](README.md) et
[`backend/ARCHITECTURE.md`](backend/ARCHITECTURE.md) pour l'architecture backend.

## Documentation

- Contrats d'API : [`backend/docs/API.md`](backend/docs/API.md)
- Schéma de données : [`backend/db/schema.sql`](backend/db/schema.sql)
- Swagger (au runtime) : `http://localhost:3000/api/docs`

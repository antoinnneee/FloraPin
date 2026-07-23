# Publication automatisée sur Google Play

Le workflow `.github/workflows/play-store.yml` construit, signe et publie un tag
FloraPin existant avec la Google Play Developer API. Il est déclenché manuellement
pour laisser la CI terminer avant toute publication. La piste `internal` est le
choix par défaut ; `production` doit rester protégée par une approbation GitHub.

## Garde-fous

- Le workflow accepte uniquement un tag SemVer `vMAJEUR.MINEUR.CORRECTIF`.
- Le tag doit correspondre exactement au commit extrait et à `versionName`.
- L'AAB doit être signé et son `mapping.txt` R8 doit exister.
- La note française est obligatoire et limitée à 500 caractères.
- Les actions tierces sont épinglées par leur SHA de commit.
- Aucun JSON de compte de service Google n'est stocké dans GitHub :
  l'authentification utilise Workload Identity Federation (OIDC).
- Les fichiers Firebase, keystore et identifiants OIDC temporaires sont supprimés
  du runner à la fin du job.

## 1. Préparer Google Play et Google Cloud

1. Vérifier que l'application `com.florapin.app` existe déjà dans Play Console et
   qu'un premier bundle a été téléversé manuellement.
2. Activer **Google Play Android Developer API** dans un projet Google Cloud.
3. Créer un compte de service dédié, sans rôle Google Cloud général inutile.
4. Dans Play Console, ouvrir **Utilisateurs et autorisations**, inviter l'adresse
   du compte de service et limiter son accès à FloraPin :
   - **Publier des applications sur les canaux de test** pour `internal`, `alpha`
     et `beta` ;
   - **Publier en production, exclure des appareils et utiliser Play App Signing**
     uniquement si le workflow doit pouvoir cibler `production`.
5. Configurer un pool et un fournisseur Workload Identity pour GitHub Actions,
   limité au dépôt `antoinnneee/FloraPin`, puis autoriser ce fournisseur à utiliser
   le compte de service.

Documentation officielle :

- https://developers.google.com/android-publisher/getting_started
- https://github.com/google-github-actions/auth#workload-identity-federation

## 2. Configurer l'environnement GitHub

Créer un environnement GitHub nommé `play-store` et lui ajouter des approbateurs
obligatoires. Ajouter ensuite les variables :

| Variable | Valeur |
| --- | --- |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | Chemin complet `projects/.../locations/global/workloadIdentityPools/.../providers/...` |
| `GCP_PLAY_PUBLISHER_SERVICE_ACCOUNT` | Adresse du compte de service dédié |

Ajouter les secrets suivants :

| Secret | Contenu |
| --- | --- |
| `GOOGLE_SERVICES_JSON_BASE64` | `app/google-services.json` encodé en Base64 |
| `MAPTILER_API_KEY` | Clé API MapTiler intégrée au bundle Android |
| `RELEASE_KEYSTORE_BASE64` | Keystore d'upload Play Store encodé en Base64 |
| `RELEASE_STORE_PASSWORD` | Mot de passe du keystore |
| `RELEASE_KEY_ALIAS` | Alias de la clé d'upload |
| `RELEASE_KEY_PASSWORD` | Mot de passe de la clé d'upload |

Sous PowerShell, encoder un fichier sans l'afficher :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('app/google-services.json')) |
    Set-Clipboard
```

Remplacer le chemin par celui du keystore pour créer
`RELEASE_KEYSTORE_BASE64`. Ne jamais coller ces valeurs dans un fichier suivi.

La clé MapTiler est nécessaire au moment de la compilation : toute modification
du secret exige donc de construire et publier un nouveau bundle. Comme une clé
embarquée dans une application Android peut être extraite, limiter ses droits et
ses quotas dans MapTiler.

## 3. Maintenir la note Play Store

Avant de créer chaque tag, mettre à jour :

```text
distribution/whatsnew/whatsnew-fr-FR
```

Le fichier contient uniquement la note destinée aux utilisateurs, sans Markdown,
et ne doit pas dépasser 500 caractères. Une note ponctuelle peut aussi être
fournie dans l'entrée `release_notes` du workflow.

## 4. Publier

Depuis l'onglet **Actions → Publish Play Store → Run workflow**, renseigner le tag,
la piste et le statut. Pour une première validation, utiliser `internal` et
`completed`.

La commande équivalente est :

```powershell
gh workflow run play-store.yml --ref main `
  -f release_tag=v1.15.0 `
  -f track=internal `
  -f status=completed
```

Pour `production`, le statut `completed` soumet ou publie la version selon le mode
de publication et l'état de validation configurés dans Play Console. Utiliser
`draft` pour déposer un brouillon sans lancer le déploiement.

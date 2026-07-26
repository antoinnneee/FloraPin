// @ts-check
import eslint from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * Configuration ESLint du backend (flat config, ESLint 9).
 *
 * Parti pris : on lint la CORRECTION (promesses avalées, await sur non-thenable,
 * variables mortes…), pas le STYLE. Aucun formateur n'est branché — Prettier
 * reformaterait l'intégralité du code existant et noierait l'historique Git.
 *
 * Les règles « unsafe » de `recommendedTypeChecked` sont en `error` sur `src/` :
 * le code de production a été mis à zéro avertissement (retours de
 * `DataSource.query` et flux MinIO typés explicitement), donc tout nouveau `any`
 * qui s'échappe est signalé au lieu de se fondre dans un bruit de fond. Elles
 * sont neutralisées dans les fichiers de test, où Jest et supertest rendent
 * `any` par conception (voir le dernier bloc).
 */
export default tseslint.config(
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', 'eslint.config.mjs'],
  },
  eslint.configs.recommended,
  ...tseslint.configs.recommendedTypeChecked,
  {
    languageOptions: {
      globals: { ...globals.node, ...globals.jest },
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      // Nest s'appuie massivement sur les décorateurs et l'injection : le typage
      // exact d'un DTO passe par class-validator, pas par la signature.
      '@typescript-eslint/no-explicit-any': 'off',
      // `src/` est à zéro : on verrouille pour empêcher la régression. Quand une
      // API tierce impose un `any` (SDK non typé), le convertir explicitement
      // — voir `AdminService.rows<T>()` ou `ObjectListStream`.
      '@typescript-eslint/no-unsafe-assignment': 'error',
      '@typescript-eslint/no-unsafe-member-access': 'error',
      '@typescript-eslint/no-unsafe-call': 'error',
      '@typescript-eslint/no-unsafe-argument': 'error',
      '@typescript-eslint/no-unsafe-return': 'error',
      // Une promesse non attendue dans un service Nest = notification perdue ou
      // erreur invisible : à corriger, pas à ignorer.
      '@typescript-eslint/no-floating-promises': 'error',
      // Une implémentation qui honore un contrat `Promise<T>` sans rien attendre
      // reste légitime : c'est le cas de tous les pilotes de repli
      // (stub-storage, stub-mail, stub-push, stub-identifier) et des gardes
      // synchrones alignées sur leurs voisines asynchrones. La règle n'y verrait
      // que du bruit, et la retirer de la signature casserait l'interface.
      '@typescript-eslint/require-await': 'off',
      // `_` en préfixe = paramètre volontairement inutilisé (signature imposée).
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  {
    /**
     * Fichiers de test uniquement. Les règles « unsafe » y sont désactivées, non
     * par facilité mais parce que deux API centrales rendent `any` PAR
     * CONCEPTION : les matchers Jest (`expect.objectContaining`,
     * `expect.stringMatching`) et le `.body` de supertest. Les traquer ici
     * produit du bruit qu'aucun typage raisonnable ne supprime.
     *
     * Le code de `src/` hors specs, lui, est tenu à **zéro avertissement** :
     * tout nouveau `any` non maîtrisé s'y voit immédiatement.
     */
    files: ['**/*.spec.ts', 'test/**/*.ts'],
    rules: {
      '@typescript-eslint/unbound-method': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
      '@typescript-eslint/no-unsafe-call': 'off',
      '@typescript-eslint/no-unsafe-argument': 'off',
      '@typescript-eslint/no-unsafe-return': 'off',
    },
  },
);

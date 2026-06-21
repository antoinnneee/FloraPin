# Wireframe basse fidélité — vitrine FloraPin

Ordre et hiérarchie des sections (une seule page, scroll vertical). Chaque bloc
correspond à un composant Astro dans `src/components/`.

```
┌──────────────────────────────────────────────┐
│  NAV   🌸 FloraPin                 [Bêta] ▸    │  (sticky, transparent -> fond au scroll)
├──────────────────────────────────────────────┤
│                                                │
│   HERO (Hero.astro)                            │
│   H1 : « Vos fleurs, géolocalisées… »          │
│   p  : sous-titre                              │
│   [ Rejoindre la bêta ]   ← CTA primaire       │
│              ┌───────────┐                     │
│              │  mockup    │  ← visuel à venir   │
│              │  téléphone │                     │
│              └───────────┘                     │
├──────────────────────────────────────────────┤
│   FEATURES (Features.astro)                     │
│   Grille 3×2 de 6 cartes (icône + titre + 1l)  │
│   📷  🗺️  🖼️                                    │
│   👥  🌿  📴                                     │
├──────────────────────────────────────────────┤
│   HOW IT WORKS (HowItWorks.astro)              │
│   3 étapes numérotées horizontales             │
│   ① Photographier → ② Localiser → ③ Partager   │
├──────────────────────────────────────────────┤
│   SCREENSHOTS (Screenshots.astro)              │
│   Carrousel / grille de captures réelles       │
│   (placeholders tant que visuels absents)      │
├──────────────────────────────────────────────┤
│   PRIVACY (Privacy.astro)                      │
│   Encart « Vos spots restent secrets » +        │
│   illustration toggle GPS                       │
├──────────────────────────────────────────────┤
│   BETA CTA (BetaCTA.astro)                      │
│   Titre + champ email + [ Je veux tester ]     │
├──────────────────────────────────────────────┤
│   FOOTER (Footer.astro)                         │
│   Contact · Confidentialité · Mentions · GitHub │
└──────────────────────────────────────────────┘
```

## Hiérarchie visuelle
1. **Hero** : point d'entrée, CTA primaire au-dessus de la ligne de flottaison.
2. **Features** : scannable (icône + titre gras + 1 ligne).
3. **How it works** : rassure sur la simplicité (3 étapes).
4. **Screenshots** : preuve par l'image.
5. **Privacy** : lève l'objection principale (protection des spots).
6. **Beta CTA** : conversion (email).

## Palette (alignée sur le thème Compose de l'app — `ui/theme/Color.kt`)
| Rôle | Couleur | Hex |
|------|---------|-----|
| Primaire (vert profond) | Green40 | `#386A53` |
| Primaire clair / fond doux | Green80 | `#A8D5BA` |
| Accent floral | Bloom40 | `#7D5260` |
| Accent floral clair | Bloom80 | `#EFB8C8` |
| Texte | gris très foncé | `#1B1C1A` |
| Fond | blanc cassé | `#FBFDFB` |

- **CTA** : fond `#386A53`, texte blanc ; survol -> `#2C5343`.
- **Accents/illustrations** : touches `#EFB8C8` (floral) avec parcimonie.

## Typographie
- **Inter** (déjà câblée dans `Layout.astro`).
- Titres : 700 ; corps : 400 ; libellés/CTA : 600.
- Échelle : H1 clamp(2.2rem, 5vw, 3.5rem) · H2 ~2rem · corps 1.05rem.

## Notes d'implémentation
- Mobile-first, une colonne ; features en grille à partir de `md`.
- Respect des préférences de mouvement réduit (animations discrètes).
- Cible légèreté (Astro statique) : pas de JS superflu.

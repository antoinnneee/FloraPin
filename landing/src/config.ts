// Configuration de la vitrine — source de vérité unique.
//
// L'app est distribuée uniquement par le canal Google Play : la vitrine ne sert
// plus d'APK en téléchargement direct.
//
// Évolution (NODE-147) : une fois l'app publiée en production, remplacer le
// parcours d'inscription en deux étapes par le badge Google Play officiel
// pointant sur `https://play.google.com/store/apps/details?id=com.florapin.app`.
import versionInfo from './version.json';

// Version réelle de l'app (`versionName` de app/build.gradle.kts). version.json
// est régénéré par deploy.sh depuis build.gradle.kts à chaque déploiement (là où
// le dossier app/ est disponible) : la vitrine annonce ainsi la version
// effectivement distribuée, sans dépendre de app/ au build (absent du VPS).
export const APP_VERSION = versionInfo.version;

// Mention affichée sous les CTA (retirée une fois le Play Store public actif).
export const BETA_NOTE = `Bêta ${APP_VERSION} · Android 8+`;

// --- Test fermé Google Play (bêta) ---
// Pour permettre l'auto-inscription depuis la vitrine, la liste de testeurs du
// canal de test fermé est un Google Groupe. Le parcours testeur :
//   1. rejoindre le Google Groupe (PLAY_TEST_GROUP_URL) → l'e-mail est autorisé ;
//   2. activer l'accès bêta via le lien d'opt-in web (PLAY_TEST_OPT_IN_URL,
//      déterministe : play.google.com/apps/testing/<applicationId>).
// Le lien d'opt-in ne devient actif qu'une fois une version PUBLIÉE dans le canal
// de test fermé (sinon 404).
export const PLAY_TEST_GROUP_URL = 'https://groups.google.com/g/florapin-testeurs';
export const PLAY_TEST_OPT_IN_URL =
  'https://play.google.com/apps/testing/com.florapin.app';

// Configuration de la vitrine — source de vérité unique.
//
// Lien de téléchargement de l'app. Bêta : APK debug auto-hébergé sur la vitrine
// (NODE-152), régénéré à chaque déploiement et servi par Caddy à la racine du
// domaine (`/florapin.apk`).
//
// Évolution (NODE-147) : remplacer DOWNLOAD_URL par le lien Play Store
// (`https://play.google.com/store/apps/details?id=com.florapin.app`) et basculer
// le bouton custom vers le badge Google Play officiel.
import versionInfo from './version.json';

export const DOWNLOAD_URL = '/florapin.apk';

// Version réelle de l'app (`versionName` de app/build.gradle.kts). version.json
// est régénéré par deploy.sh depuis build.gradle.kts à chaque déploiement (là où
// le dossier app/ est disponible) : la vitrine reste ainsi synchronisée avec le
// binaire effectivement servi, sans dépendre de app/ au build (absent du VPS).
export const APP_VERSION = versionInfo.version;

// Nom de fichier suggéré au téléchargement (attribut `download`). Sans ça, le
// navigateur déduirait le nom de la valeur de l'attribut (« true.apk »). On y
// injecte la vraie version pour que l'utilisateur télécharge « florapin_1.3.0.apk ».
export const DOWNLOAD_FILENAME = `florapin_${APP_VERSION}.apk`;

// Mention affichée sous le bouton (retirée une fois le Play Store actif).
export const DOWNLOAD_NOTE =
  `Version bêta ${APP_VERSION} — Android 8+ · autoriser les « sources inconnues » à l’installation.`;

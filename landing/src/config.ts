// Configuration de la vitrine — source de vérité unique.
//
// Lien de téléchargement de l'app. Bêta : APK debug auto-hébergé sur la vitrine
// (NODE-152), régénéré à chaque déploiement et servi par Caddy à la racine du
// domaine (`/florapin.apk`).
//
// Évolution (NODE-147) : remplacer DOWNLOAD_URL par le lien Play Store
// (`https://play.google.com/store/apps/details?id=com.florapin.app`) et basculer
// le bouton custom vers le badge Google Play officiel.
export const DOWNLOAD_URL = '/florapin.apk';

// Nom de fichier suggéré au téléchargement (attribut `download`). Sans ça, le
// navigateur déduirait le nom de la valeur de l'attribut (« true.apk »).
export const DOWNLOAD_FILENAME = 'florapin_beta.apk';

// Mention affichée sous le bouton (retirée une fois le Play Store actif).
export const DOWNLOAD_NOTE =
  'Version bêta — Android 8+ · autoriser les « sources inconnues » à l’installation.';

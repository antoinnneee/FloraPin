import { BadRequestException } from '@nestjs/common';
import sharp from 'sharp';

/** Résultat du réencodage : image pleine résolution + miniature, en WebP. */
export interface EncodedImage {
  full: Buffer;
  thumbnail: Buffer;
}

/** Côté max (px) de l'image pleine résolution servie au détail. */
const FULL_MAX_EDGE = 2048;
/** Côté max (px) de la miniature servie en galerie/feed. */
const THUMBNAIL_MAX_EDGE = 400;

/**
 * Formats d'entrée acceptés, tels qu'identifiés par sharp à partir des OCTETS
 * réels (magic bytes) — indépendant du type MIME déclaré par le client.
 * sharp identifie HEIC et HEIF sous le même format `heif`.
 */
const ALLOWED_INPUT_FORMATS: ReadonlySet<string> = new Set([
  'jpeg',
  'png',
  'webp',
  'heif',
]);

/**
 * Réencode une image (JPEG/PNG/HEIC…) en deux WebP : une version pleine
 * résolution bornée à {@link FULL_MAX_EDGE} et une miniature
 * {@link THUMBNAIL_MAX_EDGE}. `rotate()` applique l'orientation EXIF puis la
 * supprime (les WebP générés sont droits). Ne jamais agrandir (withoutEnlargement).
 */
export async function encodeWebp(input: Buffer): Promise<EncodedImage> {
  // Vérification magic-bytes : sharp lit l'en-tête réel du fichier. Un binaire
  // non-image (ou un format hors liste) est rejeté en 400 au lieu de faire
  // planter l'encodage en 500.
  let format: string | undefined;
  try {
    format = (await sharp(input, { failOn: 'none' }).metadata()).format;
  } catch {
    throw new BadRequestException(
      'Le fichier fourni n’est pas une image reconnue.',
    );
  }
  if (!format || !ALLOWED_INPUT_FORMATS.has(format)) {
    throw new BadRequestException(
      'Format d’image non supporté (attendu : JPEG, PNG, WebP ou HEIC).',
    );
  }

  try {
    // failOn: 'none' = tolérant aux fichiers légèrement malformés (POC).
    const base = sharp(input, { failOn: 'none' }).rotate();

    const full = await base
      .clone()
      .resize({
        width: FULL_MAX_EDGE,
        height: FULL_MAX_EDGE,
        fit: 'inside',
        withoutEnlargement: true,
      })
      .webp({ quality: 82 })
      .toBuffer();

    const thumbnail = await base
      .clone()
      .resize({
        width: THUMBNAIL_MAX_EDGE,
        height: THUMBNAIL_MAX_EDGE,
        fit: 'inside',
        withoutEnlargement: true,
      })
      .webp({ quality: 70 })
      .toBuffer();

    return { full, thumbnail };
  } catch {
    // Image corrompue ou non décodable malgré un en-tête valide.
    throw new BadRequestException('Image invalide ou corrompue.');
  }
}

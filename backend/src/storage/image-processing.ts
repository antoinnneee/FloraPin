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
 * Réencode une image (JPEG/PNG/HEIC…) en deux WebP : une version pleine
 * résolution bornée à {@link FULL_MAX_EDGE} et une miniature
 * {@link THUMBNAIL_MAX_EDGE}. `rotate()` applique l'orientation EXIF puis la
 * supprime (les WebP générés sont droits). Ne jamais agrandir (withoutEnlargement).
 */
export async function encodeWebp(input: Buffer): Promise<EncodedImage> {
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
}

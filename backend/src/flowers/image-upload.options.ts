import { BadRequestException } from '@nestjs/common';
import { MulterOptions } from '@nestjs/platform-express/multer/interfaces/multer-options.interface';

/** Taille maximale acceptée pour un binaire image téléversé (15 Mo). */
export const MAX_IMAGE_UPLOAD_BYTES = 15 * 1024 * 1024;

/**
 * Types MIME image acceptés (déclaration client). `image/heif` est inclus car
 * les appareils Android/iOS déclarent indifféremment HEIC ou HEIF.
 */
export const ALLOWED_IMAGE_MIME_TYPES: ReadonlySet<string> = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif',
]);

/**
 * Options Multer communes aux endpoints d'upload d'image (fleur + photos).
 *
 * - `fileSize` : un fichier trop gros est coupé par Multer → 413 Payload Too Large.
 * - `fileFilter` : premier filtre, basé sur le type MIME DÉCLARÉ par le client
 *   (falsifiable). La vérification réelle des octets (magic bytes) est faite
 *   ensuite par sharp dans `encodeWebp` (cf. storage/image-processing.ts).
 */
export const imageUploadOptions: MulterOptions = {
  limits: { fileSize: MAX_IMAGE_UPLOAD_BYTES, files: 1 },
  fileFilter: (_req, file, cb) => {
    if (ALLOWED_IMAGE_MIME_TYPES.has(file.mimetype)) {
      cb(null, true);
    } else {
      cb(
        new BadRequestException(
          'Type de fichier non supporté (attendu : JPEG, PNG, WebP ou HEIC).',
        ),
        false,
      );
    }
  },
};

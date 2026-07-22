import { BadRequestException } from '@nestjs/common';
import { createHash } from 'crypto';
import sharp from 'sharp';

export interface ValidatedImageVariants {
  fullSha256: string;
  thumbnailSha256: string;
}

const THUMBNAIL_MAX_EDGE = 400;

/** Valide les WebP produits par l'app sans les réencoder. */
export async function validateClientImageVariants(
  full: Buffer,
  thumbnail: Buffer,
): Promise<ValidatedImageVariants> {
  await validateWebp(full, null, 'image principale');
  await validateWebp(thumbnail, THUMBNAIL_MAX_EDGE, 'miniature');
  return {
    fullSha256: sha256(full),
    thumbnailSha256: sha256(thumbnail),
  };
}

async function validateWebp(
  input: Buffer,
  maxEdge: number | null,
  label: string,
): Promise<void> {
  try {
    const metadata = await sharp(input, { failOn: 'error' }).metadata();
    if (metadata.format !== 'webp' || !metadata.width || !metadata.height) {
      throw new Error('format');
    }
    if (maxEdge !== null && (metadata.width > maxEdge || metadata.height > maxEdge)) {
      throw new BadRequestException(
        `${label} trop grande (bord maximal : ${maxEdge} px).`,
      );
    }
  } catch (error) {
    if (error instanceof BadRequestException) throw error;
    throw new BadRequestException(`${label} WebP invalide.`);
  }
}

function sha256(input: Buffer): string {
  return createHash('sha256').update(input).digest('hex');
}

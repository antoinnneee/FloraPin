import { BadRequestException } from '@nestjs/common';
import sharp from 'sharp';
import { encodeWebp } from './image-processing';

/** Génère une vraie image de test (magic bytes valides) via sharp. */
function makeImage(format: 'png' | 'jpeg' | 'webp' | 'tiff'): Promise<Buffer> {
  const base = sharp({
    create: {
      width: 16,
      height: 16,
      channels: 3,
      background: { r: 200, g: 30, b: 90 },
    },
  });
  return base.toFormat(format).toBuffer();
}

describe('encodeWebp', () => {
  it('réencode une image valide en WebP (pleine résolution + miniature)', async () => {
    const input = await makeImage('png');
    const { full, thumbnail } = await encodeWebp(input);

    expect((await sharp(full).metadata()).format).toBe('webp');
    expect((await sharp(thumbnail).metadata()).format).toBe('webp');
  });

  it('accepte le JPEG (format Android nominal)', async () => {
    const input = await makeImage('jpeg');
    const { full } = await encodeWebp(input);
    expect((await sharp(full).metadata()).format).toBe('webp');
  });

  it('rejette un binaire non-image en 400 (pas de 500 sharp)', async () => {
    const notAnImage = Buffer.from('ceci n’est pas une image', 'utf8');
    await expect(encodeWebp(notAnImage)).rejects.toBeInstanceOf(
      BadRequestException,
    );
  });

  it('rejette un buffer vide en 400', async () => {
    await expect(encodeWebp(Buffer.alloc(0))).rejects.toBeInstanceOf(
      BadRequestException,
    );
  });

  it('rejette un format image hors liste (TIFF) en 400', async () => {
    const tiff = await makeImage('tiff');
    await expect(encodeWebp(tiff)).rejects.toBeInstanceOf(BadRequestException);
  });
});

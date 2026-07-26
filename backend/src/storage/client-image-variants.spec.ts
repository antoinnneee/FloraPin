import { BadRequestException } from '@nestjs/common';
import sharp from 'sharp';
import { validateClientImageVariants } from './client-image-variants';

describe('validateClientImageVariants', () => {
  it('accepte deux WebP et produit des SHA-256 stables', async () => {
    const full = await sharp({
      create: { width: 100, height: 80, channels: 3, background: '#4c8a55' },
    }).webp().toBuffer();
    const thumbnail = await sharp(full).resize(40, 32).webp().toBuffer();
    const result = await validateClientImageVariants(full, thumbnail);
    expect(result.fullSha256).toMatch(/^[a-f0-9]{64}$/);
    expect(result.thumbnailSha256).toMatch(/^[a-f0-9]{64}$/);
    expect(result.fullSha256).not.toBe(result.thumbnailSha256);
  });

  it('refuse un faux WebP', async () => {
    await expect(
      validateClientImageVariants(Buffer.from('x'), Buffer.from('y')),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('conserve la définition originale de l’image principale', async () => {
    const full = await sharp({
      create: { width: 4000, height: 1560, channels: 3, background: '#fff' },
    }).webp().toBuffer();
    const thumbnail = await sharp(full).resize(400, 156).webp().toBuffer();
    // `expect.stringMatching` est typé `any` : on le nomme une fois, typé.
    const sha256 = expect.stringMatching(/^[a-f0-9]{64}$/) as unknown as string;
    await expect(validateClientImageVariants(full, thumbnail)).resolves.toEqual({
      fullSha256: sha256,
      thumbnailSha256: sha256,
    });
  });
});

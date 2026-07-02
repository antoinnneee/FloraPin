import { BadRequestException } from '@nestjs/common';
import {
  imageUploadOptions,
  MAX_IMAGE_UPLOAD_BYTES,
} from './image-upload.options';

/** Invoque le fileFilter Multer avec un mimetype donné et capture le résultat. */
function runFilter(mimetype: string): { error: unknown; accepted: unknown } {
  let error: unknown = null;
  let accepted: unknown = null;
  const filter = imageUploadOptions.fileFilter!;
  filter(
    {} as never,
    { mimetype } as never,
    ((err: unknown, ok: unknown) => {
      error = err;
      accepted = ok;
    }) as never,
  );
  return { error, accepted };
}

describe('imageUploadOptions', () => {
  it('limite la taille des uploads à 15 Mo', () => {
    expect(MAX_IMAGE_UPLOAD_BYTES).toBe(15 * 1024 * 1024);
    expect(imageUploadOptions.limits).toEqual({
      fileSize: MAX_IMAGE_UPLOAD_BYTES,
      files: 1,
    });
  });

  it.each(['image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif'])(
    'accepte %s',
    (mimetype) => {
      const { error, accepted } = runFilter(mimetype);
      expect(error).toBeNull();
      expect(accepted).toBe(true);
    },
  );

  it.each(['application/pdf', 'text/html', 'image/gif', 'video/mp4'])(
    'rejette %s en 400',
    (mimetype) => {
      const { error, accepted } = runFilter(mimetype);
      expect(error).toBeInstanceOf(BadRequestException);
      expect(accepted).toBe(false);
    },
  );
});

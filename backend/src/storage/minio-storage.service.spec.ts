import {
  MinioStorageService,
  ObjectStorageClient,
} from './minio-storage.service';

class FakeClient implements ObjectStorageClient {
  buckets = new Set<string>();
  putCalls: Array<{ bucket: string; key: string; expiry: number }> = [];
  putObjects: Array<{ bucket: string; key: string; size?: number }> = [];
  removed: Array<{ bucket: string; key: string }> = [];

  async putObject(
    bucket: string,
    key: string,
    _body: Buffer,
    size?: number,
  ): Promise<unknown> {
    this.putObjects.push({ bucket, key, size });
    return { etag: 'fake' };
  }

  async removeObject(bucket: string, key: string): Promise<void> {
    this.removed.push({ bucket, key });
  }

  async presignedPutObject(
    bucket: string,
    key: string,
    expiry: number,
  ): Promise<string> {
    this.putCalls.push({ bucket, key, expiry });
    return `https://minio.test/${bucket}/${key}?sig=put`;
  }

  async presignedGetObject(
    bucket: string,
    key: string,
    expiry: number,
  ): Promise<string> {
    return `https://minio.test/${bucket}/${key}?sig=get&e=${expiry}`;
  }

  async bucketExists(bucket: string): Promise<boolean> {
    return this.buckets.has(bucket);
  }

  async makeBucket(bucket: string): Promise<void> {
    this.buckets.add(bucket);
  }
}

describe('MinioStorageService', () => {
  const BUCKET = 'florapin';
  const EXPIRES = 600;
  const DOWNLOAD_EXPIRES = 604800;
  let client: FakeClient;
  let service: MinioStorageService;

  beforeEach(() => {
    client = new FakeClient();
    service = new MinioStorageService(
      client,
      BUCKET,
      EXPIRES,
      'us-east-1',
      client,
      DOWNLOAD_EXPIRES,
    );
  });

  it('génère une clé sous flowers/<owner>/', () => {
    const key = service.buildKey('owner-1', 'jpg');
    expect(key).toMatch(/^flowers\/owner-1\/[0-9a-f-]+\.jpg$/);
  });

  it('présigne un upload PUT avec une expiration plafonnée à 5 min', async () => {
    // Un PUT présigné ne peut imposer ni taille ni type : la fenêtre d'usage
    // est volontairement courte (cap à 300 s), même si la config demande plus.
    const result = await service.presignUpload('flowers/o/x.jpg');
    expect(result.method).toBe('PUT');
    expect(result.expiresIn).toBe(300);
    expect(result.url).toContain('sig=put');
    expect(client.putCalls[0]).toEqual({
      bucket: BUCKET,
      key: 'flowers/o/x.jpg',
      expiry: 300,
    });
  });

  it('conserve une expiration d’upload configurée plus courte que le plafond', async () => {
    const shortService = new MinioStorageService(
      client,
      BUCKET,
      120,
      'us-east-1',
      client,
      DOWNLOAD_EXPIRES,
    );
    const result = await shortService.presignUpload('flowers/o/y.jpg');
    expect(result.expiresIn).toBe(120);
  });

  it('présigne un download GET avec l’expiration longue (persistée par l’app)', async () => {
    const url = await service.presignDownload('flowers/o/x.jpg');
    expect(url).toContain('sig=get');
    // L'app persiste ces URLs en local : elles doivent survivre bien au-delà de
    // l'expiration courte d'upload, sinon 403 « Request has expired ».
    expect(url).toContain(`e=${DOWNLOAD_EXPIRES}`);
  });

  it('crée le bucket au démarrage s’il manque', async () => {
    expect(client.buckets.has(BUCKET)).toBe(false);
    await service.onModuleInit();
    expect(client.buckets.has(BUCKET)).toBe(true);
  });

  it('supprime un objet sur le bon bucket', async () => {
    await service.delete('flowers/o/x.jpg');
    expect(client.removed).toEqual([{ bucket: BUCKET, key: 'flowers/o/x.jpg' }]);
  });
});

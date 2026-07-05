import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { ThrottlerGuard } from '@nestjs/throttler';
import { readFileSync } from 'fs';
import { join } from 'path';
import { Client } from 'pg';
import request from 'supertest';
import {
  GenericContainer,
  StartedTestContainer,
  Wait,
} from 'testcontainers';
import { AppModule } from '../src/app.module';

/**
 * Tests e2e contre une vraie infra (Postgres/PostGIS + MinIO via Testcontainers).
 *
 * Nécessite un démon Docker accessible au runner. Lancer avec `npm run test:e2e`.
 * Couvre : auth (register/login/refresh), upload présigné, listing & requête de
 * sync, et partage avec masquage du GPS.
 */
jest.setTimeout(180_000);

describe('FloraPin API (e2e)', () => {
  let app: INestApplication;
  let postgres: StartedTestContainer;
  let minio: StartedTestContainer;

  beforeAll(async () => {
    postgres = await new GenericContainer('postgis/postgis:16-3.4')
      .withEnvironment({
        POSTGRES_USER: 'florapin',
        POSTGRES_PASSWORD: 'test',
        POSTGRES_DB: 'florapin',
      })
      .withExposedPorts(5432)
      .withWaitStrategy(
        Wait.forLogMessage(
          /database system is ready to accept connections/,
          2,
        ),
      )
      .start();

    minio = await new GenericContainer('minio/minio:latest')
      .withEnvironment({
        MINIO_ROOT_USER: 'florapin',
        MINIO_ROOT_PASSWORD: 'florapin-secret',
      })
      .withCommand(['server', '/data'])
      .withExposedPorts(9000)
      .withWaitStrategy(
        Wait.forHttp('/minio/health/ready', 9000).forStatusCode(200),
      )
      .start();

    // Applique le schéma de référence à la base fraîche.
    const client = new Client({
      host: postgres.getHost(),
      port: postgres.getMappedPort(5432),
      user: 'florapin',
      password: 'test',
      database: 'florapin',
    });
    await client.connect();
    await client.query(
      readFileSync(join(__dirname, '..', 'db', 'schema.sql'), 'utf8'),
    );
    await client.end();

    Object.assign(process.env, {
      DATABASE_HOST: postgres.getHost(),
      DATABASE_PORT: String(postgres.getMappedPort(5432)),
      DATABASE_USER: 'florapin',
      DATABASE_PASSWORD: 'test',
      DATABASE_NAME: 'florapin',
      JWT_ACCESS_SECRET: 'test-access-secret',
      JWT_REFRESH_SECRET: 'test-refresh-secret',
      JWT_ACCESS_TTL: '900s',
      JWT_REFRESH_TTL: '30d',
      STORAGE_DRIVER: 'minio',
      MINIO_ENDPOINT: minio.getHost(),
      MINIO_PORT: String(minio.getMappedPort(9000)),
      MINIO_USE_SSL: 'false',
      MINIO_ACCESS_KEY: 'florapin',
      MINIO_SECRET_KEY: 'florapin-secret',
      MINIO_BUCKET: 'florapin',
      PUSH_DRIVER: 'stub',
    });

    // Neutralise le rate limiting pour les tests fonctionnels : ils enchaînent
    // plusieurs inscriptions depuis la même IP (localhost) et dépasseraient sinon
    // la limite anti-spam de /auth/register (3/min). Les seuils restent actifs en
    // prod ; on ne teste pas le throttler ici.
    const moduleRef = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideGuard(ThrottlerGuard)
      .useValue({ canActivate: () => true })
      .compile();
    app = moduleRef.createNestApplication();
    app.setGlobalPrefix('api/v1');
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    );
    await app.init();
  });

  afterAll(async () => {
    await app?.close();
    await postgres?.stop();
    await minio?.stop();
  });

  const api = () => request(app.getHttpServer());

  /** Inscrit un utilisateur et renvoie ses jetons + son id. */
  async function register(email: string) {
    const res = await api()
      .post('/api/v1/auth/register')
      .send({ email, password: 'password123', displayName: email.split('@')[0] })
      .expect(201);
    expect(res.body.accessToken).toBeDefined();
    return {
      id: res.body.user.id as string,
      access: res.body.accessToken as string,
      refresh: res.body.refreshToken as string,
    };
  }

  it('auth : register, login et refresh', async () => {
    const email = 'alice@example.com';
    const created = await register(email);

    const login = await api()
      .post('/api/v1/auth/login')
      .send({ email, password: 'password123' })
      .expect(200);
    expect(login.body.user.id).toBe(created.id);

    const refreshed = await api()
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: login.body.refreshToken })
      .expect(200);
    expect(refreshed.body.accessToken).toBeDefined();
  });

  it('auth : change-password vérifie l’ancien mdp, garde la session et coupe les autres', async () => {
    const email = 'pat@example.com';
    const created = await register(email);
    // Seconde session (autre appareil).
    const other = await api()
      .post('/api/v1/auth/login')
      .send({ email, password: 'password123' })
      .expect(200);

    // Ancien mot de passe erroné → 401.
    await api()
      .post('/api/v1/auth/change-password')
      .set('Authorization', `Bearer ${created.access}`)
      .send({ oldPassword: 'wrong-pass', newPassword: 'nouveauPass1' })
      .expect(401);

    // Changement réussi → nouvelle paire de jetons.
    const changed = await api()
      .post('/api/v1/auth/change-password')
      .set('Authorization', `Bearer ${created.access}`)
      .send({ oldPassword: 'password123', newPassword: 'nouveauPass1' })
      .expect(200);
    expect(changed.body.accessToken).toBeDefined();
    expect(changed.body.refreshToken).toBeDefined();

    // La session réémise fonctionne, l'ancienne et l'autre appareil sont coupés.
    await api()
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: changed.body.refreshToken })
      .expect(200);
    await api()
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: created.refresh })
      .expect(401);
    await api()
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: other.body.refreshToken })
      .expect(401);

    // Le nouveau mot de passe est requis désormais.
    await api()
      .post('/api/v1/auth/login')
      .send({ email, password: 'password123' })
      .expect(401);
    await api()
      .post('/api/v1/auth/login')
      .send({ email, password: 'nouveauPass1' })
      .expect(200);
  });

  it('flowers : création (upload présigné), listing et URL image', async () => {
    const { access } = await register('bob@example.com');

    const create = await api()
      .post('/api/v1/flowers')
      .set('Authorization', `Bearer ${access}`)
      .send({
        takenAt: '2026-06-21T09:00:00.000Z',
        latitude: 48.8584,
        longitude: 2.2945,
        accuracyM: 5,
        notes: 'tour eiffel',
        species: 'Rosa',
        tags: ['paris'],
      })
      .expect(201);
    expect(create.body.flower.id).toBeDefined();
    expect(create.body.upload.url).toContain('http');

    const flowerId = create.body.flower.id as string;

    const list = await api()
      .get('/api/v1/flowers')
      .set('Authorization', `Bearer ${access}`)
      .expect(200);
    expect(list.body.map((f: { id: string }) => f.id)).toContain(flowerId);

    const imageUrl = await api()
      .get(`/api/v1/flowers/${flowerId}/image-url`)
      .set('Authorization', `Bearer ${access}`)
      .expect(200);
    expect(imageUrl.body.imageUrl).toContain('http');
  });

  it('sync : pull renvoie les fleurs de l’utilisateur', async () => {
    const { access } = await register('carol@example.com');
    await api()
      .post('/api/v1/flowers')
      .set('Authorization', `Bearer ${access}`)
      .send({ takenAt: '2026-06-21T10:00:00.000Z' })
      .expect(201);

    const pull = await api()
      .get('/api/v1/sync')
      .set('Authorization', `Bearer ${access}`)
      .expect(200);
    expect(pull.body.flowers.length).toBeGreaterThanOrEqual(1);
    expect(pull.body.serverTime).toBeDefined();
  });

  it('partage : un ami voit la fleur, GPS masqué', async () => {
    const owner = await register('dan@example.com');
    const friend = await register('erin@example.com');

    await api()
      .post('/api/v1/flowers')
      .set('Authorization', `Bearer ${owner.access}`)
      .send({
        takenAt: '2026-06-21T11:00:00.000Z',
        latitude: 45.0,
        longitude: 1.0,
      })
      .expect(201);

    // Demande d'ami acceptée.
    const reqRes = await api()
      .post('/api/v1/friendships')
      .set('Authorization', `Bearer ${owner.access}`)
      .send({ addresseeId: friend.id })
      .expect(201);
    await api()
      .post(`/api/v1/friendships/${reqRes.body.id}/accept`)
      .set('Authorization', `Bearer ${friend.access}`)
      .expect(200);

    // Partage de toutes mes fleurs sans GPS.
    await api()
      .post('/api/v1/shares')
      .set('Authorization', `Bearer ${owner.access}`)
      .send({ friendId: friend.id, scope: 'all', includeGps: false })
      .expect(201);

    const shared = await api()
      .get('/api/v1/shared')
      .set('Authorization', `Bearer ${friend.access}`)
      .expect(200);
    expect(shared.body.length).toBeGreaterThanOrEqual(1);
    expect(shared.body[0].latitude).toBeNull();
  });

  it('partage réseau : les amis présents ET futurs voient la fleur', async () => {
    const owner = await register('frank@example.com');
    const early = await register('grace@example.com');
    const later = await register('heidi@example.com');

    await api()
      .post('/api/v1/flowers')
      .set('Authorization', `Bearer ${owner.access}`)
      .send({ takenAt: '2026-06-22T11:00:00.000Z', latitude: 45.0, longitude: 1.0 })
      .expect(201);

    // Amitié établie AVANT le partage.
    await befriend(owner, early);

    // Un seul partage réseau (audience='all_friends'), pas un par ami.
    const res = await api()
      .post('/api/v1/shares/all-friends')
      .set('Authorization', `Bearer ${owner.access}`)
      .send({ scope: 'all', includeGps: true })
      .expect(201);
    expect(res.body.id).toBeDefined();
    expect(res.body.audience).toBe('all_friends');
    expect(res.body.sharedWith).toBeNull();

    // L'ami présent au moment du partage voit la fleur.
    const earlyShared = await api()
      .get('/api/v1/shared')
      .set('Authorization', `Bearer ${early.access}`)
      .expect(200);
    expect(earlyShared.body.length).toBeGreaterThanOrEqual(1);

    // Amitié établie APRÈS le partage : l'ami doit tout de même voir la fleur.
    await befriend(owner, later);
    const laterShared = await api()
      .get('/api/v1/shared')
      .set('Authorization', `Bearer ${later.access}`)
      .expect(200);
    expect(laterShared.body.length).toBeGreaterThanOrEqual(1);
  });

  it('feed : pagination keyset descendante avec curseur `before`', async () => {
    const owner = await register('ivan@example.com');
    const viewer = await register('judy@example.com');
    await befriend(owner, viewer);

    // Trois fleurs diffusées au réseau, dates croissantes.
    const isos = [
      '2026-06-20T08:00:00.000Z',
      '2026-06-21T08:00:00.000Z',
      '2026-06-22T08:00:00.000Z',
    ];
    for (const takenAt of isos) {
      await api()
        .post('/api/v1/flowers')
        .set('Authorization', `Bearer ${owner.access}`)
        .send({ takenAt, visibility: 'friends', feedIncludeGps: true })
        .expect(201);
    }

    // Page 1 : les 2 plus récentes (tri date DESC).
    const page1 = await api()
      .get('/api/v1/feed?limit=2')
      .set('Authorization', `Bearer ${viewer.access}`)
      .expect(200);
    expect(page1.body).toHaveLength(2);
    const last = page1.body[1];

    // Page 2 : curseur = dernière fleur de la page 1 → la 3e, plus ancienne.
    const cursor = `${last.createdAt}_${last.id}`;
    const page2 = await api()
      .get(`/api/v1/feed?limit=2&before=${encodeURIComponent(cursor)}`)
      .set('Authorization', `Bearer ${viewer.access}`)
      .expect(200);
    expect(page2.body).toHaveLength(1);
    // Aucun chevauchement entre les deux pages.
    const ids1 = page1.body.map((f: { id: string }) => f.id);
    expect(ids1).not.toContain(page2.body[0].id);

    // `before` est incompatible avec le tri par cœurs.
    await api()
      .get(`/api/v1/feed?sort=likes&before=${encodeURIComponent(cursor)}`)
      .set('Authorization', `Bearer ${viewer.access}`)
      .expect(400);
  });

  /** Crée une amitié acceptée entre [owner] et [friend]. */
  async function befriend(
    owner: { access: string },
    friend: { id: string; access: string },
  ): Promise<void> {
    const reqRes = await api()
      .post('/api/v1/friendships')
      .set('Authorization', `Bearer ${owner.access}`)
      .send({ addresseeId: friend.id })
      .expect(201);
    await api()
      .post(`/api/v1/friendships/${reqRes.body.id}/accept`)
      .set('Authorization', `Bearer ${friend.access}`)
      .expect(200);
  }
});

import { Test } from '@nestjs/testing';
import { FlowerResponse } from '../flowers/flowers.service';
import { SharesService } from '../shares/shares.service';
import { FeedService } from './feed.service';

function flower(
  id: string,
  createdAt: string,
  overrides: Partial<FlowerResponse> = {},
): FlowerResponse {
  return {
    id,
    ownerId: 'o',
    imageUrl: 'u',
    thumbnailUrl: null,
    latitude: null,
    longitude: null,
    accuracyM: null,
    takenAt: new Date(createdAt),
    notes: '',
    visibility: 'friends',
    feedIncludeGps: true,
    needsIdentification: false,
    species: null,
    speciesId: null,
    speciesRef: null,
    tags: [],
    photos: [],
    likeCount: 0,
    likedByMe: false,
    createdAt: new Date(createdAt),
    updatedAt: new Date(createdAt),
    ...overrides,
  };
}

describe('FeedService', () => {
  let feed: FeedService;
  let shared: FlowerResponse[];
  let broadcast: FlowerResponse[];

  beforeEach(async () => {
    shared = [
      flower('a', '2026-06-20T10:00:00Z'),
      flower('b', '2026-06-21T10:00:00Z'),
      flower('c', '2026-06-19T10:00:00Z'),
    ];
    broadcast = [];
    const moduleRef = await Test.createTestingModule({
      providers: [
        FeedService,
        {
          provide: SharesService,
          useValue: {
            sharedWithMe: async () => shared,
            broadcastWithMe: async () => broadcast,
          },
        },
      ],
    }).compile();
    feed = moduleRef.get(FeedService);
  });

  it('trie du plus récent au plus ancien', async () => {
    const result = await feed.getFeed('viewer');
    expect(result.map((f) => f.id)).toEqual(['b', 'a', 'c']);
  });

  it('filtre par since', async () => {
    const result = await feed.getFeed('viewer', new Date('2026-06-20T00:00:00Z'));
    expect(result.map((f) => f.id)).toEqual(['b', 'a']);
  });

  it('applique la limite', async () => {
    const result = await feed.getFeed('viewer', undefined, 1);
    expect(result.map((f) => f.id)).toEqual(['b']);
  });

  it('inclut les fleurs diffusées (broadcast) au feed', async () => {
    broadcast = [flower('d', '2026-06-22T10:00:00Z')];
    const result = await feed.getFeed('viewer');
    expect(result.map((f) => f.id)).toEqual(['d', 'b', 'a', 'c']);
  });

  it('trie par cœurs quand sort=likes (date départage)', async () => {
    shared = [
      flower('a', '2026-06-20T10:00:00Z', { likeCount: 1 }),
      flower('b', '2026-06-21T10:00:00Z', { likeCount: 5 }),
      flower('c', '2026-06-19T10:00:00Z', { likeCount: 5 }),
    ];
    const result = await feed.getFeed('viewer', undefined, 50, 'likes');
    // b et c ont 5 cœurs : b (plus récent) devant c ; a (1 cœur) en dernier.
    expect(result.map((f) => f.id)).toEqual(['b', 'c', 'a']);
  });

  it('pagine avec le curseur `before` : ne renvoie que les plus anciennes', async () => {
    // Curseur = fleur 'b' (2026-06-21) : on attend 'a' puis 'c' (plus anciennes).
    const result = await feed.getFeed(
      'viewer',
      undefined,
      50,
      'date',
      '2026-06-21T10:00:00.000Z_b',
    );
    expect(result.map((f) => f.id)).toEqual(['a', 'c']);
  });

  it('curseur `before` : départage les dates égales par id (id < curseur)', async () => {
    shared = [
      flower('a', '2026-06-20T10:00:00Z'),
      flower('m', '2026-06-20T10:00:00Z'),
      flower('z', '2026-06-20T10:00:00Z'),
    ];
    // Même date, curseur id='m' → seules les fleurs d'id < 'm' suivent : 'a'.
    const result = await feed.getFeed(
      'viewer',
      undefined,
      50,
      'date',
      '2026-06-20T10:00:00.000Z_m',
    );
    expect(result.map((f) => f.id)).toEqual(['a']);
  });

  it('refuse le curseur `before` avec sort=likes', async () => {
    await expect(
      feed.getFeed('viewer', undefined, 50, 'likes', '2026-06-21T10:00:00.000Z_b'),
    ).rejects.toThrow();
  });

  it('rejette un curseur `before` malformé', async () => {
    await expect(
      feed.getFeed('viewer', undefined, 50, 'date', 'pas-un-curseur'),
    ).rejects.toThrow();
  });

  it('déduplique partage ciblé + broadcast en gardant la variante avec GPS', async () => {
    // Même fleur 'b' : ciblée sans GPS, diffusée avec GPS → on garde le GPS.
    shared = [flower('b', '2026-06-21T10:00:00Z', { latitude: null, longitude: null })];
    broadcast = [
      flower('b', '2026-06-21T10:00:00Z', { latitude: 48.85, longitude: 2.29 }),
    ];
    const result = await feed.getFeed('viewer');
    expect(result).toHaveLength(1);
    expect(result[0].latitude).toBe(48.85);
  });
});

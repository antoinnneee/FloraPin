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

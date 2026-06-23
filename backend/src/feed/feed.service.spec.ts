import { Test } from '@nestjs/testing';
import { FlowerResponse } from '../flowers/flowers.service';
import { SharesService } from '../shares/shares.service';
import { FeedService } from './feed.service';

function flower(id: string, createdAt: string): FlowerResponse {
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
    species: null,
    speciesId: null,
    speciesRef: null,
    tags: [],
    photos: [],
    createdAt: new Date(createdAt),
    updatedAt: new Date(createdAt),
  };
}

describe('FeedService', () => {
  let feed: FeedService;
  let shared: FlowerResponse[];

  beforeEach(async () => {
    shared = [
      flower('a', '2026-06-20T10:00:00Z'),
      flower('b', '2026-06-21T10:00:00Z'),
      flower('c', '2026-06-19T10:00:00Z'),
    ];
    const moduleRef = await Test.createTestingModule({
      providers: [
        FeedService,
        { provide: SharesService, useValue: { sharedWithMe: async () => shared } },
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
});

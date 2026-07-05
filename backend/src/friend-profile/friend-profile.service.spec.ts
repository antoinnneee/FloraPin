import { BadRequestException, NotFoundException } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse } from '../flowers/flowers.service';
import { FriendProfileService } from './friend-profile.service';

const VIEWER = 'viewer';
const FRIEND = 'friend';

/** Fabrique une FlowerResponse minimale pour les tests (seuls les champs lus). */
function flowerResponse(over: Partial<FlowerResponse>): FlowerResponse {
  return {
    id: randomUUID(),
    ownerId: FRIEND,
    imageUrl: 'u',
    thumbnailUrl: null,
    latitude: null,
    longitude: null,
    accuracyM: null,
    takenAt: new Date(),
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
    reactionCounts: {},
    myReaction: null,
    commentCount: 0,
    shareId: null,
    sharedAt: null,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...over,
  };
}

describe('FriendProfileService', () => {
  let service: FriendProfileService;
  let myFlowers: Flower[];
  let acceptedBetween: jest.Mock;
  let acceptedFriendIds: jest.Mock;
  let sharedWithMe: jest.Mock;
  let broadcastWithMe: jest.Mock;

  beforeEach(() => {
    myFlowers = [];
    acceptedBetween = jest.fn(async () => ({
      id: 'fr1',
      createdAt: new Date('2026-05-10T00:00:00Z'),
    }));
    acceptedFriendIds = jest.fn(async () => []);
    sharedWithMe = jest.fn(async () => []);
    broadcastWithMe = jest.fn(async () => []);

    const flowersRepo = {
      find: async (opts: { where: { ownerId: string } }) =>
        myFlowers.filter((f) => f.ownerId === opts.where.ownerId),
    };
    const users = {
      findById: async (id: string) =>
        id === FRIEND
          ? {
              id: FRIEND,
              displayName: 'Marie',
              createdAt: new Date('2026-01-01T00:00:00Z'),
              avatarKey: null,
            }
          : null,
      avatarUrl: async () => null,
    };
    const friendships = { acceptedBetween, acceptedFriendIds };
    const shares = { sharedWithMe, broadcastWithMe };

    service = new FriendProfileService(
      flowersRepo as never,
      users as never,
      friendships as never,
      shares as never,
    );
  });

  it('refuse la consultation de son propre profil', async () => {
    await expect(service.getProfile(VIEWER, VIEWER)).rejects.toBeInstanceOf(
      BadRequestException,
    );
  });

  it('404 pour un utilisateur inexistant', async () => {
    await expect(
      service.getProfile(VIEWER, 'inconnu'),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('404 (anti-énumération) quand il n’y a pas d’amitié acceptée', async () => {
    acceptedBetween.mockResolvedValue(null);
    await expect(service.getProfile(VIEWER, FRIEND)).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });

  it('renvoie l’ancienneté d’amitié et l’inscription', async () => {
    const profile = await service.getProfile(VIEWER, FRIEND);
    expect(profile.displayName).toBe('Marie');
    expect(profile.friendsSince).toEqual(new Date('2026-05-10T00:00:00Z'));
    expect(profile.memberSince).toEqual(new Date('2026-01-01T00:00:00Z'));
  });

  it('ne renvoie que les fleurs de l’ami ciblé, dédupliquées', async () => {
    const f1 = flowerResponse({ ownerId: FRIEND, createdAt: new Date('2026-06-01') });
    const f2 = flowerResponse({ ownerId: FRIEND, createdAt: new Date('2026-06-03') });
    const other = flowerResponse({ ownerId: 'autre' });
    broadcastWithMe.mockResolvedValue([f1, other]);
    // f1 re-apparaît en partage ciblé (shareId) + une seconde fleur de l'ami.
    sharedWithMe.mockResolvedValue([{ ...f1, shareId: 's1' }, f2]);

    const profile = await service.getProfile(VIEWER, FRIEND);
    expect(profile.visibleFlowerCount).toBe(2);
    // Tri par date décroissante : f2 (03/06) avant f1 (01/06).
    expect(profile.sharedFlowers.map((f) => f.id)).toEqual([f2.id, f1.id]);
    // La variante partagée (shareId) prime sur la version diffusée.
    expect(profile.sharedFlowers.find((f) => f.id === f1.id)?.shareId).toBe('s1');
  });

  it('compte les amis en commun (hors les deux intéressés)', async () => {
    acceptedFriendIds.mockImplementation(async (id: string) =>
      id === VIEWER ? [FRIEND, 'a', 'b'] : [VIEWER, 'a', 'c'],
    );
    const profile = await service.getProfile(VIEWER, FRIEND);
    expect(profile.mutualFriendsCount).toBe(1); // seul 'a' est commun
  });

  it('calcule les espèces communes depuis les fleurs visibles', async () => {
    myFlowers = [
      { ownerId: VIEWER, species: 'Rosa canina' } as Flower,
      { ownerId: VIEWER, species: 'Bellis perennis' } as Flower,
    ];
    broadcastWithMe.mockResolvedValue([
      flowerResponse({ ownerId: FRIEND, species: 'rosa CANINA' }), // commune (casse ignorée)
      flowerResponse({ ownerId: FRIEND, species: 'Papaver rhoeas' }), // pas dans mon herbier
    ]);
    const profile = await service.getProfile(VIEWER, FRIEND);
    expect(profile.commonSpecies).toEqual(['rosa CANINA']);
  });
});

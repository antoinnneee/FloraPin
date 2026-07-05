import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse } from '../flowers/flowers.service';
import { NotificationsService } from '../notifications/notifications.service';
import { SharesService } from '../shares/shares.service';
import { UsersService } from '../users/users.service';
import { CommentsService } from './comments.service';
import { FlowerComment } from './flower-comment.entity';

class FakeFlowerRepo {
  store = new Map<string, Flower>();
  seed(f: Partial<Flower>): Flower {
    const flower = { ...f } as Flower;
    this.store.set(flower.id, flower);
    return flower;
  }
  async findOne(opts: { where: { id: string } }): Promise<Flower | null> {
    return this.store.get(opts.where.id) ?? null;
  }
}

class FakeCommentRepo {
  store = new Map<string, FlowerComment>();
  create(obj: Partial<FlowerComment>): FlowerComment {
    return { ...obj } as FlowerComment;
  }
  async save(obj: FlowerComment): Promise<FlowerComment> {
    if (!obj.id) obj.id = randomUUID();
    obj.createdAt ??= new Date();
    this.store.set(obj.id, { ...obj });
    return obj;
  }
  async find(opts: { where: { flowerId: string } }): Promise<FlowerComment[]> {
    return [...this.store.values()].filter(
      (c) => c.flowerId === opts.where.flowerId,
    );
  }
  async findOne(opts: {
    where: { id: string; flowerId: string };
  }): Promise<FlowerComment | null> {
    const found = this.store.get(opts.where.id);
    return found && found.flowerId === opts.where.flowerId ? found : null;
  }
  async delete(id: string): Promise<void> {
    this.store.delete(id);
  }
}

const OWNER = 'owner';
const FRIEND = 'friend';
const STRANGER = 'stranger';
const FLOWER = 'flower-1';

describe('CommentsService', () => {
  let service: CommentsService;
  let flowerRepo: FakeFlowerRepo;
  let commentRepo: FakeCommentRepo;
  let sharedWithFriend: FlowerResponse[];
  let notified: Array<{ userId: string; type: string }>;

  beforeEach(async () => {
    flowerRepo = new FakeFlowerRepo();
    commentRepo = new FakeCommentRepo();
    sharedWithFriend = [{ id: FLOWER } as FlowerResponse];
    notified = [];

    const moduleRef = await Test.createTestingModule({
      providers: [
        CommentsService,
        { provide: getRepositoryToken(FlowerComment), useValue: commentRepo },
        { provide: getRepositoryToken(Flower), useValue: flowerRepo },
        {
          provide: SharesService,
          useValue: {
            // La fleur est partagée avec FRIEND (ciblé) mais pas STRANGER.
            isVisibleTo: async (viewerId: string, flower: Flower) =>
              flower.ownerId === viewerId ||
              (viewerId === FRIEND &&
                sharedWithFriend.some((f) => f.id === flower.id)),
            // FRIEND fait partie du réseau d'amis sollicité par une demande
            // d'identification ; STRANGER non.
            needsIdentificationVisibleTo: async (
              viewerId: string,
              flower: Flower,
            ) => flower.needsIdentification === true && viewerId === FRIEND,
          },
        },
        {
          provide: NotificationsService,
          useValue: {
            // Le service commente désormais via createSafe (best-effort) ; les
            // deux pointent vers le même enregistrement pour les assertions.
            create: async (userId: string, type: string) => {
              notified.push({ userId, type });
            },
            createSafe: async (userId: string, type: string) => {
              notified.push({ userId, type });
            },
          },
        },
        {
          provide: UsersService,
          useValue: {
            findById: async (id: string) => ({ id, displayName: `Nom ${id}` }),
            // Batch des auteurs (évite le N+1 findById par commentaire).
            findByIds: async (ids: string[]) =>
              ids.map((id) => ({ id, displayName: `Nom ${id}` })),
          },
        },
      ],
    }).compile();
    service = moduleRef.get(CommentsService);
  });

  it('un ami poste un commentaire et notifie le propriétaire', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const comment = await service.post(FRIEND, FLOWER, 'Magnifique !');
    expect(comment.body).toBe('Magnifique !');
    expect(comment.authorName).toBe('Nom friend');
    expect(notified).toContainEqual({
      userId: OWNER,
      type: 'flower_commented',
    });
  });

  it('ne notifie pas sur auto-commentaire du propriétaire', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    await service.post(OWNER, FLOWER, 'Note perso');
    expect(notified).toHaveLength(0);
  });

  it('refuse de commenter une fleur non accessible', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    await expect(
      service.post(STRANGER, FLOWER, 'Coucou'),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('un ami sollicité peut commenter une fleur à identifier non partagée', async () => {
    // Fleur ouverte à l'identification, SANS partage ciblé ni publication au
    // flux : FRIEND ne la « voit » pas au sens isVisibleTo, mais participe à la
    // demande d'identification → il peut discuter.
    const toIdentify = 'flower-id';
    flowerRepo.seed({ id: toIdentify, ownerId: OWNER, needsIdentification: true });
    const comment = await service.post(
      FRIEND,
      toIdentify,
      'Tu peux ajouter une photo des feuilles ?',
    );
    expect(comment.body).toBe('Tu peux ajouter une photo des feuilles ?');
    expect(notified).toContainEqual({
      userId: OWNER,
      type: 'flower_commented',
    });
  });

  it('refuse un tiers hors réseau sur une fleur à identifier', async () => {
    const toIdentify = 'flower-id';
    flowerRepo.seed({ id: toIdentify, ownerId: OWNER, needsIdentification: true });
    await expect(
      service.post(STRANGER, toIdentify, 'Coucou'),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('liste les commentaires chronologiquement avec droit de suppression', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    await service.post(FRIEND, FLOWER, 'Premier');
    await service.post(OWNER, FLOWER, 'Réponse');

    const owners = await service.listForFlower(OWNER, FLOWER);
    expect(owners.map((c) => c.body)).toEqual(['Premier', 'Réponse']);
    // Le propriétaire peut tout supprimer.
    expect(owners.every((c) => c.canDelete)).toBe(true);

    const friends = await service.listForFlower(FRIEND, FLOWER);
    // L'ami ne peut supprimer que son propre commentaire.
    expect(friends.find((c) => c.body === 'Premier')!.canDelete).toBe(true);
    expect(friends.find((c) => c.body === 'Réponse')!.canDelete).toBe(false);
  });

  it('l’auteur édite son commentaire et marque editedAt', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const comment = await service.post(FRIEND, FLOWER, 'Fôte');
    expect(comment.editedAt).toBeNull();
    const edited = await service.update(FRIEND, FLOWER, comment.id, 'Faute');
    expect(edited.body).toBe('Faute');
    expect(edited.editedAt).toBeInstanceOf(Date);
    // Persisté : la relecture reflète l'édition.
    const listed = await service.listForFlower(OWNER, FLOWER);
    expect(listed[0].body).toBe('Faute');
    expect(listed[0].editedAt).toBeInstanceOf(Date);
  });

  it('refuse l’édition par le propriétaire (non-auteur)', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const comment = await service.post(FRIEND, FLOWER, 'À moi');
    await expect(
      service.update(OWNER, FLOWER, comment.id, 'Modéré'),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('refuse l’édition par un tiers', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const comment = await service.post(FRIEND, FLOWER, 'Intouchable');
    await expect(
      service.update(STRANGER, FLOWER, comment.id, 'Piraté'),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('l’auteur supprime son commentaire', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const comment = await service.post(FRIEND, FLOWER, 'À retirer');
    await service.delete(FRIEND, FLOWER, comment.id);
    expect(await service.listForFlower(OWNER, FLOWER)).toHaveLength(0);
  });

  it('le propriétaire supprime le commentaire d’un ami', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const comment = await service.post(FRIEND, FLOWER, 'Modéré');
    await service.delete(OWNER, FLOWER, comment.id);
    expect(await service.listForFlower(OWNER, FLOWER)).toHaveLength(0);
  });

  it('refuse la suppression par un tiers', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const comment = await service.post(FRIEND, FLOWER, 'Intouchable');
    await expect(
      service.delete(STRANGER, FLOWER, comment.id),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('renvoie 404 sur une fleur inexistante', async () => {
    await expect(
      service.listForFlower(OWNER, 'absente'),
    ).rejects.toBeInstanceOf(NotFoundException);
  });
});

import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse } from '../flowers/flowers.service';
import { FriendshipsService } from '../friendships/friendships.service';
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
  // Capture enrichie (avec `data`) pour les assertions sur les mentions.
  let notifiedData: Array<{ userId: string; type: string; data: unknown }>;
  // Réseau d'amis acceptés par utilisateur (pour restreindre les mentions).
  let friendsByUser: Map<string, string[]>;
  // Destinataires qui voient la fleur via un partage.
  let viewersWithAccess: Set<string>;

  beforeEach(async () => {
    flowerRepo = new FakeFlowerRepo();
    commentRepo = new FakeCommentRepo();
    sharedWithFriend = [{ id: FLOWER } as FlowerResponse];
    notified = [];
    notifiedData = [];
    friendsByUser = new Map();
    viewersWithAccess = new Set([FRIEND]);

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
              (viewersWithAccess.has(viewerId) &&
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
            createSafe: async (
              userId: string,
              type: string,
              data: unknown,
            ) => {
              notified.push({ userId, type });
              notifiedData.push({ userId, type, data });
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
        {
          provide: FriendshipsService,
          useValue: {
            acceptedFriendIds: async (userId: string) =>
              friendsByUser.get(userId) ?? [],
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

  it('répond à un commentaire : cite l’auteur et le texte du parent', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const root = await service.post(OWNER, FLOWER, 'Quelle espèce ?');
    const reply = await service.post(FRIEND, FLOWER, 'Une rose', root.id);
    expect(reply.replyToId).toBe(root.id);
    expect(reply.replyToAuthorName).toBe('Nom owner');
    expect(reply.replyToBody).toBe('Quelle espèce ?');
  });

  it('aplatit une réponse à une réponse vers la racine', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const root = await service.post(OWNER, FLOWER, 'Racine');
    const reply = await service.post(FRIEND, FLOWER, 'Réponse 1', root.id);
    // On répond à la réponse : le fil reste à un niveau → pointe la racine.
    const nested = await service.post(OWNER, FLOWER, 'Réponse 2', reply.id);
    expect(nested.replyToId).toBe(root.id);
    expect(nested.replyToBody).toBe('Racine');
  });

  it('expose la citation à la relecture de la liste', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const root = await service.post(OWNER, FLOWER, 'Question');
    await service.post(FRIEND, FLOWER, 'Réponse', root.id);
    const listed = await service.listForFlower(OWNER, FLOWER);
    const reply = listed.find((c) => c.body === 'Réponse')!;
    expect(reply.replyToId).toBe(root.id);
    expect(reply.replyToAuthorName).toBe('Nom owner');
    expect(reply.replyToBody).toBe('Question');
  });

  it('refuse de répondre à un commentaire d’une autre fleur', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    const other = 'flower-2';
    flowerRepo.seed({ id: other, ownerId: OWNER });
    const onOther = await service.post(OWNER, other, 'Ailleurs');
    await expect(
      service.post(OWNER, FLOWER, 'Hors sujet', onOther.id),
    ).rejects.toBeInstanceOf(NotFoundException);
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

  it('notifie un ami mentionné et résout son nom pour l’affichage', async () => {
    const MENTIONED = randomUUID();
    friendsByUser.set(OWNER, [MENTIONED]);
    viewersWithAccess.add(MENTIONED);
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });

    const comment = await service.post(
      OWNER,
      FLOWER,
      `Regarde ça @[${MENTIONED}] !`,
    );

    expect(notified).toContainEqual({
      userId: MENTIONED,
      type: 'comment_mention',
    });
    const event = notifiedData.find((n) => n.type === 'comment_mention')!;
    expect(event.data).toMatchObject({
      flowerId: FLOWER,
      commentId: comment.id,
      byUserId: OWNER,
    });
    // Rendu par id : le nom d'affichage est résolu au moment de la réponse.
    expect(comment.mentions).toEqual([
      { userId: MENTIONED, displayName: `Nom ${MENTIONED}` },
    ]);
  });

  it('ne notifie pas un ami mentionné qui n’a pas accès à la fleur', async () => {
    const MENTIONED = randomUUID();
    friendsByUser.set(OWNER, [MENTIONED]);
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });

    await service.post(OWNER, FLOWER, `Coucou @[${MENTIONED}]`);

    expect(
      notified.some(
        (notification) =>
          notification.userId === MENTIONED &&
          notification.type === 'comment_mention',
      ),
    ).toBe(false);
  });

  it('ne notifie pas un mentionné hors du réseau d’amis', async () => {
    const OUTSIDER = randomUUID();
    friendsByUser.set(OWNER, []); // aucun ami accepté
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });

    const comment = await service.post(OWNER, FLOWER, `Coucou @[${OUTSIDER}]`);

    expect(notified.some((n) => n.type === 'comment_mention')).toBe(false);
    // La mention reste résolue pour l'affichage même sans notification.
    expect(comment.mentions.map((m) => m.userId)).toEqual([OUTSIDER]);
  });

  it('à l’édition, ne notifie que les mentions ajoutées', async () => {
    const A = randomUUID();
    const B = randomUUID();
    friendsByUser.set(FRIEND, [A, B]);
    viewersWithAccess.add(A);
    viewersWithAccess.add(B);
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });

    const comment = await service.post(FRIEND, FLOWER, `Salut @[${A}]`);
    const edited = await service.update(
      FRIEND,
      FLOWER,
      comment.id,
      `Salut @[${A}] et @[${B}]`,
    );

    // A mentionné à la création, B ajouté à l'édition : chacun une seule fois.
    const mentionEvents = notifiedData.filter(
      (n) => n.type === 'comment_mention',
    );
    expect(mentionEvents.map((n) => n.userId)).toEqual([A, B]);
    expect(edited.mentions.map((m) => m.userId)).toEqual([A, B]);
  });

  it('expose les mentions à la relecture de la liste', async () => {
    const M = randomUUID();
    friendsByUser.set(OWNER, [M]);
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER });
    await service.post(OWNER, FLOWER, `Hello @[${M}]`);

    const listed = await service.listForFlower(OWNER, FLOWER);
    expect(listed[0].mentions).toEqual([
      { userId: M, displayName: `Nom ${M}` },
    ]);
  });
});

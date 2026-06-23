import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { PresignedUpload, StorageService } from '../storage/storage.service';
import { FlowerPhoto } from './flower-photo.entity';
import { PhotoResponse } from './flowers.service';
import { Flower } from './flower.entity';

export interface AddPhotoResult {
  photo: PhotoResponse;
  upload: PresignedUpload;
}

/** Gestion des photos d'une fleur (NODE-106) : ajout, suppression, ordre, couverture. */
@Injectable()
export class FlowerPhotosService {
  constructor(
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    @InjectRepository(FlowerPhoto)
    private readonly photos: Repository<FlowerPhoto>,
    private readonly storage: StorageService,
  ) {}

  /** Ajoute une photo et renvoie une URL présignée pour téléverser le binaire. */
  async add(ownerId: string, flowerId: string): Promise<AddPhotoResult> {
    await this.requireFlower(ownerId, flowerId);
    const existing = await this.photos.find({ where: { flowerId } });
    const imageKey = this.storage.buildKey(ownerId, 'jpg');
    const photo = await this.photos.save(
      this.photos.create({
        flowerId,
        imageKey,
        position: nextPosition(existing),
        // Première photo d'une fleur sans photo : couverture par défaut.
        isCover: existing.length === 0,
      }),
    );
    const upload = await this.storage.presignUpload(imageKey);
    return { photo: await this.toResponse(photo), upload };
  }

  async remove(
    ownerId: string,
    flowerId: string,
    photoId: string,
  ): Promise<void> {
    await this.requireFlower(ownerId, flowerId);
    const photo = await this.requirePhoto(flowerId, photoId);
    await this.photos.remove(photo);

    // Si on retire la couverture, en promouvoir une autre (la première restante).
    if (photo.isCover) {
      const remaining = await this.photos.find({
        where: { flowerId },
        order: { position: 'ASC' },
      });
      const next = remaining[0];
      if (next) {
        next.isCover = true;
        await this.photos.save(next);
        await this.flowers.update(flowerId, { imageKey: next.imageKey });
      }
    }
  }

  /** Réordonne les photos selon l'ordre des ids fournis (position = index). */
  async reorder(
    ownerId: string,
    flowerId: string,
    photoIds: string[],
  ): Promise<PhotoResponse[]> {
    await this.requireFlower(ownerId, flowerId);
    const photos = await this.photos.find({ where: { flowerId } });
    const byId = new Map(photos.map((p) => [p.id, p]));
    if (photoIds.length !== photos.length ||
        photoIds.some((id) => !byId.has(id))) {
      throw new BadRequestException(
        'La liste doit contenir exactement les photos de la fleur.',
      );
    }
    await Promise.all(
      photoIds.map((id, index) => this.photos.update(id, { position: index })),
    );
    return this.list(flowerId);
  }

  /** Définit la photo de couverture (et synchronise flowers.image_key). */
  async setCover(
    ownerId: string,
    flowerId: string,
    photoId: string,
  ): Promise<PhotoResponse[]> {
    await this.requireFlower(ownerId, flowerId);
    const photo = await this.requirePhoto(flowerId, photoId);
    await this.photos.update({ flowerId }, { isCover: false });
    await this.photos.update(photo.id, { isCover: true });
    await this.flowers.update(flowerId, { imageKey: photo.imageKey });
    return this.list(flowerId);
  }

  async list(flowerId: string): Promise<PhotoResponse[]> {
    const photos = await this.photos.find({
      where: { flowerId },
      order: { position: 'ASC' },
    });
    return Promise.all(photos.map((p) => this.toResponse(p)));
  }

  private async requireFlower(ownerId: string, flowerId: string): Promise<Flower> {
    const flower = await this.flowers.findOne({
      where: { id: flowerId, ownerId },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    return flower;
  }

  private async requirePhoto(
    flowerId: string,
    photoId: string,
  ): Promise<FlowerPhoto> {
    const photo = await this.photos.findOne({
      where: { id: photoId, flowerId },
    });
    if (!photo) {
      throw new NotFoundException('Photo introuvable.');
    }
    return photo;
  }

  private async toResponse(photo: FlowerPhoto): Promise<PhotoResponse> {
    return {
      id: photo.id,
      url: await this.storage.presignDownload(photo.imageKey),
      position: photo.position,
      isCover: photo.isCover,
    };
  }
}

function nextPosition(photos: FlowerPhoto[]): number {
  return photos.reduce((max, p) => Math.max(max, p.position), -1) + 1;
}

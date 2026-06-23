import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { Album } from './album.entity';
import { CreateAlbumDto, UpdateAlbumDto } from './dto/album.dto';

export interface AlbumResponse {
  id: string;
  ownerId: string;
  name: string;
  flowerIds: string[];
  createdAt: Date;
}

@Injectable()
export class AlbumsService {
  constructor(
    @InjectRepository(Album)
    private readonly albums: Repository<Album>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
  ) {}

  async create(ownerId: string, dto: CreateAlbumDto): Promise<AlbumResponse> {
    const album = this.albums.create({
      ownerId,
      name: dto.name,
      flowers: [],
    });
    const saved = await this.albums.save(album);
    return toResponse(saved);
  }

  async list(ownerId: string): Promise<AlbumResponse[]> {
    const albums = await this.albums.find({
      where: { ownerId },
      relations: { flowers: true },
      order: { createdAt: 'DESC' },
    });
    return albums.map(toResponse);
  }

  async getById(ownerId: string, id: string): Promise<AlbumResponse> {
    return toResponse(await this.requireAlbum(ownerId, id));
  }

  async rename(
    ownerId: string,
    id: string,
    dto: UpdateAlbumDto,
  ): Promise<AlbumResponse> {
    const album = await this.requireAlbum(ownerId, id);
    album.name = dto.name;
    const saved = await this.albums.save(album);
    return toResponse(saved);
  }

  async remove(ownerId: string, id: string): Promise<void> {
    const album = await this.requireAlbum(ownerId, id);
    await this.albums.remove(album);
  }

  /** Ajoute une fleur (du même propriétaire) à l'album. Idempotent. */
  async addFlower(
    ownerId: string,
    albumId: string,
    flowerId: string,
  ): Promise<AlbumResponse> {
    const album = await this.requireAlbum(ownerId, albumId);
    const flower = await this.flowers.findOne({
      where: { id: flowerId, ownerId },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    if (!album.flowers.some((f) => f.id === flowerId)) {
      album.flowers.push(flower);
      await this.albums.save(album);
    }
    return toResponse(album);
  }

  /** Retire une fleur de l'album. Idempotent. */
  async removeFlower(
    ownerId: string,
    albumId: string,
    flowerId: string,
  ): Promise<AlbumResponse> {
    const album = await this.requireAlbum(ownerId, albumId);
    album.flowers = album.flowers.filter((f) => f.id !== flowerId);
    await this.albums.save(album);
    return toResponse(album);
  }

  private async requireAlbum(ownerId: string, id: string): Promise<Album> {
    const album = await this.albums.findOne({
      where: { id, ownerId },
      relations: { flowers: true },
    });
    if (!album) {
      throw new NotFoundException('Album introuvable.');
    }
    return album;
  }
}

function toResponse(album: Album): AlbumResponse {
  return {
    id: album.id,
    ownerId: album.ownerId,
    name: album.name,
    flowerIds: (album.flowers ?? []).map((f) => f.id),
    createdAt: album.createdAt,
  };
}

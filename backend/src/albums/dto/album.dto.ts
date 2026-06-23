import { IsNotEmpty, IsString, IsUUID, MaxLength } from 'class-validator';

export class CreateAlbumDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(120)
  name: string;
}

export class UpdateAlbumDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(120)
  name: string;
}

/** Rattachement d'une fleur à un album. */
export class AddFlowerToAlbumDto {
  @IsUUID()
  flowerId: string;
}

import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  UploadedFile,
  UploadedFiles,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileFieldsInterceptor, FileInterceptor } from '@nestjs/platform-express';
import { ApiBearerAuth, ApiConsumes, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { ReorderPhotosDto } from './dto/photo.dto';
import {
  imageUploadOptions,
  imageVariantsUploadOptions,
} from './image-upload.options';
import { FlowerPhotosService } from './flower-photos.service';

@ApiTags('flowers')
@ApiBearerAuth('access-token')
@Controller('flowers/:id/photos')
@UseGuards(JwtAuthGuard)
export class FlowerPhotosController {
  constructor(private readonly photos: FlowerPhotosService) {}

  @Post()
  add(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ) {
    return this.photos.add(user.userId, flowerId);
  }

  /** Reçoit les deux variantes WebP finales, sans réencodage. */
  @Post(':photoId/image-variants')
  @ApiConsumes('multipart/form-data')
  @UseInterceptors(
    FileFieldsInterceptor(
      [{ name: 'file', maxCount: 1 }, { name: 'thumbnail', maxCount: 1 }],
      imageVariantsUploadOptions,
    ),
  )
  uploadImage(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Param('photoId', ParseUUIDPipe) photoId: string,
    @UploadedFiles() files: Record<string, Array<{ buffer: Buffer }>> | undefined,
  ) {
    const full = files?.file?.[0]?.buffer;
    const thumbnail = files?.thumbnail?.[0]?.buffer;
    if (!full?.length || !thumbnail?.length) {
      throw new BadRequestException('Image principale ou miniature manquante.');
    }
    return this.photos.uploadImage(user.userId, flowerId, photoId, full, thumbnail);
  }

  /** Compatibilité temporaire avec les anciennes apps qui envoient un JPEG. */
  @Post(':photoId/image')
  @ApiConsumes('multipart/form-data')
  @UseInterceptors(FileInterceptor('file', imageUploadOptions))
  uploadLegacyImage(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Param('photoId', ParseUUIDPipe) photoId: string,
    @UploadedFile() file: { buffer: Buffer } | undefined,
  ) {
    if (!file?.buffer?.length) throw new BadRequestException('Fichier image manquant.');
    return this.photos.uploadLegacyImage(user.userId, flowerId, photoId, file.buffer);
  }

  @Patch('order')
  reorder(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Body() dto: ReorderPhotosDto,
  ) {
    return this.photos.reorder(user.userId, flowerId, dto.photoIds);
  }

  @Patch(':photoId/cover')
  setCover(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Param('photoId', ParseUUIDPipe) photoId: string,
  ) {
    return this.photos.setCover(user.userId, flowerId, photoId);
  }

  @Delete(':photoId')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Param('photoId', ParseUUIDPipe) photoId: string,
  ): Promise<void> {
    await this.photos.remove(user.userId, flowerId, photoId);
  }
}

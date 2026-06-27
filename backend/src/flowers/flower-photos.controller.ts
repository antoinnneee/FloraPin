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
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { ApiBearerAuth, ApiConsumes, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { ReorderPhotosDto } from './dto/photo.dto';
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

  /** Téléverse le binaire d'une photo (multipart `file`), réencodé en WebP. */
  @Post(':photoId/image')
  @ApiConsumes('multipart/form-data')
  @UseInterceptors(FileInterceptor('file'))
  uploadImage(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Param('photoId', ParseUUIDPipe) photoId: string,
    @UploadedFile() file: { buffer: Buffer } | undefined,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('Fichier image manquant.');
    }
    return this.photos.uploadImage(user.userId, flowerId, photoId, file.buffer);
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

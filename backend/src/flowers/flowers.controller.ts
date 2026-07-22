import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
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
import {
  CreateFlowerDto,
  SearchFlowersQueryDto,
  UpdateFlowerDto,
} from './dto/flower.dto';
import {
  imageUploadOptions,
  imageVariantsUploadOptions,
} from './image-upload.options';
import { FlowersService } from './flowers.service';

@ApiTags('flowers')
@ApiBearerAuth('access-token')
@Controller('flowers')
@UseGuards(JwtAuthGuard)
export class FlowersController {
  constructor(private readonly flowers: FlowersService) {}

  @Post()
  create(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreateFlowerDto) {
    return this.flowers.create(user.userId, dto);
  }

  @Get()
  listMine(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: SearchFlowersQueryDto,
  ) {
    return this.flowers.search(user.userId, {
      species: query.species,
      tag: query.tag,
    });
  }

  @Get(':id')
  getOne(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.flowers.getById(user.userId, id);
  }

  /**
   * Reçoit les deux WebP finaux de l'app et les valide sans réencodage.
   */
  @Post(':id/image-variants')
  @ApiConsumes('multipart/form-data')
  @UseInterceptors(
    FileFieldsInterceptor(
      [{ name: 'file', maxCount: 1 }, { name: 'thumbnail', maxCount: 1 }],
      imageVariantsUploadOptions,
    ),
  )
  uploadImage(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @UploadedFiles() files: Record<string, Array<{ buffer: Buffer }>> | undefined,
  ) {
    const full = files?.file?.[0]?.buffer;
    const thumbnail = files?.thumbnail?.[0]?.buffer;
    if (!full?.length || !thumbnail?.length) {
      throw new BadRequestException('Image principale ou miniature manquante.');
    }
    return this.flowers.uploadImage(user.userId, id, full, thumbnail);
  }

  /** Compatibilité temporaire avec les anciennes apps qui envoient un JPEG. */
  @Post(':id/image')
  @ApiConsumes('multipart/form-data')
  @UseInterceptors(FileInterceptor('file', imageUploadOptions))
  uploadLegacyImage(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @UploadedFile() file: { buffer: Buffer } | undefined,
  ) {
    if (!file?.buffer?.length) throw new BadRequestException('Fichier image manquant.');
    return this.flowers.uploadLegacyImage(user.userId, id, file.buffer);
  }

  @Get(':id/image-url')
  imageUrl(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.flowers.getImageUrl(user.userId, id);
  }

  @Patch(':id')
  update(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateFlowerDto,
  ) {
    return this.flowers.update(user.userId, id, dto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    await this.flowers.remove(user.userId, id);
  }
}

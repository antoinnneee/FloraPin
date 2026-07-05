import {
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
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { AlbumsService } from './albums.service';
import {
  AddFlowerToAlbumDto,
  CreateAlbumDto,
  SetAlbumGroupDto,
  SetAlbumPermissionsDto,
  UpdateAlbumDto,
} from './dto/album.dto';

@ApiTags('albums')
@ApiBearerAuth('access-token')
@Controller('albums')
@UseGuards(JwtAuthGuard)
export class AlbumsController {
  constructor(private readonly albums: AlbumsService) {}

  @Post()
  create(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreateAlbumDto) {
    return this.albums.create(user.userId, dto);
  }

  @Get()
  listMine(@CurrentUser() user: AuthenticatedUser) {
    return this.albums.list(user.userId);
  }

  @Get(':id')
  getOne(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.albums.getById(user.userId, id);
  }

  @Patch(':id')
  rename(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateAlbumDto,
  ) {
    return this.albums.rename(user.userId, id, dto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    await this.albums.remove(user.userId, id);
  }

  /** Rattache/détache l'album à un groupe collaboratif (TÂCHE 7.1). */
  @Patch(':id/group')
  setGroup(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SetAlbumGroupDto,
  ) {
    return this.albums.setGroup(
      user.userId,
      id,
      dto.groupId ?? null,
      dto.permissionMode,
    );
  }

  /** Règle le régime de droits d'un album de groupe (TÂCHE 7.1). */
  @Patch(':id/permissions')
  setPermissions(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: SetAlbumPermissionsDto,
  ) {
    return this.albums.setPermissions(user.userId, id, dto);
  }

  @Post(':id/flowers')
  addFlower(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: AddFlowerToAlbumDto,
  ) {
    return this.albums.addFlower(user.userId, id, dto.flowerId);
  }

  @Delete(':id/flowers/:flowerId')
  removeFlower(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Param('flowerId', ParseUUIDPipe) flowerId: string,
  ) {
    return this.albums.removeFlower(user.userId, id, flowerId);
  }
}

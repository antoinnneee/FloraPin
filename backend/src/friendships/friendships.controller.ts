import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { AddFriendByIdDto, CreateFriendshipDto } from './dto/friendship.dto';
import { FriendshipsService } from './friendships.service';

@ApiTags('friendships')
@ApiBearerAuth('access-token')
@Controller('friendships')
@UseGuards(JwtAuthGuard)
export class FriendshipsController {
  constructor(private readonly friendships: FriendshipsService) {}

  @Get()
  list(@CurrentUser() user: AuthenticatedUser) {
    return this.friendships.list(user.userId);
  }

  @Post()
  request(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: CreateFriendshipDto,
  ) {
    return this.friendships.requestByEmail(user.userId, dto.email);
  }

  /**
   * Ajout d'ami par QR code (TÂCHE 4.5) : le corps porte l'id (UUID) scanné.
   * Contrairement à l'ajout par email, l'acceptation croisée est automatique
   * quand chacun scanne l'autre (consentement mutuel).
   */
  @Post('by-id')
  requestById(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: AddFriendByIdDto,
  ) {
    return this.friendships.requestById(user.userId, dto.userId);
  }

  @Post(':id/accept')
  @HttpCode(HttpStatus.OK)
  accept(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.friendships.accept(user.userId, id);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    await this.friendships.remove(user.userId, id);
  }
}

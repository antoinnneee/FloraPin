import { Controller, Get, Param, ParseUUIDPipe, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { FriendProfileService } from './friend-profile.service';

/**
 * Profil public limité d'un ami (TÂCHE 5.7). Route distincte de `UsersController`
 * (qui gère le compte courant `users/me`) : ici on lit le profil D'UN AUTRE
 * utilisateur, borné à ce qui est déjà accessible au spectateur.
 */
@ApiTags('users')
@ApiBearerAuth('access-token')
@Controller('users')
@UseGuards(JwtAuthGuard)
export class FriendProfileController {
  constructor(private readonly profiles: FriendProfileService) {}

  @Get(':id/profile')
  getProfile(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.profiles.getProfile(user.userId, id);
  }
}

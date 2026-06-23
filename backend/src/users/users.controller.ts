import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  NotFoundException,
  Patch,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { ChangeEmailDto } from '../auth/dto/auth.dto';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { DeleteAccountDto } from './dto/delete-account.dto';
import { UsersService } from './users.service';

@ApiTags('users')
@ApiBearerAuth('access-token')
@Controller('users')
export class UsersController {
  constructor(private readonly users: UsersService) {}

  @Get('me')
  @UseGuards(JwtAuthGuard)
  async me(@CurrentUser() current: AuthenticatedUser) {
    const user = await this.users.findById(current.userId);
    if (!user) {
      throw new NotFoundException('Utilisateur introuvable.');
    }
    return {
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      emailVerified: user.emailVerified,
      createdAt: user.createdAt,
    };
  }

  /**
   * Change l'adresse email du compte courant (NODE-117). Autorisé uniquement
   * tant que l'adresse n'est pas vérifiée.
   */
  @Patch('me/email')
  @UseGuards(JwtAuthGuard)
  async changeEmail(
    @CurrentUser() current: AuthenticatedUser,
    @Body() dto: ChangeEmailDto,
  ) {
    const user = await this.users.changeEmail(current.userId, dto.email);
    return {
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      emailVerified: user.emailVerified,
      createdAt: user.createdAt,
    };
  }

  /**
   * Supprime définitivement le compte courant et toutes ses données (NODE-118,
   * droit à l'effacement RGPD). Re-authentification par mot de passe exigée.
   */
  @Delete('me')
  @HttpCode(204)
  @UseGuards(JwtAuthGuard)
  async deleteMe(
    @CurrentUser() current: AuthenticatedUser,
    @Body() dto: DeleteAccountDto,
  ): Promise<void> {
    await this.users.deleteAccount(current.userId, dto.password);
  }
}

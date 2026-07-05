import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  NotFoundException,
  Patch,
  Post,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { ApiBearerAuth, ApiConsumes, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { ChangeEmailDto } from '../auth/dto/auth.dto';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { imageUploadOptions } from '../flowers/image-upload.options';
import { User } from './user.entity';
import { DeleteAccountDto } from './dto/delete-account.dto';
import { UpdateProfileDto } from './dto/update-profile.dto';
import { UsersService } from './users.service';

@ApiTags('users')
@ApiBearerAuth('access-token')
@Controller('users')
export class UsersController {
  constructor(private readonly users: UsersService) {}

  /**
   * Sérialise le profil renvoyé au client. Résout l'URL présignée de l'avatar
   * (TÂCHE 5.1) à la volée : les URLs expirent, elles ne sont jamais figées.
   */
  private async toProfileResponse(user: User) {
    return {
      id: user.id,
      email: user.email,
      displayName: user.displayName,
      emailVerified: user.emailVerified,
      avatarUrl: await this.users.avatarUrl(user),
      createdAt: user.createdAt,
    };
  }

  @Get('me')
  @UseGuards(JwtAuthGuard)
  async me(@CurrentUser() current: AuthenticatedUser) {
    const user = await this.users.findById(current.userId);
    if (!user) {
      throw new NotFoundException('Utilisateur introuvable.');
    }
    return this.toProfileResponse(user);
  }

  /**
   * Modifie le nom d'affichage du compte courant (TÂCHE 1.7). Mêmes règles
   * qu'à l'inscription (trim + 1..80 caractères, cf. UpdateProfileDto).
   */
  @Patch('me')
  @UseGuards(JwtAuthGuard)
  async updateProfile(
    @CurrentUser() current: AuthenticatedUser,
    @Body() dto: UpdateProfileDto,
  ) {
    const user = await this.users.updateDisplayName(
      current.userId,
      dto.displayName,
    );
    return this.toProfileResponse(user);
  }

  /**
   * Téléverse (ou remplace) l'avatar du compte courant (TÂCHE 5.1, multipart,
   * champ `file`). Le serveur réencode en WebP avant stockage.
   */
  @Post('me/avatar')
  @UseGuards(JwtAuthGuard)
  @ApiConsumes('multipart/form-data')
  @UseInterceptors(FileInterceptor('file', imageUploadOptions))
  async uploadAvatar(
    @CurrentUser() current: AuthenticatedUser,
    @UploadedFile() file: { buffer: Buffer } | undefined,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('Fichier image manquant.');
    }
    const user = await this.users.uploadAvatar(current.userId, file.buffer);
    return this.toProfileResponse(user);
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
    return this.toProfileResponse(user);
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

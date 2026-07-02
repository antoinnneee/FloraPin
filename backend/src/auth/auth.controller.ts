import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from './current-user.decorator';
import { AuthenticatedUser } from './jwt.strategy';
import { JwtAuthGuard } from './jwt-auth.guard';
import { AuthService } from './auth.service';
import {
  ForgotPasswordDto,
  LoginDto,
  RefreshDto,
  RegisterDto,
  ResetPasswordDto,
  VerifyEmailDto,
} from './dto/auth.dto';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}

  /** Création de compte : 3 tentatives/min par IP (anti-spam). */
  @Post('register')
  @Throttle({ default: { limit: 3, ttl: 60_000 } })
  register(@Body() dto: RegisterDto) {
    return this.auth.register(dto.email, dto.password, dto.displayName);
  }

  /** Connexion : 5 tentatives/min par IP (anti brute-force). */
  @Post('login')
  @HttpCode(HttpStatus.OK)
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  login(@Body() dto: LoginDto) {
    return this.auth.login(dto.email, dto.password);
  }

  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  refresh(@Body() dto: RefreshDto) {
    return this.auth.refresh(dto.refreshToken);
  }

  @Post('logout')
  @HttpCode(HttpStatus.NO_CONTENT)
  async logout(@Body() dto: RefreshDto): Promise<void> {
    await this.auth.logout(dto.refreshToken);
  }

  /**
   * Démarre un « mot de passe oublié » (NODE-116). Réponse 200 systématique
   * (anti-énumération) : on ne révèle pas si l'email correspond à un compte.
   */
  @Post('forgot-password')
  @HttpCode(HttpStatus.OK)
  // 3 demandes / 15 min par IP (anti-spam d'emails de réinitialisation).
  @Throttle({ default: { limit: 3, ttl: 900_000 } })
  async forgotPassword(
    @Body() dto: ForgotPasswordDto,
  ): Promise<{ message: string }> {
    await this.auth.forgotPassword(dto.email);
    return {
      message:
        "Si un compte existe pour cet email, un lien de réinitialisation a été envoyé.",
    };
  }

  /** Termine la réinitialisation avec le token reçu par email (NODE-116). */
  @Post('reset-password')
  @HttpCode(HttpStatus.OK)
  async resetPassword(
    @Body() dto: ResetPasswordDto,
  ): Promise<{ message: string }> {
    await this.auth.resetPassword(dto.token, dto.newPassword);
    return { message: 'Mot de passe réinitialisé.' };
  }

  /**
   * Demande/renvoie un email de vérification d'adresse (NODE-117, opt-in).
   * Sans effet si l'adresse est déjà vérifiée.
   */
  @Post('email/verification')
  @HttpCode(HttpStatus.OK)
  @ApiBearerAuth('access-token')
  @UseGuards(JwtAuthGuard)
  // 3 demandes / 15 min par IP (anti-spam d'emails de vérification).
  @Throttle({ default: { limit: 3, ttl: 900_000 } })
  async requestEmailVerification(
    @CurrentUser() current: AuthenticatedUser,
  ): Promise<{ message: string }> {
    await this.auth.requestEmailVerification(current.userId);
    return {
      message: "Si nécessaire, un email de vérification a été envoyé.",
    };
  }

  /** Valide l'adresse via le token reçu par email (NODE-117). */
  @Post('email/verify')
  @HttpCode(HttpStatus.OK)
  async verifyEmail(@Body() dto: VerifyEmailDto): Promise<{ message: string }> {
    await this.auth.verifyEmailToken(dto.token);
    return { message: 'Adresse email vérifiée.' };
  }
}

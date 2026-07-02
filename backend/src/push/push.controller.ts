import {
  Body,
  Controller,
  Delete,
  Param,
  Post,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { DeviceTokensService } from './device-tokens.service';
import { RegisterDeviceDto } from './dto/register-device.dto';

@ApiTags('push')
@ApiBearerAuth('access-token')
@Controller('push')
@UseGuards(JwtAuthGuard)
export class PushController {
  constructor(private readonly devices: DeviceTokensService) {}

  /** Enregistre le jeton de l'appareil courant pour recevoir les push. */
  @Post('devices')
  register(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: RegisterDeviceDto,
  ) {
    return this.devices.register(user.userId, dto.token, dto.platform);
  }

  /**
   * Désenregistre un jeton (déconnexion). Ne supprime que les jetons
   * appartenant à l'utilisateur authentifié (I2).
   */
  @Delete('devices/:token')
  async unregister(
    @CurrentUser() user: AuthenticatedUser,
    @Param('token') token: string,
  ) {
    await this.devices.unregister(user.userId, token);
    return { ok: true };
  }
}

import {
  Controller,
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
import { IdentificationService } from './identification.service';

@ApiTags('identification')
@ApiBearerAuth('access-token')
@Controller('flowers')
@UseGuards(JwtAuthGuard)
export class IdentificationController {
  constructor(private readonly identification: IdentificationService) {}

  /** Lance l'identification de l'espèce et renvoie les suggestions. */
  @Post(':id/identify')
  @HttpCode(HttpStatus.OK)
  identify(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.identification.identify(user.userId, id);
  }
}

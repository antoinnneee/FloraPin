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
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { CreateShareDto } from './dto/share.dto';
import { SharesService } from './shares.service';

@Controller()
@UseGuards(JwtAuthGuard)
export class SharesController {
  constructor(private readonly shares: SharesService) {}

  /** Crée un partage (toutes mes fleurs ou une fleur précise), avec/sans GPS. */
  @Post('shares')
  create(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreateShareDto) {
    return this.shares.create(user.userId, dto);
  }

  /** Mes partages sortants. */
  @Get('shares')
  listMine(@CurrentUser() user: AuthenticatedUser) {
    return this.shares.listMine(user.userId);
  }

  @Delete('shares/:id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async revoke(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    await this.shares.revoke(user.userId, id);
  }

  /** Fleurs partagées avec moi (GPS éventuellement masqué). */
  @Get('shared')
  sharedWithMe(@CurrentUser() user: AuthenticatedUser) {
    return this.shares.sharedWithMe(user.userId);
  }
}

import {
  Controller,
  Delete,
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
import { LikesService } from './likes.service';

/** Cœurs sur les fleurs (NODE-139). */
@ApiTags('likes')
@ApiBearerAuth('access-token')
@UseGuards(JwtAuthGuard)
@Controller('flowers/:id/like')
export class LikesController {
  constructor(private readonly likes: LikesService) {}

  /** Pose un cœur (idempotent). */
  @Post()
  @HttpCode(HttpStatus.NO_CONTENT)
  async like(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ): Promise<void> {
    await this.likes.like(user.userId, flowerId);
  }

  /** Retire le cœur (idempotent). */
  @Delete()
  @HttpCode(HttpStatus.NO_CONTENT)
  async unlike(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ): Promise<void> {
    await this.likes.unlike(user.userId, flowerId);
  }
}

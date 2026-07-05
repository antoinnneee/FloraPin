import {
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
import { LikerResponse, LikesService } from './likes.service';

/** Cœurs sur les fleurs (NODE-139). */
@ApiTags('likes')
@ApiBearerAuth('access-token')
@UseGuards(JwtAuthGuard)
@Controller('flowers/:id')
export class LikesController {
  constructor(private readonly likes: LikesService) {}

  /** Pose un cœur (idempotent). */
  @Post('like')
  @HttpCode(HttpStatus.NO_CONTENT)
  async like(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ): Promise<void> {
    await this.likes.like(user.userId, flowerId);
  }

  /** Retire le cœur (idempotent). */
  @Delete('like')
  @HttpCode(HttpStatus.NO_CONTENT)
  async unlike(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ): Promise<void> {
    await this.likes.unlike(user.userId, flowerId);
  }

  /** Liste les utilisateurs ayant posé un cœur (fleur visible par le viewer). */
  @Get('likes')
  listLikers(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ): Promise<LikerResponse[]> {
    return this.likes.listLikers(user.userId, flowerId);
  }
}

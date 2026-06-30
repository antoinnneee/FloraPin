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
import { CommentsService } from './comments.service';
import { CreateCommentDto } from './dto/comment.dto';

/** Fil de discussion sur une fleur. */
@ApiTags('comments')
@ApiBearerAuth('access-token')
@Controller('flowers/:id/comments')
@UseGuards(JwtAuthGuard)
export class CommentsController {
  constructor(private readonly comments: CommentsService) {}

  /** Poste un commentaire sur une fleur visible. */
  @Post()
  post(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Body() dto: CreateCommentDto,
  ) {
    return this.comments.post(user.userId, flowerId, dto.body);
  }

  /** Liste les commentaires d'une fleur visible (chronologique). */
  @Get()
  list(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ) {
    return this.comments.listForFlower(user.userId, flowerId);
  }

  /** Supprime un commentaire (auteur ou propriétaire de la fleur). */
  @Delete(':commentId')
  @HttpCode(HttpStatus.NO_CONTENT)
  async delete(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Param('commentId', ParseUUIDPipe) commentId: string,
  ): Promise<void> {
    await this.comments.delete(user.userId, flowerId, commentId);
  }
}

import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import {
  CreateFlowerDto,
  SearchFlowersQueryDto,
  UpdateFlowerDto,
} from './dto/flower.dto';
import { FlowersService } from './flowers.service';

@ApiTags('flowers')
@ApiBearerAuth('access-token')
@Controller('flowers')
@UseGuards(JwtAuthGuard)
export class FlowersController {
  constructor(private readonly flowers: FlowersService) {}

  @Post()
  create(@CurrentUser() user: AuthenticatedUser, @Body() dto: CreateFlowerDto) {
    return this.flowers.create(user.userId, dto);
  }

  @Get()
  listMine(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: SearchFlowersQueryDto,
  ) {
    return this.flowers.search(user.userId, {
      species: query.species,
      tag: query.tag,
    });
  }

  @Get(':id')
  getOne(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.flowers.getById(user.userId, id);
  }

  @Get(':id/image-url')
  imageUrl(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ) {
    return this.flowers.getImageUrl(user.userId, id);
  }

  @Patch(':id')
  update(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateFlowerDto,
  ) {
    return this.flowers.update(user.userId, id, dto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  async remove(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<void> {
    await this.flowers.remove(user.userId, id);
  }
}

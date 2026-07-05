import {
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import {
  HerbierResponse,
  ListSpeciesQueryDto,
  SearchSpeciesQueryDto,
} from './dto/species.dto';
import { SpeciesService } from './species.service';

/** Encyclopédie des espèces (NODE-125). */
@ApiTags('species')
@ApiBearerAuth('access-token')
@Controller('species')
@UseGuards(JwtAuthGuard)
export class SpeciesController {
  constructor(private readonly species: SpeciesService) {}

  /** Liste paginée du référentiel d'espèces. */
  @Get()
  list(@Query() query: ListSpeciesQueryDto) {
    return this.species.list(query.page, query.limit);
  }

  /** Autocomplétion par nom scientifique ou commun. */
  // Déclaré avant `:id` pour que /species/search ne soit pas pris pour un id.
  @Get('search')
  search(@Query() query: SearchSpeciesQueryDto) {
    return this.species.search(query.q, query.limit);
  }

  /**
   * Herbier de l'utilisateur courant (TÂCHE 5.6) : espèces distinctes regroupées
   * par famille botanique. Déclaré avant `:id` pour que /species/herbier ne soit
   * pas pris pour un id.
   */
  @Get('herbier')
  herbier(@CurrentUser() user: AuthenticatedUser): Promise<HerbierResponse> {
    return this.species.herbierFor(user.userId);
  }

  /** Fiche détaillée d'une espèce. */
  @Get(':id')
  getOne(@Param('id', ParseUUIDPipe) id: string) {
    return this.species.getById(id);
  }
}

import {
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { ListSpeciesQueryDto, SearchSpeciesQueryDto } from './dto/species.dto';
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

  /** Fiche détaillée d'une espèce. */
  @Get(':id')
  getOne(@Param('id', ParseUUIDPipe) id: string) {
    return this.species.getById(id);
  }
}

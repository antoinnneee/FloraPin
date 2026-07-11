import {
  Controller,
  DefaultValuePipe,
  Get,
  Header,
  ParseIntPipe,
  Query,
  UseGuards,
} from '@nestjs/common';
import { ApiExcludeController } from '@nestjs/swagger';
import { AdminGuard } from './admin.guard';
import { AdminService } from './admin.service';
import { adminDashboardHtml } from './dashboard.html';

@ApiExcludeController()
@Controller('admin')
export class AdminController {
  constructor(private readonly admin: AdminService) {}

  /** Coquille sans données sensibles ; les appels JSON sont protégés par AdminGuard. */
  @Get('dashboard')
  @Header('Content-Type', 'text/html; charset=utf-8')
  dashboard(): string {
    return adminDashboardHtml();
  }

  @Get('overview')
  @UseGuards(AdminGuard)
  @Header('Cache-Control', 'no-store')
  overview() {
    return this.admin.overview();
  }

  @Get('images')
  @UseGuards(AdminGuard)
  @Header('Cache-Control', 'no-store')
  images(
    @Query('page', new DefaultValuePipe(1), ParseIntPipe) page: number,
    @Query('pageSize', new DefaultValuePipe(24), ParseIntPipe) pageSize: number,
  ) {
    return this.admin.images(page, pageSize);
  }
}

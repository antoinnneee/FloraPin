import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { FriendshipsModule } from '../friendships/friendships.module';
import { SharesModule } from '../shares/shares.module';
import { UsersModule } from '../users/users.module';
import { FriendProfileController } from './friend-profile.controller';
import { FriendProfileService } from './friend-profile.service';

/**
 * Profil d'ami (TÂCHE 5.7) : agrège UsersService (identité), FriendshipsService
 * (amitié + amis en commun) et SharesService (fleurs visibles). Module dédié
 * placé en aval de ces trois-là (aucun ne l'importe) : pas de dépendance
 * circulaire.
 */
@Module({
  imports: [
    TypeOrmModule.forFeature([Flower]),
    UsersModule,
    FriendshipsModule,
    SharesModule,
  ],
  controllers: [FriendProfileController],
  providers: [FriendProfileService],
})
export class FriendProfileModule {}

import { IsIn, IsNotEmpty, IsString } from 'class-validator';
import { DevicePlatform } from '../device-token.entity';

const PLATFORMS: DevicePlatform[] = ['android', 'ios', 'web'];

export class RegisterDeviceDto {
  /** Jeton fourni par FCM/APNs côté client. */
  @IsString()
  @IsNotEmpty()
  token: string;

  @IsIn(PLATFORMS)
  platform: DevicePlatform;
}

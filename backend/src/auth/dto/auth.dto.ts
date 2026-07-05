import { IsEmail, IsString, MaxLength, MinLength } from 'class-validator';

export class RegisterDto {
  @IsEmail()
  email: string;

  @IsString()
  @MinLength(8)
  @MaxLength(72) // limite bcrypt
  password: string;

  @IsString()
  @MinLength(1)
  @MaxLength(80)
  displayName: string;
}

export class LoginDto {
  @IsEmail()
  email: string;

  @IsString()
  @MinLength(1)
  password: string;
}

export class RefreshDto {
  @IsString()
  refreshToken: string;
}

export class ForgotPasswordDto {
  @IsEmail()
  email: string;
}

export class ResetPasswordDto {
  @IsString()
  token: string;

  @IsString()
  @MinLength(8)
  @MaxLength(72) // limite bcrypt
  newPassword: string;
}

export class VerifyEmailDto {
  @IsString()
  token: string;
}

export class ChangeEmailDto {
  @IsEmail()
  email: string;
}

export class ChangePasswordDto {
  @IsString()
  @MinLength(1)
  oldPassword: string;

  @IsString()
  @MinLength(8)
  @MaxLength(72) // limite bcrypt
  newPassword: string;
}

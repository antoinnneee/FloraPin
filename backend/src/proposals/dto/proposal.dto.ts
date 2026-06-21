import { IsString, MaxLength, MinLength } from 'class-validator';

export class ProposeSpeciesDto {
  @IsString()
  @MinLength(2)
  @MaxLength(200)
  species: string;
}

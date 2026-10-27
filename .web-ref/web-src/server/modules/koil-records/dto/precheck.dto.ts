import {
  IsString,
  IsNumber,
  IsPositive,
  IsOptional,
  IsBoolean,
  IsIn,
} from 'class-validator';

export class PrecheckDto {
  @IsString()
  reoilDate!: string;

  @IsOptional()
  @IsNumber()
  mileage?: number;

  @IsOptional()
  @IsNumber()
  @IsPositive()
  oilAmount?: number;

  @IsOptional()
  @IsNumber()
  @IsPositive()
  actualAmount?: number;

  @IsOptional()
  @IsString()
  excludeId?: string;

  @IsOptional()
  @IsBoolean()
  includeBoundary?: boolean;

  @IsOptional()
  @IsBoolean()
  includePrevRecord?: boolean;

  @IsOptional()
  @IsString()
  @IsIn(['entry_page', 'backfill', 'import_csv', 'api'])
  source?: string;
}

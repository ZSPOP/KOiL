import {
  IsString,
  IsNumber,
  IsPositive,
  IsIn,
  IsOptional,
  IsObject,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';
import type {
  CreateKoilRecordRequest,
  UpdateKoilRecordRequest,
  ImportCsvRequest,
} from '@shared/api.interface';

export class CreateKoilRecordDto implements CreateKoilRecordRequest {
  @IsString()
  reoilDate!: string;

  @IsNumber()
  @IsPositive()
  mileage!: number;

  @IsNumber()
  @IsPositive()
  oilAmount!: number;

  @IsNumber()
  @IsPositive()
  payableAmount!: number;

  @IsNumber()
  @IsPositive()
  actualAmount!: number;

  @IsOptional()
  @IsNumber()
  oilPrice?: number | null;

  @IsOptional()
  @IsString()
  @IsIn(['92', '95', '98', '0'])
  oilGrade?: '92' | '95' | '98' | '0';

  @IsOptional()
  @IsString()
  province?: string;

  @IsOptional()
  @IsString()
  @IsIn(['entry_page', 'backfill', 'import_csv', 'api'])
  source?: 'entry_page' | 'backfill_modal';
}

export class UpdateKoilRecordDto implements UpdateKoilRecordRequest {
  @IsOptional()
  @IsString()
  reoilDate?: string;

  @IsOptional()
  @IsNumber()
  @IsPositive()
  mileage?: number;

  @IsOptional()
  @IsNumber()
  @IsPositive()
  oilAmount?: number;

  @IsOptional()
  @IsNumber()
  @IsPositive()
  payableAmount?: number;

  @IsOptional()
  @IsNumber()
  @IsPositive()
  actualAmount?: number;

  @IsOptional()
  @IsNumber()
  oilPrice?: number | null;

  @IsOptional()
  @IsString()
  @IsIn(['92', '95', '98', '0'])
  oilGrade?: '92' | '95' | '98' | '0';

  @IsOptional()
  @IsString()
  province?: string;
}

export class ImportCsvDto implements ImportCsvRequest {
  @IsString()
  csvContent!: string;
}

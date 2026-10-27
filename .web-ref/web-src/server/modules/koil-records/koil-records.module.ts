import { Module, forwardRef } from '@nestjs/common';
import { KoilRecordsController } from './koil-records.controller';
import { KoilRecordsService } from './koil-records.service';
import { OilPriceService } from './oil-price.service';
import { OilPriceDbService } from './oil-price-db.service';
import { KoilRecordsAutomationService } from './koil-records.automation';

@Module({
  controllers: [KoilRecordsController],
  providers: [KoilRecordsService, OilPriceService, OilPriceDbService, KoilRecordsAutomationService],
})
export class KoilRecordsModule {}

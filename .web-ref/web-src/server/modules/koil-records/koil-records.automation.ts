import { Logger } from '@nestjs/common';
import { Automation, BindTrigger } from '@lark-apaas/fullstack-nestjs-core';
import { OilPriceDbService } from './oil-price-db.service';

@Automation()
export class KoilRecordsAutomationService {
  private readonly logger = new Logger(KoilRecordsAutomationService.name);

  constructor(private readonly oilPriceDbService: OilPriceDbService) {}

  @BindTrigger('koil_price_auto_sync')
  async autoSyncKoilPrice(): Promise<void> {
    this.logger.log('定时任务：开始检测油价更新');
    try {
      const result = await this.oilPriceDbService.checkAndUpdate(false);
      if (result.success && result.newAdjustments > 0) {
        this.logger.log(
          `定时任务：油价已更新，新增 ${result.newRecords} 条记录，最新调价日 ${result.latestDate}`,
        );
      } else {
        this.logger.log(`定时任务：油价检测完成 - ${result.message}`);
      }
    } catch (error) {
      this.logger.error(
        `定时任务：油价检测失败 - ${error instanceof Error ? error.message : String(error)}`,
      );
      throw error;
    }
  }
}

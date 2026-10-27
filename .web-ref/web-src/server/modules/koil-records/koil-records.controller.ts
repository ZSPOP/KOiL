import { Controller, Get, Post, Put, Patch, Delete, Body, Param, Query, Res, Req } from '@nestjs/common';
import type { Response, Request } from 'express';
import { KoilRecordsService } from './koil-records.service';
import { OilPriceService } from './oil-price.service';
import { OilPriceDbService } from './oil-price-db.service';
import {
  CreateKoilRecordDto,
  UpdateKoilRecordDto,
  ImportCsvDto,
} from './dto/create-koil-record.dto';
import { PrecheckDto } from './dto/precheck.dto';
import type {
  KoilRecordListResponse,
  KoilTrendData,
  LastKoilRecord,
  OilPriceResponse,
  ProvinceInfo,
  IpLocationResponse,
  ReverseGeocodeResponse,
  DuplicateCheckResponse,
  ImportPreviewResponse,
  ImportConfirmResponse,
  HistoryOilPriceResponse,
  MileageConflictCheckResponse,
  MileageBoundaryResponse,
  KoilRecordPrecheckResponse,
  OilPriceDbStatusResponse,
  OilPriceDbUpdateResponse,
  KoilPricePendingListResponse,
  PriceDeviationCheckResponse,
  PriceDeviationThresholdResponse,
  PriceDeviationCheckRequest,
} from '@shared/api.interface';

@Controller('api/koil-records')
export class KoilRecordsController {
  constructor(
    private readonly koilRecordService: KoilRecordsService,
    private readonly oilPriceService: OilPriceService,
    private readonly oilPriceDbService: OilPriceDbService,
  ) {}

  @Get()
  async findAll(
    @Query('cursorDate') cursorDate?: string,
    @Query('cursorId') cursorId?: string,
    @Query('cursorMileage') cursorMileage?: string,
    @Query('pageSize') pageSize?: string,
  ): Promise<KoilRecordListResponse> {
    const size = pageSize ? parseInt(pageSize, 10) : 0;
    if (size > 0) {
      return this.koilRecordService.findPage(
        cursorDate || null,
        cursorId || null,
        cursorMileage || null,
        size,
      );
    }
    return this.koilRecordService.findAll();
  }

  @Get('mileage-conflict-check')
  async checkMileageConflict(
    @Query('reoilDate') reoilDate: string,
    @Query('mileage') mileage: string,
  ): Promise<MileageConflictCheckResponse> {
    const mileageNum = Number(mileage);
    if (!reoilDate || Number.isNaN(mileageNum) || mileageNum <= 0) {
      return {
        hasConflict: false,
        message: null,
        prevDate: null,
        prevMileage: null,
        nextDate: null,
        nextMileage: null,
        sameDayMileages: [],
      };
    }
    return this.koilRecordService.checkMileageChain(reoilDate, mileageNum);
  }

  @Get('mileage-boundary')
  async getMileageBoundary(
    @Query('reoilDate') reoilDate: string,
  ): Promise<MileageBoundaryResponse> {
    if (!reoilDate) {
      return {
        prevDate: null,
        prevMileage: null,
        nextDate: null,
        nextMileage: null,
        sameDayMileages: [],
        isBeforeFirst: false,
        firstDate: null,
        firstMileage: null,
      };
    }
    return this.koilRecordService.getMileageBoundary(reoilDate);
  }

  @Get('duplicate-check')
  async checkDuplicate(
    @Query('reoilDate') reoilDate: string,
    @Query('mileage') mileage: string,
    @Query('oilAmount') oilAmount?: string,
    @Query('actualAmount') actualAmount?: string,
  ): Promise<DuplicateCheckResponse> {
    const mileageNum = Number(mileage);
    if (!reoilDate || Number.isNaN(mileageNum)) {
      return { exists: false, sameDayCount: 0, similarExists: false };
    }
    const oilAmountNum = oilAmount !== undefined ? Number(oilAmount) : undefined;
    const actualAmountNum = actualAmount !== undefined ? Number(actualAmount) : undefined;
    return this.koilRecordService.checkDuplicate(
      reoilDate,
      mileageNum,
      oilAmountNum,
      actualAmountNum,
    );
  }

  @Post('precheck')
  async precheck(@Body() dto: PrecheckDto): Promise<KoilRecordPrecheckResponse> {
    return this.koilRecordService.precheck({
      reoilDate: dto.reoilDate,
      mileage: dto.mileage,
      oilAmount: dto.oilAmount,
      actualAmount: dto.actualAmount,
      excludeId: dto.excludeId,
      includeBoundary: dto.includeBoundary ?? false,
      includePrevRecord: dto.includePrevRecord ?? false,
    });
  }

  @Post()
  async create(@Body() dto: CreateKoilRecordDto): Promise<{ id: string }> {
    return this.koilRecordService.create(dto);
  }

  @Patch(':id')
  async update(
    @Param('id') id: string,
    @Body() dto: UpdateKoilRecordDto,
  ): Promise<void> {
    await this.koilRecordService.update(id, dto);
  }

  @Delete(':id')
  async remove(@Param('id') id: string): Promise<void> {
    await this.koilRecordService.remove(id);
  }

  @Get('trend')
  async getTrend(): Promise<KoilTrendData> {
    return this.koilRecordService.getTrendData();
  }

  @Get('prev-record')
  async getPrevRecord(
    @Query('reoilDate') reoilDate: string,
    @Query('mileage') mileage?: string,
    @Query('excludeId') excludeId?: string,
  ): Promise<LastKoilRecord | null> {
    if (!reoilDate) return null;
    const mileageNum = mileage !== undefined ? Number(mileage) : undefined;
    return this.koilRecordService.getPrevRecord(
      reoilDate,
      mileageNum,
      excludeId || undefined,
    );
  }

  @Get('latest')
  async getLatest(): Promise<LastKoilRecord | null> {
    return this.koilRecordService.getLatestRecord();
  }

  @Get('export')
  async exportCsv(@Res() res: Response): Promise<void> {
    const result = await this.koilRecordService.exportAllCsv();
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('X-Export-Count', String(result.count));
    res.setHeader('X-Export-Filename', encodeURIComponent(result.filename));
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${encodeURIComponent(result.filename)}"; filename*=UTF-8''${encodeURIComponent(result.filename)}`,
    );
    res.send('\uFEFF' + result.csv);
  }

  @Post('export-backup')
  async exportBackup(
    @Body() body: { settings: Record<string, string | number | boolean> },
    @Res() res: Response,
  ): Promise<void> {
    const settings = body?.settings ?? {};
    const result = await this.koilRecordService.exportBackup(settings);
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.setHeader('X-Export-Count', String(result.count));
    res.setHeader('X-Export-Filename', encodeURIComponent(result.filename));
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${encodeURIComponent(result.filename)}"; filename*=UTF-8''${encodeURIComponent(result.filename)}`,
    );
    res.send(result.json);
  }

  @Post('import/preview')
  async previewImport(@Body() dto: ImportCsvDto): Promise<ImportPreviewResponse> {
    return this.koilRecordService.previewImport(dto.csvContent);
  }

  @Post('import/confirm')
  async confirmImport(@Body() dto: ImportCsvDto): Promise<ImportConfirmResponse> {
    return this.koilRecordService.confirmImport(dto.csvContent);
  }

  @Get('oil-price/provinces')
  async getProvinces(): Promise<ProvinceInfo[]> {
    return this.oilPriceService.getProvinces().map((p) => ({
      code: p.code,
      name: p.name,
    }));
  }

  @Get('oil-price/history')
  async getHistoryOilPrice(
    @Query('province') province: string,
    @Query('grade') grade: string,
    @Query('date') date: string,
  ): Promise<HistoryOilPriceResponse | null> {
    if (!province || !grade || !date) return null;
    void this.oilPriceDbService.checkAndUpdate(false).catch(() => {
      // 异步触发检测更新，不阻塞查价响应
    });
    return this.oilPriceService.getHistoryPrice(province, grade, date);
  }

  @Get('oil-price')
  async getOilPrice(
    @Query('province') province: string,
  ): Promise<OilPriceResponse> {
    return this.oilPriceService.getOilPriceByProvince(province);
  }

  @Get('oil-price/reverse-geocode')
  async reverseGeocode(
    @Query('lat') lat: string,
    @Query('lon') lon: string,
  ): Promise<ReverseGeocodeResponse> {
    const latNum = Number(lat);
    const lonNum = Number(lon);
    if (Number.isNaN(latNum) || Number.isNaN(lonNum)) {
      return { success: false, error: '坐标参数无效' };
    }
    return this.oilPriceService.reverseGeocode(latNum, lonNum);
  }

  @Get('oil-price/ip-location')
  async getIpLocation(@Req() req: Request): Promise<IpLocationResponse> {
    const xff = req.headers['x-forwarded-for'];
    const xri = req.headers['x-real-ip'];
    const ip = (Array.isArray(xff) ? xff[0] : xff) || (Array.isArray(xri) ? xri[0] : xri) || req.ip || '';
    const province = await this.oilPriceService.getProvinceByIp(ip);
    if (province) {
      return {
        province,
        source: 'IP 归属地',
      };
    }
    return {
      province: '',
      source: '',
    };
  }

  @Get('oil-price-db/status')
  async getOilPriceDbStatus(): Promise<OilPriceDbStatusResponse> {
    return this.oilPriceDbService.getStatus();
  }

  @Get('oil-price-db/today')
  async getTodayOilPriceFromDb(
    @Query('province') province: string,
  ): Promise<OilPriceResponse> {
    return this.oilPriceDbService.getTodayOilPrice(province);
  }

  @Post('oil-price-db/update')
  async updateOilPriceDb(): Promise<OilPriceDbUpdateResponse> {
    return this.oilPriceDbService.checkAndUpdate(true);
  }

  @Patch('oil-price-db/auto-update')
  async setAutoUpdate(@Body() body: { enabled: boolean }): Promise<{ enabled: boolean }> {
    await this.oilPriceDbService.setAutoUpdateEnabled(body.enabled);
    return { enabled: body.enabled };
  }

  @Get('oil-price-db/export')
  async exportOilPriceHistory(@Res() res: Response): Promise<void> {
    const result = await this.oilPriceDbService.exportOilPriceHistoryCsv();
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('X-Export-Count', String(result.count));
    res.setHeader('X-Export-Filename', encodeURIComponent(result.filename));
    res.setHeader(
      'Content-Disposition',
      `attachment; filename="${encodeURIComponent(result.filename)}"; filename*=UTF-8''${encodeURIComponent(result.filename)}`,
    );
    res.send('\uFEFF' + result.csv);
  }

  @Get('oil-price-db/pending')
  async getPendingList(
    @Query('status') status?: string,
  ): Promise<KoilPricePendingListResponse> {
    return this.oilPriceDbService.getPendingList(status);
  }

  @Post('oil-price-db/pending/:id/accept')
  async acceptPending(
    @Param('id') id: string,
    @Body() body: { useSource: 'primary' | 'secondary'; customPrice?: number },
  ): Promise<{ success: boolean }> {
    return this.oilPriceDbService.acceptPending(id, body.useSource, body.customPrice);
  }

  @Post('oil-price-db/pending/:id/ignore')
  async ignorePending(
    @Param('id') id: string,
  ): Promise<{ success: boolean }> {
    return this.oilPriceDbService.ignorePending(id);
  }

  @Get('price-deviation/threshold')
  async getPriceDeviationThreshold(): Promise<PriceDeviationThresholdResponse> {
    const thresholdPercent = await this.oilPriceDbService.getPriceDeviationThreshold();
    return { thresholdPercent };
  }

  @Patch('price-deviation/threshold')
  async setPriceDeviationThreshold(
    @Body() body: { thresholdPercent: number },
  ): Promise<PriceDeviationThresholdResponse> {
    await this.oilPriceDbService.setPriceDeviationThreshold(Number(body.thresholdPercent));
    const thresholdPercent = await this.oilPriceDbService.getPriceDeviationThreshold();
    return { thresholdPercent };
  }

  @Post('price-deviation/check')
  async checkPriceDeviation(
    @Body() body: PriceDeviationCheckRequest,
  ): Promise<PriceDeviationCheckResponse> {
    const result = await this.oilPriceDbService.checkPriceDeviation(
      body.province,
      body.grade,
      body.date,
      Number(body.oilAmount),
      Number(body.actualAmount),
    );
    return result;
  }

  @Post('price-deviation/check-batch')
  async checkPriceDeviationBatch(
    @Body() body: { items: Array<{ id: string; province: string; grade: string; date: string; oilAmount: number; actualAmount: number }> },
  ): Promise<{ items: Array<{ id: string; result: PriceDeviationCheckResponse }> }> {
    const items = body.items || [];
    const results = await Promise.all(
      items.map(async (item) => {
        const result = await this.oilPriceDbService.checkPriceDeviation(
          item.province,
          item.grade,
          item.date,
          Number(item.oilAmount),
          Number(item.actualAmount),
        );
        return { id: item.id, result };
      }),
    );
    return { items: results };
  }
}

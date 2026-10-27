import { Injectable, Inject, Logger, NotFoundException, ConflictException, BadRequestException, forwardRef } from '@nestjs/common';
import { OilPriceService } from './oil-price.service';
import { DRIZZLE_DATABASE, type PostgresJsDatabase } from '@lark-apaas/fullstack-nestjs-core';
import { koilRecord } from '@server/database/schema';
import { eq, asc, desc, count, and, gte, lt, lte, gt, ne, inArray, sql, or } from 'drizzle-orm';
import type {
  KoilRecordWithDerived,
  KoilSummary,
  KoilRecordListResponse,
  CreateKoilRecordRequest,
  UpdateKoilRecordRequest,
  KoilTrendData,
  LastKoilRecord,
  TrendPoint,
  MonthlyCostItem,
  ImportPreviewItem,
  ImportPreviewResponse,
  ImportConfirmResponse,
  OilGrade,
  KoilRecordSource,
  MileageConflictCheckResponse,
  MileageBoundaryResponse,
} from '@shared/api.interface';

function round2(value: number): number {
  return Number(value.toFixed(2));
}

function formatMd(dateStr: string): string {
  const d = new Date(dateStr);
  const mm = d.getMonth() + 1;
  const dd = d.getDate();
  return `${mm}月${dd}日`;
}

function daysBetween(a: string, b: string): number {
  const da = new Date(a).getTime();
  const db = new Date(b).getTime();
  return Math.round((db - da) / (1000 * 60 * 60 * 24));
}

interface RawRow {
  id: string;
  reoilDate: string;
  mileage: string | number;
  oilAmount: string | number;
  payableAmount: string | number;
  actualAmount: string | number;
  oilPrice: string | number | null;
  oilGrade: string | null;
  province: string | null;
  source: string | null;
}

interface SummaryAggRow {
  totalCount: string | number;
  totalOilAmount: string | number | null;
  totalPayable: string | number | null;
  totalActual: string | number | null;
}

const SUMMARY_CACHE_TTL_MS = 30_000;
const TREND_CACHE_TTL_MS = 30_000;
const MIN_DISTANCE_FOR_OIL_CONSUMPTION_KM = 100;

@Injectable()
export class KoilRecordsService {
  private readonly logger = new Logger(KoilRecordsService.name);

  private summaryCache: { value: KoilSummary; timestamp: number } | null = null;
  private trendCache: { value: KoilTrendData; timestamp: number } | null = null;

  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: PostgresJsDatabase,
    private readonly oilPriceService: OilPriceService,
  ) {}

  invalidateCaches(): void {
    this.summaryCache = null;
    this.trendCache = null;
  }

  private async getSummaryCached(): Promise<KoilSummary> {
    const now = Date.now();
    if (this.summaryCache && now - this.summaryCache.timestamp < SUMMARY_CACHE_TTL_MS) {
      return this.summaryCache.value;
    }
    const value = await this.calculateSummaryFromDb();
    this.summaryCache = { value, timestamp: now };
    return value;
  }

  private async getTrendCached(): Promise<KoilTrendData> {
    const now = Date.now();
    if (this.trendCache && now - this.trendCache.timestamp < TREND_CACHE_TTL_MS) {
      return this.trendCache.value;
    }
    const value = await this.computeTrendFromDb();
    this.trendCache = { value, timestamp: now };
    return value;
  }

  async findAll(): Promise<KoilRecordListResponse> {
    const rows = await this.db
      .select({
        id: koilRecord.id,
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
        oilAmount: koilRecord.oilAmount,
        payableAmount: koilRecord.payableAmount,
        actualAmount: koilRecord.actualAmount,
        oilPrice: koilRecord.oilPrice,
        oilGrade: koilRecord.oilGrade,
        province: koilRecord.province,
        source: koilRecord.source,
      })
      .from(koilRecord)
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.mileage));

    const items = this.computeDerived(rows as RawRow[]);
    const summary = await this.getSummaryCached();

    return {
      items: [...items].reverse(),
      summary,
      totalCount: items.length,
      hasMore: false,
    };
  }

  async findPage(
    cursorDate: string | null,
    cursorId: string | null,
    cursorMileage: string | null,
    pageSize: number,
  ): Promise<KoilRecordListResponse> {
    const size = Math.min(Math.max(pageSize, 1), 100);

    const [countResult] = await this.db
      .select({ count: count() })
      .from(koilRecord);
    const totalCount = Number(countResult.count);

    if (totalCount === 0) {
      return {
        items: [],
        summary: this.emptySummary(),
        totalCount: 0,
        hasMore: false,
      };
    }

    let pageRows: RawRow[] = [];
    let hasMore = false;
    let prevRow: RawRow | null = null;

    const baseOrder = [desc(koilRecord.reoilDate), desc(koilRecord.mileage), desc(koilRecord.id)] as const;

    if (cursorDate && cursorId && cursorMileage !== null) {
      const rowsAfter = await this.db
        .select({
          id: koilRecord.id,
          reoilDate: koilRecord.reoilDate,
          mileage: koilRecord.mileage,
          oilAmount: koilRecord.oilAmount,
          payableAmount: koilRecord.payableAmount,
          actualAmount: koilRecord.actualAmount,
          oilPrice: koilRecord.oilPrice,
          oilGrade: koilRecord.oilGrade,
          province: koilRecord.province,
          source: koilRecord.source,
        })
        .from(koilRecord)
        .where(
          sql`((${koilRecord.reoilDate}, ${koilRecord.mileage}, ${koilRecord.id}) < (${cursorDate}::date, ${Number(cursorMileage)}, ${cursorId}::uuid))`,
        )
        .orderBy(...baseOrder)
        .limit(size + 1);

      hasMore = rowsAfter.length > size;
      pageRows = hasMore ? rowsAfter.slice(0, size) : rowsAfter;
    } else {
      const firstPageRows = await this.db
        .select({
          id: koilRecord.id,
          reoilDate: koilRecord.reoilDate,
          mileage: koilRecord.mileage,
          oilAmount: koilRecord.oilAmount,
          payableAmount: koilRecord.payableAmount,
          actualAmount: koilRecord.actualAmount,
          oilPrice: koilRecord.oilPrice,
          oilGrade: koilRecord.oilGrade,
          province: koilRecord.province,
          source: koilRecord.source,
        })
        .from(koilRecord)
        .orderBy(...baseOrder)
        .limit(size + 1);

      hasMore = firstPageRows.length > size;
      pageRows = hasMore ? firstPageRows.slice(0, size) : firstPageRows;
    }

    if (pageRows.length > 0) {
      const lastRow = pageRows[pageRows.length - 1];
      const [prevResult] = await this.db
        .select({
          id: koilRecord.id,
          reoilDate: koilRecord.reoilDate,
          mileage: koilRecord.mileage,
          oilAmount: koilRecord.oilAmount,
          payableAmount: koilRecord.payableAmount,
          actualAmount: koilRecord.actualAmount,
          oilPrice: koilRecord.oilPrice,
          oilGrade: koilRecord.oilGrade,
          province: koilRecord.province,
          source: koilRecord.source,
        })
        .from(koilRecord)
        .where(
          sql`((${koilRecord.reoilDate}, ${koilRecord.mileage}, ${koilRecord.id}) < (${lastRow.reoilDate}::date, ${lastRow.mileage}, ${lastRow.id}::uuid))`,
        )
        .orderBy(...baseOrder)
        .limit(1);
      prevRow = (prevResult as RawRow) || null;
    }

    const descRows = pageRows;
    const ascRows = [...descRows].reverse();

    let derivedItems: KoilRecordWithDerived[];
    if (prevRow) {
      const rowsWithPrev = [prevRow, ...ascRows];
      const derivedWithPrev = this.computeDerived(rowsWithPrev);
      derivedItems = derivedWithPrev.slice(1);
    } else {
      derivedItems = this.computeDerived(ascRows);
    }

    const descItems = [...derivedItems].reverse();
    const summary = await this.getSummaryCached();

    return {
      items: descItems,
      summary,
      totalCount,
      hasMore,
    };
  }

  private emptySummary(): KoilSummary {
    return {
      totalCount: 0,
      totalDistance: 0,
      totalOilAmount: 0,
      totalPayable: 0,
      totalActual: 0,
      avgOilConsumption: 0,
      avgCostPerKm: 0,
      avgUnitPriceActual: 0,
      avgDistancePerReoil: 0,
      abnormalCount: 0,
    };
  }

  private async calculateSummaryFromDb(): Promise<KoilSummary> {
    const agg = await this.db
      .select({
        totalCount: count(),
        totalOilAmount: sql<number>`SUM(${koilRecord.oilAmount})`,
        totalPayable: sql<number>`SUM(${koilRecord.payableAmount})`,
        totalActual: sql<number>`SUM(${koilRecord.actualAmount})`,
      })
      .from(koilRecord);

    const row = (agg[0] as SummaryAggRow) || { totalCount: 0, totalOilAmount: null, totalPayable: null, totalActual: null };
    const totalCount = Number(row.totalCount);

    if (totalCount === 0) {
      return this.emptySummary();
    }

    const totalOilAmount = Number(row.totalOilAmount ?? 0);
    const totalPayable = Number(row.totalPayable ?? 0);
    const totalActual = Number(row.totalActual ?? 0);

    if (totalCount <= 1) {
      return {
        totalCount,
        totalDistance: 0,
        totalOilAmount: round2(totalOilAmount),
        totalPayable: round2(totalPayable),
        totalActual: round2(totalActual),
        avgOilConsumption: 0,
        avgCostPerKm: 0,
        avgUnitPriceActual: totalOilAmount > 0 ? round2(totalActual / totalOilAmount) : 0,
        avgDistancePerReoil: 0,
        abnormalCount: 0,
      };
    }

    const allRows = await this.db
      .select({
        id: koilRecord.id,
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
        oilAmount: koilRecord.oilAmount,
        payableAmount: koilRecord.payableAmount,
        actualAmount: koilRecord.actualAmount,
        oilPrice: koilRecord.oilPrice,
        oilGrade: koilRecord.oilGrade,
        source: koilRecord.source,
      })
      .from(koilRecord)
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.mileage));

    const items = this.computeDerived(allRows as RawRow[]);
    return this.calculateSummary(items);
  }

  private computeDerived(rows: RawRow[]): KoilRecordWithDerived[] {
    const rawItems = rows.map((row: RawRow, index: number) => {
      const mileage = Number(row.mileage);
      const oilAmount = Number(row.oilAmount);
      const payableAmount = Number(row.payableAmount);
      const actualAmount = Number(row.actualAmount);
      const oilPrice = row.oilPrice != null ? Number(row.oilPrice) : null;
      const oilGrade = (row.oilGrade as OilGrade | null) || null;
      const province = row.province || null;
      const sourceVal =
        row.source === 'entry_page' || row.source === 'backfill_modal'
          ? (row.source as KoilRecordSource)
          : null;

      const unitPricePayable = round2(payableAmount / oilAmount);
      const unitPriceActual = round2(actualAmount / oilAmount);

      if (index === 0) {
        return {
          id: row.id,
          reoilDate: row.reoilDate,
          mileage,
          oilAmount,
          payableAmount,
          actualAmount,
          oilPrice,
          oilGrade,
          province,
          source: sourceVal,
          prevReoilDate: null,
          prevMileage: null,
          prevOilAmount: null,
          prevActualAmount: null,
          distance: null,
          oilDays: null,
          oilDateRange: null,
          unitPricePayable,
          unitPriceActual,
          oilConsumption: null,
          costPerKm: null,
          isAbnormalOilConsumption: false,
          oilConsumptionLevel: null,
          shortDistanceSkipped: false,
          priceDeviation: null,
        };
      }

      const prev = rows[index - 1];
      const prevMileage = Number(prev.mileage);

      const distance = mileage - prevMileage;
      const oilDays = daysBetween(prev.reoilDate, row.reoilDate);
      const oilDateRange = `${formatMd(prev.reoilDate)} 至 ${formatMd(row.reoilDate)}`;
      const shortDistanceSkipped =
        distance > 0 && distance < MIN_DISTANCE_FOR_OIL_CONSUMPTION_KM;
      const oilConsumption =
        distance >= MIN_DISTANCE_FOR_OIL_CONSUMPTION_KM
          ? round2((oilAmount / distance) * 100)
          : null;
      const costPerKm =
        distance >= MIN_DISTANCE_FOR_OIL_CONSUMPTION_KM
          ? round2(actualAmount / distance)
          : null;

      return {
        id: row.id,
        reoilDate: row.reoilDate,
        mileage,
        oilAmount,
        payableAmount,
        actualAmount,
        oilPrice,
        oilGrade,
        province,
        source: sourceVal,
        prevReoilDate: prev.reoilDate,
        prevMileage,
        prevOilAmount: Number(prev.oilAmount),
        prevActualAmount: Number(prev.actualAmount),
        distance,
        oilDays,
        oilDateRange,
        unitPricePayable,
        unitPriceActual,
        oilConsumption,
        costPerKm,
        isAbnormalOilConsumption: false,
        oilConsumptionLevel: null,
        shortDistanceSkipped,
        priceDeviation: null,
      };
    });

    const consumptions = rawItems
      .filter((item) => item.oilConsumption != null)
      .map((item) => item.oilConsumption as number);
    const avgConsumption =
      consumptions.length > 0
        ? consumptions.reduce((sum: number, v: number) => sum + v, 0) / consumptions.length
        : 0;

    if (avgConsumption > 0) {
      const highThreshold = avgConsumption * 1.3;
      const lowThreshold = avgConsumption * 0.7;
      for (const item of rawItems) {
        if (item.oilConsumption == null) continue;
        if (item.oilConsumption > highThreshold) {
          item.isAbnormalOilConsumption = true;
          item.oilConsumptionLevel = 'high';
        } else if (item.oilConsumption < lowThreshold) {
          item.isAbnormalOilConsumption = true;
          item.oilConsumptionLevel = 'low';
        } else {
          item.oilConsumptionLevel = 'normal';
        }
      }
    }

    return rawItems;
  }

  private calculateSummary(items: KoilRecordWithDerived[]): KoilSummary {
    const totalCount = items.length;

    if (totalCount === 0) {
      return this.emptySummary();
    }

    const totalOilAmount = items.reduce(
      (sum: number, item: KoilRecordWithDerived) => sum + item.oilAmount,
      0,
    );
    const totalPayable = items.reduce(
      (sum: number, item: KoilRecordWithDerived) => sum + item.payableAmount,
      0,
    );
    const totalActual = items.reduce(
      (sum: number, item: KoilRecordWithDerived) => sum + item.actualAmount,
      0,
    );

    const distances = items
      .filter((item: KoilRecordWithDerived) => item.distance != null)
      .map((item: KoilRecordWithDerived) => item.distance as number);
    const totalDistance = distances.reduce((sum: number, d: number) => sum + d, 0);
    const validSegments = distances.length;

    const segmentOilAmount = items.reduce((sum: number, item: KoilRecordWithDerived) => {
      return item.distance != null && item.distance >= MIN_DISTANCE_FOR_OIL_CONSUMPTION_KM
        ? sum + item.oilAmount
        : sum;
    }, 0);
    const segmentActualAmount = items.reduce((sum: number, item: KoilRecordWithDerived) => {
      return item.distance != null && item.distance >= MIN_DISTANCE_FOR_OIL_CONSUMPTION_KM
        ? sum + item.actualAmount
        : sum;
    }, 0);
    const effectiveDistance = items.reduce((sum: number, item: KoilRecordWithDerived) => {
      return item.distance != null && item.distance >= MIN_DISTANCE_FOR_OIL_CONSUMPTION_KM
        ? sum + item.distance
        : sum;
    }, 0);

    let avgOilConsumption = 0;
    let avgCostPerKm = 0;
    let avgDistancePerReoil = 0;

    if (effectiveDistance > 0 && segmentOilAmount > 0) {
      avgOilConsumption = round2((segmentOilAmount / effectiveDistance) * 100);
      avgCostPerKm = round2(segmentActualAmount / effectiveDistance);
    }

    if (validSegments > 0) {
      avgDistancePerReoil = round2(totalDistance / validSegments);
    }

    const avgUnitPriceActual = totalOilAmount > 0 ? round2(totalActual / totalOilAmount) : 0;

    const abnormalCount = items.filter(
      (item: KoilRecordWithDerived) => item.isAbnormalOilConsumption,
    ).length;

    return {
      totalCount,
      totalDistance: round2(totalDistance),
      totalOilAmount: round2(totalOilAmount),
      totalPayable: round2(totalPayable),
      totalActual: round2(totalActual),
      avgOilConsumption,
      avgCostPerKm,
      avgUnitPriceActual,
      avgDistancePerReoil,
      abnormalCount,
    };
  }

  private async validateOilAmountAndAmount(
    oilAmount: number,
    actualAmount: number,
    payableAmountInput: number | null | undefined,
    province: string | null | undefined,
    oilGrade: string | null | undefined,
    reoilDate: string,
  ): Promise<void> {
    if (oilAmount < 1) {
      throw new BadRequestException('加油量过小，请确认');
    }
    if (oilAmount > 200) {
      throw new BadRequestException('加油量超过上限，请确认是否正确');
    }
    if (actualAmount > 1500) {
      throw new BadRequestException('实付金额超过1500元，请确认是否正确');
    }
    let payableForCheck: number | null = null;
    if (payableAmountInput != null && Number(payableAmountInput) > 0) {
      payableForCheck = Number(payableAmountInput);
    } else if (province && oilGrade && reoilDate) {
      const historyPrice = await this.oilPriceService.getHistoryPrice(
        province,
        oilGrade,
        reoilDate,
      );
      if (historyPrice && historyPrice.price > 0) {
        payableForCheck = oilAmount * historyPrice.price;
      }
    }
    if (payableForCheck != null && payableForCheck > 0) {
      const ratio = actualAmount / payableForCheck;
      if (ratio > 1.01) {
        throw new BadRequestException('实付金额高于应付金额，请确认');
      }
      if (ratio < 0.75) {
        throw new BadRequestException(
          '实付金额与应付金额差异过大（优惠超过25%），请确认',
        );
      }
    }
  }

  async create(dto: CreateKoilRecordRequest): Promise<{ id: string }> {
    const mileageNum = Number(dto.mileage);
    const oilAmountNum = Number(dto.oilAmount);
    const actualAmountNum = Number(dto.actualAmount);
    const payableAmountNum = dto.payableAmount != null ? Number(dto.payableAmount) : null;

    const recordSource = dto.source || 'entry_page';
    const today = new Date()
      .toLocaleDateString('en-CA', {
        timeZone: 'Asia/Shanghai',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      })
      .replace(/\//g, '-');
    if (dto.reoilDate > today) {
      throw new BadRequestException(
        recordSource === 'entry_page'
          ? '加油日期不能是未来日期'
          : '不能补录未来日期的记录',
      );
    }
    if (recordSource !== 'entry_page') {
      const firstRow = await this.db
        .select({
          reoilDate: koilRecord.reoilDate,
          mileage: koilRecord.mileage,
        })
        .from(koilRecord)
        .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.mileage))
        .limit(1);
      if (firstRow.length > 0) {
        const firstDate = String(firstRow[0].reoilDate);
        const firstMileage = Number(firstRow[0].mileage);
        if (
          dto.reoilDate < firstDate ||
          (dto.reoilDate === firstDate && mileageNum < firstMileage)
        ) {
          throw new BadRequestException('补录不能早于你的第一条记录');
        }
      }
    }

    await this.validateOilAmountAndAmount(
      oilAmountNum,
      actualAmountNum,
      payableAmountNum,
      dto.province ?? null,
      dto.oilGrade ?? null,
      dto.reoilDate,
    );

    if (recordSource === 'entry_page') {
      const latestRows = await this.db
        .select({
          reoilDate: koilRecord.reoilDate,
          mileage: koilRecord.mileage,
        })
        .from(koilRecord)
        .orderBy(desc(koilRecord.reoilDate), desc(koilRecord.mileage))
        .limit(1);
      const latest = latestRows[0];
      if (latest && mileageNum <= Number(latest.mileage)) {
        throw new BadRequestException(
          `本次加油里程必须大于上次加油里程（${Number(latest.mileage)} km）`,
        );
      }
    }

    const chainResult = await this.checkMileageChain(
      dto.reoilDate,
      mileageNum,
    );
    if (chainResult.hasConflict && chainResult.message) {
      throw new ConflictException(chainResult.message);
    }

    const insertData: Record<string, string> = {
      reoilDate: dto.reoilDate,
      mileage: String(dto.mileage),
      oilAmount: String(dto.oilAmount),
      payableAmount: String(dto.payableAmount),
      actualAmount: String(dto.actualAmount),
      source: recordSource,
    };
    if (dto.oilPrice != null && String(dto.oilPrice).trim() !== '') {
      insertData.oilPrice = String(dto.oilPrice);
    }
    if (dto.oilGrade) {
      insertData.oilGrade = dto.oilGrade;
    }
    if (dto.province && dto.province.trim() !== '') {
      insertData.province = dto.province.trim();
    }

    try {
      const [inserted] = await this.db
        .insert(koilRecord)
        .values(insertData as any)
        .returning({ id: koilRecord.id });

      this.invalidateCaches();
      return { id: inserted.id };
    } catch (err) {
      const code = this.extractPostgresErrorCode(err);
      if (code === '23505') {
        throw new ConflictException(
          '该日期和里程的加油记录已存在，请勿重复提交',
        );
      }
      throw err;
    }
  }

  async update(id: string, dto: UpdateKoilRecordRequest): Promise<void> {
    const existing = await this.db
      .select({
        id: koilRecord.id,
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
        oilAmount: koilRecord.oilAmount,
        payableAmount: koilRecord.payableAmount,
        actualAmount: koilRecord.actualAmount,
        province: koilRecord.province,
        oilGrade: koilRecord.oilGrade,
      })
      .from(koilRecord)
      .where(eq(koilRecord.id, id));

    if (existing.length === 0) {
      throw new NotFoundException('加油记录不存在');
    }

    const current = existing[0];
    const checkOilAmount = dto.oilAmount !== undefined
      ? Number(dto.oilAmount)
      : Number(current.oilAmount);
    const checkActualAmount = dto.actualAmount !== undefined
      ? Number(dto.actualAmount)
      : Number(current.actualAmount);
    const checkPayable = dto.payableAmount !== undefined
      ? Number(dto.payableAmount)
      : Number(current.payableAmount);
    const checkProvince = dto.province !== undefined
      ? dto.province
      : (current.province as string | null);
    const checkGrade = dto.oilGrade !== undefined
      ? dto.oilGrade
      : (current.oilGrade as string | null);
    const checkDate = dto.reoilDate !== undefined
      ? dto.reoilDate
      : String(current.reoilDate);
    const checkMileage = dto.mileage !== undefined
      ? Number(dto.mileage)
      : Number(current.mileage);

    const today = new Date()
      .toLocaleDateString('en-CA', {
        timeZone: 'Asia/Shanghai',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      })
      .replace(/\//g, '-');
    if (checkDate > today) {
      throw new BadRequestException('不能补录未来日期的记录');
    }

    const firstRow = await this.db
      .select({
        id: koilRecord.id,
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
      })
      .from(koilRecord)
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.mileage))
      .limit(1);
    if (firstRow.length > 0 && firstRow[0].id !== id) {
      const firstDate = String(firstRow[0].reoilDate);
      const firstMileage = Number(firstRow[0].mileage);
      if (
        checkDate < firstDate ||
        (checkDate === firstDate && checkMileage < firstMileage)
      ) {
        throw new BadRequestException('不能早于你的第一条加油记录');
      }
    }

    await this.validateOilAmountAndAmount(
      checkOilAmount,
      checkActualAmount,
      checkPayable,
      checkProvince,
      checkGrade,
      checkDate,
    );

    const chainResult = await this.checkMileageChain(
      checkDate,
      checkMileage,
      id,
    );
    if (chainResult.hasConflict && chainResult.message) {
      throw new ConflictException(chainResult.message);
    }

    const patch: Record<string, string | null> = {};
    if (dto.reoilDate !== undefined) patch.reoilDate = dto.reoilDate;
    if (dto.mileage !== undefined) patch.mileage = String(dto.mileage);
    if (dto.oilAmount !== undefined) patch.oilAmount = String(dto.oilAmount);
    if (dto.payableAmount !== undefined) patch.payableAmount = String(dto.payableAmount);
    if (dto.actualAmount !== undefined) patch.actualAmount = String(dto.actualAmount);
    if (dto.oilPrice !== undefined) {
      patch.oilPrice = dto.oilPrice != null && String(dto.oilPrice).trim() !== '' ? String(dto.oilPrice) : null;
    }
    if (dto.oilGrade !== undefined) {
      patch.oilGrade = dto.oilGrade || null;
    }
    if (dto.province !== undefined) {
      patch.province = dto.province && dto.province.trim() !== '' ? dto.province.trim() : null;
    }

    if (Object.keys(patch).length === 0) {
      throw new BadRequestException('未提供可更新字段');
    }

    try {
      const updated = await this.db
        .update(koilRecord)
        .set(patch as any)
        .where(eq(koilRecord.id, id))
        .returning({ id: koilRecord.id });

      if (updated.length === 0) {
        throw new NotFoundException('加油记录不存在');
      }

      this.invalidateCaches();
    } catch (err) {
      const code = this.extractPostgresErrorCode(err);
      if (code === '23505') {
        throw new ConflictException(
          '该日期和里程的加油记录已存在，请勿重复提交',
        );
      }
      throw err;
    }
  }

  private extractPostgresErrorCode(error: unknown): string | undefined {
    let current: unknown = error;
    for (let depth = 0; depth < 4 && current && typeof current === 'object'; depth += 1) {
      const { code, cause } = current as { code?: unknown; cause?: unknown };
      if (typeof code === 'string') return code;
      current = cause;
    }
    return undefined;
  }

  async remove(id: string): Promise<void> {
    const deleted = await this.db
      .delete(koilRecord)
      .where(eq(koilRecord.id, id))
      .returning({ id: koilRecord.id });

    if (deleted.length === 0) {
      throw new NotFoundException('加油记录不存在');
    }

    this.invalidateCaches();
  }

  async checkMileageChain(
    reoilDate: string,
    mileage: number,
    excludeId?: string,
  ): Promise<MileageConflictCheckResponse> {
    const prevConditions = [lt(koilRecord.reoilDate, reoilDate)];
    const nextConditions = [gt(koilRecord.reoilDate, reoilDate)];
    const sameDayConditions = [eq(koilRecord.reoilDate, reoilDate)];
    if (excludeId) {
      prevConditions.push(ne(koilRecord.id, excludeId));
      nextConditions.push(ne(koilRecord.id, excludeId));
      sameDayConditions.push(ne(koilRecord.id, excludeId));
    }

    const prevRows = await this.db
      .select({
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
      })
      .from(koilRecord)
      .where(and(...prevConditions))
      .orderBy(desc(koilRecord.reoilDate), desc(koilRecord.mileage))
      .limit(1);

    const nextRows = await this.db
      .select({
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
      })
      .from(koilRecord)
      .where(and(...nextConditions))
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.mileage))
      .limit(1);

    const sameDayRows = await this.db
      .select({ mileage: koilRecord.mileage })
      .from(koilRecord)
      .where(and(...sameDayConditions))
      .orderBy(asc(koilRecord.mileage));

    const prev = prevRows[0] || null;
    const next = nextRows[0] || null;

    const prevDate = prev ? String(prev.reoilDate) : null;
    const prevMileage = prev ? Number(prev.mileage) : null;
    const nextDate = next ? String(next.reoilDate) : null;
    const nextMileage = next ? Number(next.mileage) : null;
    const sameDayMileages = sameDayRows.map((row) => Number(row.mileage));

    const messages: string[] = [];

    if (prev && mileage <= prevMileage) {
      messages.push(
        `与${formatMd(prevDate!)}记录(${prevMileage}km)冲突：补录里程需大于${prevMileage}km`,
      );
    }
    if (next && mileage >= nextMileage) {
      messages.push(
        `与${formatMd(nextDate!)}记录(${nextMileage}km)冲突：补录里程需小于${nextMileage}km`,
      );
    }

    return {
      hasConflict: messages.length > 0,
      message: messages.length > 0 ? messages.join('；') : null,
      prevDate,
      prevMileage,
      nextDate,
      nextMileage,
      sameDayMileages,
    };
  }

  async checkDuplicate(
    reoilDate: string,
    mileage: number,
    oilAmount?: number,
    actualAmount?: number,
    excludeId?: string,
  ): Promise<{ exists: boolean; sameDayCount: number; similarExists: boolean }> {
    const whereConditions = [eq(koilRecord.reoilDate, reoilDate)];
    if (excludeId) {
      whereConditions.push(ne(koilRecord.id, excludeId));
    }
    const rows = await this.db
      .select({
        mileage: koilRecord.mileage,
        oilAmount: koilRecord.oilAmount,
        actualAmount: koilRecord.actualAmount,
        source: koilRecord.source,
      })
      .from(koilRecord)
      .where(and(...whereConditions));

    const sameDayCount = rows.length;
    const exists = rows.some((row) => Number(row.mileage) === mileage);

    let similarExists = false;
    if (oilAmount !== undefined && actualAmount !== undefined && !exists) {
      similarExists = rows.some((row) => {
        const fa = Number(row.oilAmount);
        const aa = Number(row.actualAmount);
        return Math.abs(fa - oilAmount) <= 2 && Math.abs(aa - actualAmount) <= 10;
      });
    }

    return { exists, sameDayCount, similarExists };
  }

  async precheck(dto: {
    reoilDate: string;
    mileage?: number;
    oilAmount?: number;
    actualAmount?: number;
    excludeId?: string;
    includeBoundary?: boolean;
    includePrevRecord?: boolean;
  }): Promise<{
    duplicate: { exists: boolean; sameDayCount: number; similarExists: boolean };
    mileageChain: MileageConflictCheckResponse;
    boundary: MileageBoundaryResponse | null;
    prevRecord: LastKoilRecord | null;
    isBeforeFirst: boolean;
    firstDate: string | null;
    firstMileage: number | null;
  }> {
    const { reoilDate, mileage, oilAmount, actualAmount, excludeId, includeBoundary, includePrevRecord } = dto;
    const hasMileage = mileage !== undefined && !Number.isNaN(mileage) && mileage > 0;

    const firstRows = await this.db
      .select({
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
        id: koilRecord.id,
      })
      .from(koilRecord)
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.mileage))
      .limit(1);
    const first = firstRows[0] || null;
    const firstDate = first ? String(first.reoilDate) : null;
    const firstMileage = first ? Number(first.mileage) : null;
    const isBeforeFirst = first != null && first.id !== excludeId && (
      reoilDate < firstDate! ||
      (reoilDate === firstDate && hasMileage && mileage! < firstMileage!)
    );

    const [duplicate, mileageChain, boundary, prevRecord] = await Promise.all([
      hasMileage
        ? this.checkDuplicate(reoilDate, mileage, oilAmount, actualAmount, excludeId)
        : Promise.resolve({ exists: false, sameDayCount: 0, similarExists: false }),
      hasMileage
        ? this.checkMileageChain(reoilDate, mileage, excludeId)
        : Promise.resolve({
            hasConflict: false,
            message: null,
            prevDate: null,
            prevMileage: null,
            nextDate: null,
            nextMileage: null,
            sameDayMileages: [],
          }),
      includeBoundary ? this.getMileageBoundary(reoilDate) : Promise.resolve(null),
      includePrevRecord ? this.getPrevRecord(reoilDate, mileage, excludeId) : Promise.resolve(null),
    ]);

    return {
      duplicate, mileageChain, boundary, prevRecord,
      isBeforeFirst, firstDate, firstMileage,
    };
  }

  async getMileageBoundary(reoilDate: string): Promise<MileageBoundaryResponse> {
    const prevRows = await this.db
      .select({
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
      })
      .from(koilRecord)
      .where(lt(koilRecord.reoilDate, reoilDate))
      .orderBy(desc(koilRecord.reoilDate), desc(koilRecord.mileage))
      .limit(1);

    const nextRows = await this.db
      .select({
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
      })
      .from(koilRecord)
      .where(gt(koilRecord.reoilDate, reoilDate))
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.mileage))
      .limit(1);

    const sameDayRows = await this.db
      .select({ mileage: koilRecord.mileage })
      .from(koilRecord)
      .where(eq(koilRecord.reoilDate, reoilDate))
      .orderBy(asc(koilRecord.mileage));

    const firstRows = await this.db
      .select({
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
      })
      .from(koilRecord)
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.mileage))
      .limit(1);

    const prev = prevRows[0] || null;
    const next = nextRows[0] || null;
    const first = firstRows[0] || null;
    const sameDayMileages = sameDayRows.map((row) => Number(row.mileage));

    const prevDate = prev ? String(prev.reoilDate) : null;
    const prevMileage = prev ? Number(prev.mileage) : null;
    const nextDate = next ? String(next.reoilDate) : null;
    const nextMileage = next ? Number(next.mileage) : null;
    const firstDate = first ? String(first.reoilDate) : null;
    const firstMileage = first ? Number(first.mileage) : null;

    const isBeforeFirst = first != null && reoilDate < firstDate;

    return {
      prevDate,
      prevMileage,
      nextDate,
      nextMileage,
      sameDayMileages,
      isBeforeFirst,
      firstDate,
      firstMileage,
    };
  }

  async getTrendData(): Promise<KoilTrendData> {
    return this.getTrendCached();
  }

  private async computeTrendFromDb(): Promise<KoilTrendData> {
    const rows = await this.db
      .select({
        id: koilRecord.id,
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
        oilAmount: koilRecord.oilAmount,
        payableAmount: koilRecord.payableAmount,
        actualAmount: koilRecord.actualAmount,
        oilPrice: koilRecord.oilPrice,
        oilGrade: koilRecord.oilGrade,
        source: koilRecord.source,
      })
      .from(koilRecord)
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.id));

    const items = this.computeDerived(rows as RawRow[]);

    const points: TrendPoint[] = items
      .filter((item: KoilRecordWithDerived) => item.oilConsumption !== null)
      .map((item: KoilRecordWithDerived) => ({
        reoilDate: item.reoilDate,
        oilConsumption: item.oilConsumption,
        costPerKm: item.costPerKm,
      }));

    const monthMap = new Map<string, number>();
    for (const item of items) {
      const d = new Date(item.reoilDate);
      const year = d.getFullYear();
      const month = d.getMonth() + 1;
      const key = `${year}-${String(month).padStart(2, '0')}`;
      monthMap.set(key, (monthMap.get(key) || 0) + item.actualAmount);
    }

    const monthlyCost: MonthlyCostItem[] = Array.from(monthMap.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, total]) => {
        const [year, month] = key.split('-');
        return {
          monthKey: key,
          monthLabel: `${year}年${parseInt(month, 10)}月`,
          totalActual: round2(total),
        };
      });

    return { points, monthlyCost };
  }

  async getLatestRecord(): Promise<LastKoilRecord | null> {
    const rows = await this.db
      .select({
        id: koilRecord.id,
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
        oilAmount: koilRecord.oilAmount,
        actualAmount: koilRecord.actualAmount,
        oilPrice: koilRecord.oilPrice,
        oilGrade: koilRecord.oilGrade,
        source: koilRecord.source,
      })
      .from(koilRecord)
      .orderBy(desc(koilRecord.reoilDate), desc(koilRecord.mileage))
      .limit(2);

    if (rows.length === 0) return null;

    const last = rows[0];
    const prev = rows.length >= 2 ? rows[1] : null;

    const lastMileage = Number(last.mileage);
    const prevMileage = prev ? Number(prev.mileage) : 0;
    const distance = prev ? lastMileage - prevMileage : null;
    const lastOilAmount = Number(last.oilAmount);
    const avgOilConsumption =
      prev && distance && distance > 0
        ? (lastOilAmount / distance) * 100
        : null;

    return {
      reoilDate: last.reoilDate,
      mileage: lastMileage,
      oilAmount: lastOilAmount,
      actualAmount: Number(last.actualAmount),
      oilPrice: last.oilPrice != null ? Number(last.oilPrice) : null,
      oilGrade: (last.oilGrade as OilGrade | null) || null,
      distance: distance != null ? round2(distance) : null,
      avgOilConsumption:
        avgOilConsumption != null ? round2(avgOilConsumption) : null,
    };
  }

  async getPrevRecord(
    reoilDate: string,
    mileage?: number,
    excludeId?: string,
  ): Promise<LastKoilRecord | null> {
    const hasMileage = mileage !== undefined && !Number.isNaN(mileage);
    const whereConditions = [];
    if (hasMileage) {
      whereConditions.push(
        or(
          lt(koilRecord.reoilDate, reoilDate),
          and(
            eq(koilRecord.reoilDate, reoilDate),
            lt(koilRecord.mileage, String(mileage)),
          ),
        ),
      );
    } else {
      whereConditions.push(lte(koilRecord.reoilDate, reoilDate));
    }
    if (excludeId) {
      whereConditions.push(ne(koilRecord.id, excludeId));
    }
    const rows = await this.db
      .select({
        id: koilRecord.id,
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
        oilAmount: koilRecord.oilAmount,
        actualAmount: koilRecord.actualAmount,
        oilPrice: koilRecord.oilPrice,
        oilGrade: koilRecord.oilGrade,
        source: koilRecord.source,
      })
      .from(koilRecord)
      .where(and(...whereConditions))
      .orderBy(desc(koilRecord.reoilDate), desc(koilRecord.mileage))
      .limit(2);

    if (rows.length === 0) return null;

    const last = rows[0];
    const prev = rows.length >= 2 ? rows[1] : null;

    const lastMileage = Number(last.mileage);
    const prevMileage = prev ? Number(prev.mileage) : 0;
    const distance = prev ? lastMileage - prevMileage : null;
    const lastOilAmount = Number(last.oilAmount);
    const avgOilConsumption =
      prev && distance && distance > 0
        ? (lastOilAmount / distance) * 100
        : null;

    return {
      reoilDate: last.reoilDate,
      mileage: lastMileage,
      oilAmount: lastOilAmount,
      actualAmount: Number(last.actualAmount),
      oilPrice: last.oilPrice != null ? Number(last.oilPrice) : null,
      oilGrade: (last.oilGrade as OilGrade | null) || null,
      distance: distance != null ? round2(distance) : null,
      avgOilConsumption:
        avgOilConsumption != null ? round2(avgOilConsumption) : null,
    };
  }

  async exportAllCsv(): Promise<{ count: number; csv: string; filename: string }> {
    const rows = await this.db
      .select({
        id: koilRecord.id,
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
        oilAmount: koilRecord.oilAmount,
        payableAmount: koilRecord.payableAmount,
        actualAmount: koilRecord.actualAmount,
        oilPrice: koilRecord.oilPrice,
        oilGrade: koilRecord.oilGrade,
        source: koilRecord.source,
        province: koilRecord.province,
      })
      .from(koilRecord)
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.id));

    const items = this.computeDerived(rows as RawRow[]);

    const headers = [
      '加油日期',
      '上次加油日期',
      '油号',
      '加油量(L)',
      '油价(元/L)',
      '应付金额(元)',
      '实付金额(元)',
      '本次里程(km)',
      '上次里程(km)',
      '行驶里程(km)',
      '上次加油量(L)',
      '上次实付金额(元)',
      '应付单价(元/L)',
      '实付单价(元/L)',
      '用油天数',
      '平均油耗(L/100km)',
      '每公里成本(元/km)',
      '油耗状态',
      '省份',
      '来源',
    ];

    const escapeCsv = (value: string | number | null): string => {
      const str = value === null || value === undefined ? '' : String(value);
      if (str.includes(',') || str.includes('"') || str.includes('\n')) {
        return `"${str.replace(/"/g, '""')}"`;
      }
      return str;
    };

    const lines = [headers.join(',')];
    for (const item of items) {
      const levelText =
        item.oilConsumptionLevel === 'high'
          ? '偏高'
          : item.oilConsumptionLevel === 'low'
            ? '偏低'
            : item.oilConsumptionLevel === 'normal'
              ? '正常'
              : '-';
      const row = [
        item.reoilDate,
        item.prevReoilDate ?? '-',
        item.oilGrade ? `${item.oilGrade}号` : '-',
        item.oilAmount.toFixed(2),
        item.oilPrice != null ? item.oilPrice.toFixed(2) : '-',
        item.payableAmount.toFixed(2),
        item.actualAmount.toFixed(2),
        item.mileage.toFixed(2),
        item.prevMileage !== null ? item.prevMileage.toFixed(2) : '-',
        item.distance !== null ? item.distance.toFixed(2) : '-',
        item.prevOilAmount !== null ? item.prevOilAmount.toFixed(2) : '-',
        item.prevActualAmount !== null ? item.prevActualAmount.toFixed(2) : '-',
        item.unitPricePayable.toFixed(2),
        item.unitPriceActual.toFixed(2),
        item.oilDays !== null ? String(item.oilDays) : '-',
        item.oilConsumption !== null ? item.oilConsumption.toFixed(2) : '-',
        item.costPerKm !== null ? item.costPerKm.toFixed(3) : '-',
        levelText,
        item.province ?? '-',
        item.source ?? '-',
      ];
      lines.push(row.map(escapeCsv).join(','));
    }

    const today = new Date().toISOString().slice(0, 10);
    const filename = `KOiL_加油记录_${today}.csv`;
    return { count: items.length, csv: lines.join('\n'), filename };
  }

  async exportBackup(settings: Record<string, string | number | boolean>): Promise<{
    count: number;
    json: string;
    filename: string;
  }> {
    const rows = await this.db
      .select({
        id: koilRecord.id,
        reoilDate: koilRecord.reoilDate,
        mileage: koilRecord.mileage,
        oilAmount: koilRecord.oilAmount,
        payableAmount: koilRecord.payableAmount,
        actualAmount: koilRecord.actualAmount,
        oilPrice: koilRecord.oilPrice,
        oilGrade: koilRecord.oilGrade,
        source: koilRecord.source,
        province: koilRecord.province,
      })
      .from(koilRecord)
      .orderBy(asc(koilRecord.reoilDate), asc(koilRecord.id));

    const items = this.computeDerived(rows as RawRow[]);
    const today = new Date().toISOString().slice(0, 10);
    const filename = `KOiL_完整备份_${today}.json`;
    const payload = {
      version: 1,
      exportedAt: new Date().toISOString(),
      koilRecords: items,
      settings,
    };
    return { count: items.length, json: JSON.stringify(payload, null, 2), filename };
  }

  async previewImport(csvContent: string): Promise<ImportPreviewResponse> {
    const lines = csvContent
      .replace(/^\uFEFF/, '')
      .split(/\r?\n/)
      .filter((line) => line.trim() !== '');

    if (lines.length === 0) {
      return { valid: [], duplicates: [], mileageConflicts: [], errors: [], totalRows: 0 };
    }

    const headers = this.parseCsvLine(lines[0]);
    const dataLines = lines.slice(1);

    const valid: ImportPreviewItem[] = [];
    const duplicates: ImportPreviewItem[] = [];
    const mileageConflicts: ImportPreviewItem[] = [];
    const errors: ImportPreviewItem[] = [];

    const dateIdx = this.findColumnIndex(headers, ['加油日期', '日期', 'reoilDate', 'date']);
    const mileageIdx = this.findColumnIndex(headers, ['本次里程', '里程', '里程表', 'mileage']);
    const oilIdx = this.findColumnIndex(headers, ['加油量', '油量', 'oilAmount', 'fuel']);
    const actualIdx = this.findColumnIndex(headers, ['实付金额', '实付', 'actualAmount', 'actual']);
    const payableIdx = this.findColumnIndex(headers, ['应付金额', '应付', 'payableAmount', 'payable']);
    const gradeIdx = this.findColumnIndex(headers, ['油号', '油品', 'oilGrade', 'grade']);
    const priceIdx = this.findColumnIndex(headers, ['油价', '单价', 'oilPrice', 'price']);
    const provinceIdx = this.findColumnIndex(headers, ['省份', '省市', 'province']);
    const sourceIdx = this.findColumnIndex(headers, ['来源', 'source']);

    const parsedRows: Array<{
      rowNumber: number;
      reoilDate: string;
      mileage: number;
      oilAmount: number;
      actualAmount: number;
      payableAmount: number;
      oilGrade: OilGrade | null;
      oilPrice: number | null;
      province: string | null;
      source: string | null;
      errorMessage?: string;
    }> = [];

    for (let i = 0; i < dataLines.length; i++) {
      const rowNum = i + 2;
      const cols = this.parseCsvLine(dataLines[i]);

      const dateStr = dateIdx >= 0 ? cols[dateIdx]?.trim() : '';
      const mileageStr = mileageIdx >= 0 ? cols[mileageIdx]?.trim() : '';
      const oilStr = oilIdx >= 0 ? cols[oilIdx]?.trim() : '';
      const actualStr = actualIdx >= 0 ? cols[actualIdx]?.trim() : '';
      const payableStr = payableIdx >= 0 ? cols[payableIdx]?.trim() : '';
      const gradeStr = gradeIdx >= 0 ? cols[gradeIdx]?.trim().replace(/[号#汽油柴油]/g, '') : '';
      const priceStr = priceIdx >= 0 ? cols[priceIdx]?.trim() : '';
      const provinceStr = provinceIdx >= 0 ? cols[provinceIdx]?.trim() : '';
      const sourceStr = sourceIdx >= 0 ? cols[sourceIdx]?.trim() : '';

      if (!dateStr || !mileageStr || !oilStr || !actualStr) {
        errors.push({
          rowNumber: rowNum,
          status: 'error',
          errorMessage: '缺少必填字段（日期、里程、加油量、实付金额）',
        });
        continue;
      }

      const dateMatch = dateStr.match(/^\d{4}[-/]\d{1,2}[-/]\d{1,2}$/);
      if (!dateMatch) {
        errors.push({
          rowNumber: rowNum,
          reoilDate: dateStr,
          status: 'error',
          errorMessage: `日期格式无效：${dateStr}`,
        });
        continue;
      }

      const normalizedDate = dateStr.replace(/\//g, '-');
      const mileage = Number(mileageStr);
      const oilAmount = Number(oilStr);
      const actualAmount = Number(actualStr);
      const payableAmount = payableStr ? Number(payableStr) : actualAmount;
      const oilGrade: OilGrade | null = gradeStr && ['92', '95', '98', '0'].includes(gradeStr)
        ? (gradeStr as OilGrade)
        : null;
      const oilPrice = priceStr ? Number(priceStr) : null;

      if (!Number.isFinite(mileage) || mileage <= 0) {
        errors.push({
          rowNumber: rowNum,
          reoilDate: normalizedDate,
          status: 'error',
          errorMessage: '里程必须是正数',
        });
        continue;
      }
      if (!Number.isFinite(oilAmount) || oilAmount <= 0) {
        errors.push({
          rowNumber: rowNum,
          reoilDate: normalizedDate,
          status: 'error',
          errorMessage: '加油量必须是正数',
        });
        continue;
      }
      if (!Number.isFinite(actualAmount) || actualAmount < 0) {
        errors.push({
          rowNumber: rowNum,
          reoilDate: normalizedDate,
          status: 'error',
          errorMessage: '实付金额不能为负',
        });
        continue;
      }

      parsedRows.push({
        rowNumber: rowNum,
        reoilDate: normalizedDate,
        mileage,
        oilAmount,
        actualAmount,
        payableAmount: Number.isFinite(payableAmount) && payableAmount > 0 ? payableAmount : actualAmount,
        oilGrade,
        oilPrice: Number.isFinite(oilPrice) && oilPrice! > 0 ? oilPrice! : null,
        province: provinceStr || null,
        source: sourceStr || null,
      });
    }

    const allDates = [...new Set(parsedRows.map((r) => r.reoilDate))];
    const existingMap = new Map<string, number[]>();
    const allExisting: Array<{ date: string; mileage: number }> = [];

    if (allDates.length > 0) {
      const existingRows = await this.db
        .select({
          reoilDate: koilRecord.reoilDate,
          mileage: koilRecord.mileage,
        })
        .from(koilRecord)
        .orderBy(koilRecord.reoilDate, koilRecord.id);

      for (const row of existingRows) {
        const key = String(row.reoilDate);
        const list = existingMap.get(key) || [];
        list.push(Number(row.mileage));
        existingMap.set(key, list);
        allExisting.push({ date: key, mileage: Number(row.mileage) });
      }
    }

    interface ChainPoint {
      date: string;
      mileage: number;
      rowNumber?: number;
      isCsv: boolean;
    }

    const csvPoints: ChainPoint[] = parsedRows.map((r) => ({
      date: r.reoilDate,
      mileage: r.mileage,
      rowNumber: r.rowNumber,
      isCsv: true,
    }));

    const allPoints: ChainPoint[] = [...csvPoints, ...allExisting.map((e) => ({ ...e, isCsv: false as const }))]
      .sort((a, b) => {
        if (a.date < b.date) return -1;
        if (a.date > b.date) return 1;
        return a.mileage - b.mileage;
      });

    const conflictRows = new Set<number>();
    const conflictMessages = new Map<number, string>();

    for (let i = 0; i < allPoints.length; i++) {
      const curr = allPoints[i];
      if (!curr.isCsv) continue;
      let prev: ChainPoint | null = null;
      let next: ChainPoint | null = null;
      for (let j = i - 1; j >= 0; j--) {
        if (allPoints[j].date < curr.date) {
          prev = allPoints[j];
          break;
        }
      }
      for (let j = i + 1; j < allPoints.length; j++) {
        if (allPoints[j].date > curr.date) {
          next = allPoints[j];
          break;
        }
      }

      const msgs: string[] = [];
      if (prev && curr.mileage <= prev.mileage) {
        msgs.push(`与${prev.date}记录(${prev.mileage}km)冲突：里程需大于${prev.mileage}km`);
      }
      if (next && curr.mileage >= next.mileage) {
        msgs.push(`与${next.date}记录(${next.mileage}km)冲突：里程需小于${next.mileage}km`);
      }
      if (msgs.length > 0 && curr.rowNumber !== undefined) {
        conflictRows.add(curr.rowNumber);
        conflictMessages.set(curr.rowNumber, msgs.join('；'));
      }
    }

    let totalValid = 0;
    for (const row of parsedRows) {
      const existingMileages = existingMap.get(row.reoilDate) || [];
      const isDuplicate = existingMileages.some((m) => m === row.mileage);
      const hasMileageConflict = conflictRows.has(row.rowNumber);

      let status: ImportPreviewItem['status'] = 'valid';
      let conflictMessage: string | undefined;
      if (isDuplicate) {
        status = 'duplicate';
      } else if (hasMileageConflict) {
        status = 'mileage_conflict';
        conflictMessage = conflictMessages.get(row.rowNumber);
      }

      const item: ImportPreviewItem = {
        rowNumber: row.rowNumber,
        reoilDate: row.reoilDate,
        mileage: row.mileage,
        oilAmount: row.oilAmount,
        actualAmount: row.actualAmount,
        payableAmount: row.payableAmount,
        oilGrade: row.oilGrade,
        oilPrice: row.oilPrice,
        province: row.province,
        source: row.source,
        status,
        conflictMessage,
      };

      if (status === 'duplicate') {
        duplicates.push(item);
      } else if (status === 'mileage_conflict') {
        mileageConflicts.push(item);
      } else {
        valid.push(item);
        totalValid += 1;
      }
    }

    return {
      valid,
      duplicates,
      mileageConflicts,
      errors,
      totalRows: totalValid,
    };
  }

  async confirmImport(csvContent: string): Promise<ImportConfirmResponse> {
    const preview = await this.previewImport(csvContent);
    const errorDetails = preview.errors.map((e) => ({
      rowNumber: e.rowNumber,
      message: e.errorMessage || '数据错误',
    }));
    const skippedDetails: { rowNumber: number; date?: string; mileage?: number; reason: string }[] = [
      ...preview.duplicates.map((d) => ({
        rowNumber: d.rowNumber,
        date: d.reoilDate,
        mileage: d.mileage,
        reason: '与库内记录重复（同日期同里程）',
      })),
      ...preview.mileageConflicts.map((d) => ({
        rowNumber: d.rowNumber,
        date: d.reoilDate,
        mileage: d.mileage,
        reason: d.conflictMessage || '里程链冲突',
      })),
    ];

    const validRows = preview.valid.filter(
      (item) => item.reoilDate && item.mileage !== undefined && item.oilAmount !== undefined && item.actualAmount !== undefined,
    );

    if (validRows.length === 0) {
      return {
        imported: 0,
        skipped: preview.duplicates.length + preview.mileageConflicts.length + preview.errors.length,
        errors: preview.errors.length,
        mileageConflicts: preview.mileageConflicts.length,
        duplicates: preview.duplicates.length,
        errorDetails,
        skippedDetails,
      };
    }

    const values = validRows.map((row) => {
      const data: Record<string, string> = {
        reoilDate: row.reoilDate!,
        mileage: String(row.mileage),
        oilAmount: String(row.oilAmount),
        payableAmount: String(row.payableAmount ?? row.actualAmount),
        actualAmount: String(row.actualAmount),
      };
      if (row.oilPrice != null) data.oilPrice = String(row.oilPrice);
      if (row.oilGrade) data.oilGrade = row.oilGrade;
      if (row.province) data.province = row.province;
      if (row.source) data.source = row.source;
      return data;
    });

    const result = await this.db
      .insert(koilRecord)
      .values(values as any)
      .onConflictDoNothing()
      .returning({ id: koilRecord.id });

    this.invalidateCaches();

    const imported = result.length;
    const skipped = validRows.length - imported + preview.duplicates.length + preview.mileageConflicts.length + preview.errors.length;

    return {
      imported,
      skipped,
      errors: preview.errors.length,
      mileageConflicts: preview.mileageConflicts.length,
      duplicates: preview.duplicates.length,
      errorDetails,
      skippedDetails,
    };
  }

  private parseCsvLine(line: string): string[] {
    const result: string[] = [];
    let current = '';
    let inQuotes = false;

    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (inQuotes) {
        if (ch === '"') {
          if (line[i + 1] === '"') {
            current += '"';
            i += 1;
          } else {
            inQuotes = false;
          }
        } else {
          current += ch;
        }
      } else {
        if (ch === '"') {
          inQuotes = true;
        } else if (ch === ',') {
          result.push(current);
          current = '';
        } else {
          current += ch;
        }
      }
    }
    result.push(current);
    return result;
  }

  private findColumnIndex(headers: string[], candidates: string[]): number {
    for (let i = 0; i < headers.length; i++) {
      const h = headers[i]?.trim().toLowerCase() || '';
      if (!h) continue;
      if (candidates.some((c) => {
        const candidate = c.toLowerCase();
        return h === candidate || h.startsWith(candidate);
      })) return i;
    }
    return -1;
  }
}

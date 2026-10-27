import { Inject, Injectable, Logger, OnModuleInit, NotFoundException, BadRequestException } from '@nestjs/common';
import { eq, and, desc, count, sql } from 'drizzle-orm';
import { DRIZZLE_DATABASE, type PostgresJsDatabase } from '@lark-apaas/fullstack-nestjs-core';
import { koilPriceHistory, koilPricePending, appConfig } from '@server/database/schema';
import type {
  KoilPricePendingItem,
  OilPriceDbStatusResponse,
  OilPriceDbUpdateResponse,
  OilPriceInfo,
  OilPriceResponse,
  OilGrade,
  KoilPricePendingListResponse,
} from '@shared/api.interface';
import { OilPriceService } from './oil-price.service';
import { GRADE_LABELS } from './oil-price.service';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

interface SeedRecord {
  date: string;
  province: string;
  grade: string;
  price: number;
  source_url?: string;
  verified_by?: string;
}

const CONFIG_KEY_AUTO = 'koil_price_auto_update';
const CONFIG_KEY_LAST_CHECK = 'koil_price_last_check_at';
const CONFIG_KEY_LAST_UPDATE = 'koil_price_last_update_date';
const CONFIG_KEY_INIT_AT = 'koil_price_initialized_at';
const CONFIG_KEY_LAST_MSG = 'koil_price_last_update_msg';
const CONFIG_KEY_LAST_WINDOW = 'koil_price_last_window_checked';
const CONFIG_KEY_DEVIATION_THRESHOLD = 'koil_price_deviation_threshold';
const CONFIG_KEY_NEXT_ADJUSTMENT = 'koil_price_next_adjustment';
const CONFIG_KEY_PRICE_FORECAST = 'koil_price_forecast';
const CHECK_INTERVAL_MS = 8 * 60 * 60 * 1000;
const BATCH_SIZE = 500;
const DEFAULT_DEVIATION_THRESHOLD = 3;
const TODAY_PRICE_CACHE_TTL_MS = 5 * 60 * 1000;

@Injectable()
export class OilPriceDbService implements OnModuleInit {
  private readonly logger = new Logger(OilPriceDbService.name);
  private seedData: SeedRecord[] | null = null;
  private checking = false;
  private readonly todayPriceCache = new Map<string, { data: OilPriceResponse; expiresAt: number }>();

  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: PostgresJsDatabase,
    private readonly oilPriceService: OilPriceService,
  ) {}

  onModuleInit(): void {
    void this.lazyInitIfEmpty().catch((err) => {
      this.logger.warn(`启动时初始化油价库失败: ${err instanceof Error ? err.message : String(err)}`);
    });
  }

  private async getConfig(key: string): Promise<string | null> {
    const rows = await this.db
      .select({ value: appConfig.configValue })
      .from(appConfig)
      .where(eq(appConfig.configKey, key))
      .limit(1);
    return rows[0]?.value ?? null;
  }

  private async setConfig(key: string, value: string): Promise<void> {
    await this.db
      .insert(appConfig)
      .values({ configKey: key, configValue: value })
      .onConflictDoUpdate({
        target: [appConfig.configKey],
        set: { configValue: value },
      });
  }

  async getAutoUpdateEnabled(): Promise<boolean> {
    const val = await this.getConfig(CONFIG_KEY_AUTO);
    return val === null ? true : val === '1';
  }

  async setAutoUpdateEnabled(enabled: boolean): Promise<void> {
    await this.setConfig(CONFIG_KEY_AUTO, enabled ? '1' : '0');
  }

  private async getLastCheckAt(): Promise<number> {
    const val = await this.getConfig(CONFIG_KEY_LAST_CHECK);
    if (!val) return 0;
    const n = Number(val);
    return Number.isFinite(n) ? n : 0;
  }

  private async setLastCheckAt(ts: number): Promise<void> {
    await this.setConfig(CONFIG_KEY_LAST_CHECK, String(ts));
  }

  private async getLastUpdatedAtStr(): Promise<string | null> {
    return this.getConfig(CONFIG_KEY_LAST_UPDATE);
  }

  private async setLastUpdatedAtStr(date: string): Promise<void> {
    await this.setConfig(CONFIG_KEY_LAST_UPDATE, date);
  }

  private async getInitializedAtStr(): Promise<string | null> {
    return this.getConfig(CONFIG_KEY_INIT_AT);
  }

  private async setInitializedAtStr(ts: string): Promise<void> {
    await this.setConfig(CONFIG_KEY_INIT_AT, ts);
  }

  private async getLastUpdateMsg(): Promise<string | null> {
    return this.getConfig(CONFIG_KEY_LAST_MSG);
  }

  private async setLastUpdateMsg(msg: string): Promise<void> {
    await this.setConfig(CONFIG_KEY_LAST_MSG, msg);
  }

  private async getLastWindowChecked(): Promise<string | null> {
    return this.getConfig(CONFIG_KEY_LAST_WINDOW);
  }

  private async setLastWindowChecked(date: string): Promise<void> {
    await this.setConfig(CONFIG_KEY_LAST_WINDOW, date);
  }

  private async getNextAdjustment(): Promise<string | null> {
    return this.getConfig(CONFIG_KEY_NEXT_ADJUSTMENT);
  }

  private async setNextAdjustment(date: string | null): Promise<void> {
    if (date) {
      await this.setConfig(CONFIG_KEY_NEXT_ADJUSTMENT, date);
    }
  }

  private async getPriceForecast(): Promise<string | null> {
    return this.getConfig(CONFIG_KEY_PRICE_FORECAST);
  }

  private async setPriceForecast(forecast: string | null): Promise<void> {
    if (forecast) {
      await this.setConfig(CONFIG_KEY_PRICE_FORECAST, forecast);
    }
  }

  async getTodayOilPrice(province: string): Promise<OilPriceResponse> {
    const cacheKey = province;
    const cached = this.todayPriceCache.get(cacheKey);
    if (cached && Date.now() < cached.expiresAt) {
      return cached.data;
    }

    const provinceName = this.oilPriceService.getProvinceName(province) || province;
    await this.lazyInitIfEmpty();

    const gradeLabels = Object.values(GRADE_LABELS);
    const results = await Promise.all(
      gradeLabels.map(async (gradeLabel: string) => {
        const rows = await this.db
          .select({ date: koilPriceHistory.date, price: koilPriceHistory.price })
          .from(koilPriceHistory)
          .where(
            and(
              eq(koilPriceHistory.province, provinceName),
              eq(koilPriceHistory.grade, gradeLabel),
            ),
          )
          .orderBy(desc(koilPriceHistory.date))
          .limit(2);
        return { gradeLabel, latest: rows[0] ?? null, previous: rows[1] ?? null };
      }),
    );

    const data: OilPriceInfo[] = [];
    let latestDate = '';
    for (const { gradeLabel, latest, previous } of results) {
      if (!latest) continue;
      const dateStr = latest.date.toString();
      if (dateStr > latestDate) latestDate = dateStr;
      const gradeKey = Object.entries(GRADE_LABELS).find(
        ([, label]) => label === gradeLabel,
      )?.[0] as OilGrade | undefined;
      if (!gradeKey) continue;
      const currentPrice = Number(latest.price);
      const previousPrice = previous ? Number(previous.price) : undefined;
      const priceChange = previousPrice !== undefined
        ? Number((currentPrice - previousPrice).toFixed(4))
        : undefined;
      const priceChangePercent = previousPrice !== undefined && previousPrice > 0
        ? Number(((priceChange! / previousPrice) * 100).toFixed(2))
        : undefined;
      const previousDate = previous ? previous.date.toString() : undefined;
      data.push({
        province: provinceName,
        grade: gradeKey,
        gradeLabel,
        price: currentPrice,
        unit: '元/升',
        updateDate: dateStr,
        source: '油价历史数据库',
        previousPrice,
        priceChange,
        priceChangePercent,
        previousDate,
      });
    }

    const nextAdjustment = await this.getNextAdjustment();
    const forecast = await this.getPriceForecast();

    if (data.length === 0) {
      const result: OilPriceResponse = {
        success: false,
        error: '暂无油价数据，请先更新油价库',
      };
      this.todayPriceCache.set(cacheKey, {
        data: result,
        expiresAt: Date.now() + 60 * 1000,
      });
      return result;
    }

    const result: OilPriceResponse = {
      success: true,
      province: provinceName,
      updateDate: latestDate,
      nextAdjustment: nextAdjustment ?? undefined,
      forecast: forecast ?? undefined,
      data,
    };
    this.todayPriceCache.set(cacheKey, {
      data: result,
      expiresAt: Date.now() + TODAY_PRICE_CACHE_TTL_MS,
    });
    return result;
  }

  private loadSeedData(): SeedRecord[] {
    if (this.seedData) return this.seedData;
    try {
      const seedPath = join(__dirname, 'assets', 'koil-price-seed.json');
      const raw = readFileSync(seedPath, 'utf-8');
      this.seedData = JSON.parse(raw) as SeedRecord[];
      this.logger.log(`油价种子数据加载完成: ${this.seedData.length} 条`);
      return this.seedData;
    } catch (err) {
      this.logger.warn(
        `加载油价种子数据失败: ${err instanceof Error ? err.message : String(err)}`,
      );
      return [];
    }
  }

  private async countRecords(): Promise<number> {
    const rows = await this.db
      .select({ count: count() })
      .from(koilPriceHistory);
    return Number(rows[0]?.count ?? 0);
  }

  async countPending(status = 'pending'): Promise<number> {
    const rows = await this.db
      .select({ count: count() })
      .from(koilPricePending)
      .where(eq(koilPricePending.status, status));
    return Number(rows[0]?.count ?? 0);
  }

  async exportOilPriceHistoryCsv(): Promise<{
    count: number;
    csv: string;
    filename: string;
  }> {
    const rows = await this.db
      .select({
        province: koilPriceHistory.province,
        grade: koilPriceHistory.grade,
        date: koilPriceHistory.date,
        price: koilPriceHistory.price,
        sourceUrl: koilPriceHistory.sourceUrl,
        verifiedBy: koilPriceHistory.verifiedBy,
      })
      .from(koilPriceHistory)
      .orderBy(koilPriceHistory.province, koilPriceHistory.grade, koilPriceHistory.date);

    const header = ['省份', '油号', '日期', '油价(元/升)', '来源链接', '验证来源'];
    const lines = [header.join(',')];
    for (const row of rows) {
      lines.push(
        [
          row.province,
          row.grade ?? '',
          row.date,
          row.price?.toString() ?? '',
          `"${(row.sourceUrl ?? '').replace(/"/g, '""')}"`,
          `"${(row.verifiedBy ?? '').replace(/"/g, '""')}"`,
        ].join(','),
      );
    }
    const today = new Date().toISOString().slice(0, 10);
    const filename = `KOiL_油价历史记录_${today}.csv`;
    return { count: rows.length, csv: lines.join('\n'), filename };
  }

  async getStatus(): Promise<OilPriceDbStatusResponse> {
    const total = await this.countRecords();
    const pendingCount = await this.countPending();
    const initialized = total > 0;

    let latestDate: string | null = null;
    let provinceCount = 0;
    let gradeCount = 0;
    let adjustmentCount = 0;

    if (initialized) {
      const latestRow = await this.db
        .select({ date: koilPriceHistory.date })
        .from(koilPriceHistory)
        .orderBy(desc(koilPriceHistory.date))
        .limit(1);
      latestDate = latestRow[0]?.date?.toString() ?? null;

      const provRow = await this.db
        .select({ count: sql<number>`count(distinct ${koilPriceHistory.province})` })
        .from(koilPriceHistory);
      provinceCount = Number(provRow[0]?.count ?? 0);

      const gradeRow = await this.db
        .select({ count: sql<number>`count(distinct ${koilPriceHistory.grade})` })
        .from(koilPriceHistory);
      gradeCount = Number(gradeRow[0]?.count ?? 0);

      const adjRow = await this.db
        .select({ count: sql<number>`count(distinct ${koilPriceHistory.date})` })
        .from(koilPriceHistory);
      adjustmentCount = Number(adjRow[0]?.count ?? 0);
    }

    const lastCheckTs = await this.getLastCheckAt();
    const autoEnabled = await this.getAutoUpdateEnabled();
    const initializedAt = await this.getInitializedAtStr();
    const lastUpdatedAt = await this.getLastUpdatedAtStr();
    const lastMsg = await this.getLastUpdateMsg();
    const lastWindowChecked = await this.getLastWindowChecked();

    const lastPriceChangeDate = latestDate;
    let versionDate: string | null = null;
    let isStranded = false;
    if (latestDate && lastWindowChecked) {
      if (lastWindowChecked > latestDate) {
        versionDate = lastWindowChecked;
        isStranded = true;
      } else {
        versionDate = latestDate;
      }
    } else if (latestDate) {
      versionDate = latestDate;
    } else if (lastWindowChecked) {
      versionDate = lastWindowChecked;
    }

    return {
      initialized,
      latestDate,
      lastPriceChangeDate,
      lastWindowChecked,
      versionDate,
      provinceCount,
      gradeCount,
      adjustmentCount,
      totalRecords: total,
      lastCheckedAt: lastCheckTs > 0
        ? new Date(lastCheckTs).toISOString()
        : null,
      lastUpdatedAt: lastUpdatedAt
        ? new Date(lastUpdatedAt).toISOString()
        : null,
      initializedAt: initializedAt
        ? new Date(initializedAt).toISOString()
        : null,
      autoUpdateEnabled: autoEnabled,
      checkIntervalHours: CHECK_INTERVAL_MS / (60 * 60 * 1000),
      lastUpdateMessage: lastMsg,
      isStranded,
      pendingCount,
    };
  }

  async lazyInitIfEmpty(): Promise<boolean> {
    const total = await this.countRecords();
    if (total > 0) {
      if (total >= 2000) {
        const initAt = await this.getInitializedAtStr();
        if (!initAt) {
          await this.setInitializedAtStr(new Date().toISOString());
        }
        const latestRow = await this.db
          .select({ date: koilPriceHistory.date })
          .from(koilPriceHistory)
          .orderBy(desc(koilPriceHistory.date))
          .limit(1);
        const latestDate = latestRow[0]?.date?.toString() ?? null;
        if (latestDate) {
          await this.setLastUpdatedAtStr(latestDate);
        }
      }
      return false;
    }

    const seeds = this.loadSeedData();
    if (seeds.length === 0) {
      this.logger.warn('种子数据为空，跳过初始化');
      return false;
    }

    this.logger.log(`开始灌入油价种子数据: ${seeds.length} 条`);
    let inserted = 0;

    for (let i = 0; i < seeds.length; i += BATCH_SIZE) {
      const batch = seeds.slice(i, i + BATCH_SIZE);
      const values = batch.map((item) => ({
        date: item.date,
        province: item.province,
        grade: item.grade,
        price: String(item.price),
        sourceUrl: item.source_url ?? null,
        verifiedBy: item.verified_by ?? null,
      }));

      await this.db
        .insert(koilPriceHistory)
        .values(values)
        .onConflictDoNothing({
          target: [koilPriceHistory.province, koilPriceHistory.grade, koilPriceHistory.date],
        });
      inserted += batch.length;
    }

    const latestRow = await this.db
      .select({ date: koilPriceHistory.date })
      .from(koilPriceHistory)
      .orderBy(desc(koilPriceHistory.date))
      .limit(1);
    const latestDate = latestRow[0]?.date?.toString() ?? null;

    const now = new Date();
    await this.setLastUpdatedAtStr(latestDate || now.toISOString().slice(0, 10));
    await this.setInitializedAtStr(now.toISOString());
    await this.setLastCheckAt(now.getTime());
    await this.setLastUpdateMsg(`初始化完成，共 ${inserted} 条记录`);

    this.logger.log(`油价种子数据灌入完成: ${inserted} 条, 最新调价日: ${latestDate}`);
    return true;
  }

  async checkAndUpdate(force = false): Promise<OilPriceDbUpdateResponse> {
    if (this.checking) {
      const pendingCount = await this.countPending();
      return {
        success: false,
        newAdjustments: 0,
        newRecords: 0,
        pendingCount,
        latestDate: (await this.getLastUpdatedAtStr()) || null,
        message: '正在更新中，请稍后再试',
      };
    }

    const autoEnabled = await this.getAutoUpdateEnabled();
    if (!force && !autoEnabled) {
      const pendingCount = await this.countPending();
      return {
        success: true,
        newAdjustments: 0,
        newRecords: 0,
        pendingCount,
        latestDate: (await this.getLastUpdatedAtStr()) || null,
        message: '自动更新已关闭，跳过检测',
      };
    }

    const now = Date.now();
    if (!force) {
      const lastCheck = await this.getLastCheckAt();
      if (now - lastCheck < CHECK_INTERVAL_MS) {
        const pendingCount = await this.countPending();
        return {
          success: true,
          newAdjustments: 0,
          newRecords: 0,
          pendingCount,
          latestDate: (await this.getLastUpdatedAtStr()) || null,
          message: '距上次检查不足 8 小时，跳过',
        };
      }
    }

    this.checking = true;
    try {
      await this.lazyInitIfEmpty();

      const result = await this.performPriceCheck();
      const pendingCount = await this.countPending();
      const response: OilPriceDbUpdateResponse = {
        success: result.success,
        newAdjustments: result.newAdjustments,
        newRecords: result.newRecords,
        latestDate: result.latestDate,
        message: result.message,
        pendingCount,
        error: (result as { error?: string }).error,
      };
      const fore = (result as { nextAdjustment?: string; forecast?: string });
      if (fore.nextAdjustment) await this.setNextAdjustment(fore.nextAdjustment);
      if (fore.forecast) await this.setPriceForecast(fore.forecast);
      await this.setLastCheckAt(now);
      await this.setLastUpdateMsg(response.message);
      if (result.newAdjustments > 0 && result.latestDate) {
        await this.setLastUpdatedAtStr(result.latestDate);
        await this.setLastWindowChecked(result.latestDate);
      } else if ((result as { strandedWindowDate?: string }).strandedWindowDate) {
        await this.setLastWindowChecked((result as { strandedWindowDate?: string }).strandedWindowDate as string);
      }
      return response;
    } catch (err) {
      await this.setLastCheckAt(now);
      const message = err instanceof Error ? err.message : String(err);
      this.logger.error(`油价检测更新失败: ${message}`);
      await this.setLastUpdateMsg(`更新失败：${message}`);
      return {
        success: false,
        newAdjustments: 0,
        newRecords: 0,
        pendingCount: await this.countPending(),
        latestDate: (await this.getLastUpdatedAtStr()) || null,
        message: '油价检测失败',
        error: message,
      };
    } finally {
      this.checking = false;
      this.todayPriceCache.clear();
    }
  }

  private buildGradeMap(data: OilPriceInfo[]): Map<string, OilPriceInfo> {
    const map = new Map<string, OilPriceInfo>();
    for (const item of data) {
      map.set(item.grade, item);
    }
    return map;
  }

  private async performPriceCheck(): Promise<OilPriceDbUpdateResponse & { strandedWindowDate?: string; nextAdjustment?: string; forecast?: string }> {
    const provinceNames = this.oilPriceService.getProvinces().map((p) => p.name);
    if (provinceNames.length === 0) {
      return {
        success: false,
        newAdjustments: 0,
        newRecords: 0,
        pendingCount: 0,
        latestDate: (await this.getLastUpdatedAtStr()) || null,
        message: '省份列表为空',
      };
    }

    const sampleProvince = provinceNames[0];
    const { primary: samplePrimary, secondary: sampleSecondary } =
      await this.oilPriceService.fetchBothSources(sampleProvince);

    const bothFailed = !samplePrimary.success && !sampleSecondary.success;
    if (bothFailed) {
      return {
        success: false,
        newAdjustments: 0,
        newRecords: 0,
        pendingCount: 0,
        latestDate: (await this.getLastUpdatedAtStr()) || null,
        message: samplePrimary.error || sampleSecondary.error || '实时油价获取失败',
      };
    }

    const sampleLiveSource = samplePrimary.success && samplePrimary.data && samplePrimary.data.length > 0
      ? samplePrimary
      : sampleSecondary;
    if (!sampleLiveSource.success || !sampleLiveSource.data || sampleLiveSource.data.length === 0) {
      return {
        success: false,
        newAdjustments: 0,
        newRecords: 0,
        pendingCount: 0,
        latestDate: (await this.getLastUpdatedAtStr()) || null,
        message: '实时油价获取失败',
      };
    }

    const sampleGrade = sampleLiveSource.data[0].grade;
    const samplePrice = sampleLiveSource.data[0].price;
    const sampleGradeLabel = GRADE_LABELS[sampleGrade] || sampleGrade;
    const nextAdjustment = samplePrimary.nextAdjustment || sampleSecondary.nextAdjustment;
    const forecast = samplePrimary.forecast || sampleSecondary.forecast;

    const latestRow = await this.db
      .select({ date: koilPriceHistory.date, price: koilPriceHistory.price })
      .from(koilPriceHistory)
      .where(
        and(
          eq(koilPriceHistory.province, sampleProvince),
          eq(koilPriceHistory.grade, sampleGradeLabel),
        ),
      )
      .orderBy(desc(koilPriceHistory.date))
      .limit(1);

    const latestPrice = latestRow[0] ? Number(latestRow[0].price) : null;
    const latestDate = latestRow[0]?.date?.toString() ?? null;

    const today = new Date().toISOString().slice(0, 10);

    if (latestPrice !== null && Math.abs(latestPrice - samplePrice) < 0.005) {
      return {
        success: true,
        newAdjustments: 0,
        newRecords: 0,
        pendingCount: 0,
        latestDate,
        strandedWindowDate: today,
        nextAdjustment,
        forecast,
        message: `已核对至 ${today}，油价无变化（搁浅）`,
      };
    }

    if (latestDate === today) {
      return {
        success: true,
        newAdjustments: 0,
        newRecords: 0,
        pendingCount: 0,
        latestDate: today,
        nextAdjustment,
        forecast,
        message: '今日已有调价记录，跳过',
      };
    }

    this.logger.log(
      `检测到油价变化: ${sampleProvince} ${sampleGradeLabel} ${latestPrice ?? 'N/A'} → ${samplePrice}，开始追加新调价记录（双源核验）`,
    );

    let verifiedRecords = 0;
    let pendingRecords = 0;
    const failedProvinces: string[] = [];

    for (const province of provinceNames) {
      try {
        const { primary, secondary } = await this.oilPriceService.fetchBothSources(province);

        const primaryData = primary.success && primary.data ? primary.data : [];
        const secondaryData = secondary.success && secondary.data ? secondary.data : [];
        const primaryMap = this.buildGradeMap(primaryData);
        const secondaryMap = this.buildGradeMap(secondaryData);

        const allGrades = new Set<string>([...primaryMap.keys(), ...secondaryMap.keys()]);
        if (allGrades.size === 0) {
          failedProvinces.push(province);
          continue;
        }

        const matchedValues: Array<{
          date: string;
          province: string;
          grade: string;
          price: string;
          sourceUrl: string | null;
          verifiedBy: string;
        }> = [];
        const pendingValues: Array<{
          province: string;
          grade: string;
          effectiveDate: string;
          primaryPrice: string | null;
          primarySource: string | null;
          secondaryPrice: string | null;
          secondarySource: string | null;
          status: string;
        }> = [];

        for (const grade of allGrades) {
          const pItem = primaryMap.get(grade);
          const sItem = secondaryMap.get(grade);
          const gradeLabel = GRADE_LABELS[grade as keyof typeof GRADE_LABELS] || grade;

          const pPrice = pItem?.price ?? null;
          const sPrice = sItem?.price ?? null;
          const pSource = pItem?.source || 'youjia.abapi.cn';
          const sSource = sItem?.source || 'v1.apizero.cn';

          const bothPresent = pPrice !== null && sPrice !== null;
          const matches = bothPresent && Math.abs(pPrice - sPrice) < 0.005;

          if (matches) {
            matchedValues.push({
              date: today,
              province,
              grade: gradeLabel,
              price: String(pPrice),
              sourceUrl: pSource,
              verifiedBy: '官方已核验',
            });
          } else {
            pendingValues.push({
              province,
              grade: gradeLabel,
              effectiveDate: today,
              primaryPrice: pPrice !== null ? String(pPrice) : null,
              primarySource: pPrice !== null ? pSource : null,
              secondaryPrice: sPrice !== null ? String(sPrice) : null,
              secondarySource: sPrice !== null ? sSource : null,
              status: 'pending',
            });
          }
        }

        if (matchedValues.length > 0) {
          await this.db
            .insert(koilPriceHistory)
            .values(matchedValues)
            .onConflictDoNothing({
              target: [koilPriceHistory.province, koilPriceHistory.grade, koilPriceHistory.date],
            });
          verifiedRecords += matchedValues.length;
        }

        if (pendingValues.length > 0) {
          for (const pv of pendingValues) {
            await this.db
              .insert(koilPricePending)
              .values(pv)
              .onConflictDoUpdate({
                target: [koilPricePending.province, koilPricePending.grade, koilPricePending.effectiveDate],
                set: {
                  primaryPrice: pv.primaryPrice,
                  primarySource: pv.primarySource,
                  secondaryPrice: pv.secondaryPrice,
                  secondarySource: pv.secondarySource,
                  status: 'pending',
                },
                where: eq(koilPricePending.status, 'pending'),
              });
          }
          pendingRecords += pendingValues.length;
        }
      } catch (err) {
        failedProvinces.push(province);
        this.logger.warn(
          `处理 ${province} 油价失败: ${err instanceof Error ? err.message : String(err)}`,
        );
      }
    }

    this.logger.log(
      `调价追加完成: 核验通过 ${verifiedRecords} 条, 待确认 ${pendingRecords} 条 (${today}), 失败省份: ${failedProvinces.length}`,
    );

    const hasNew = verifiedRecords > 0;
    const parts: string[] = [];
    if (verifiedRecords > 0) parts.push(`已入库 ${verifiedRecords} 条`);
    if (pendingRecords > 0) parts.push(`${pendingRecords} 条待确认`);
    if (failedProvinces.length > 0) parts.push(`${failedProvinces.length} 省失败`);
    const message = parts.length > 0 ? parts.join('、') : '无新记录追加';

    return {
      success: true,
      newAdjustments: hasNew ? 1 : 0,
      newRecords: verifiedRecords,
      pendingCount: pendingRecords,
      latestDate: today,
      nextAdjustment,
      forecast,
      message,
    };
  }

  async getPendingList(status?: string): Promise<KoilPricePendingListResponse> {
    const filterStatus = status || 'pending';
    const rows = await this.db
      .select()
      .from(koilPricePending)
      .where(eq(koilPricePending.status, filterStatus))
      .orderBy(desc(koilPricePending.effectiveDate));

    const items: KoilPricePendingItem[] = rows.map((row) => ({
      id: row.id,
      province: row.province,
      grade: row.grade,
      effectiveDate: row.effectiveDate.toString(),
      primaryPrice: row.primaryPrice !== null ? Number(row.primaryPrice) : null,
      primarySource: row.primarySource,
      secondaryPrice: row.secondaryPrice !== null ? Number(row.secondaryPrice) : null,
      secondarySource: row.secondarySource,
      status: row.status as KoilPricePendingItem['status'],
      resolvedPrice: row.resolvedPrice !== null ? Number(row.resolvedPrice) : null,
      resolvedSource: row.resolvedSource,
      note: row.note,
      createdAt: row.createdAt.toISOString(),
    }));

    const totalRows = await this.db
      .select({ count: count() })
      .from(koilPricePending)
      .where(eq(koilPricePending.status, filterStatus));

    return {
      items,
      total: Number(totalRows[0]?.count ?? 0),
    };
  }

  async acceptPending(
    id: string,
    useSource: 'primary' | 'secondary',
    customPrice?: number,
  ): Promise<{ success: boolean }> {
    const existing = await this.db
      .select()
      .from(koilPricePending)
      .where(eq(koilPricePending.id, id))
      .limit(1);
    if (existing.length === 0) {
      throw new NotFoundException('待确认记录不存在');
    }
    const row = existing[0];

    let resolvedPrice: number | null = null;
    let resolvedSource: string | null = null;

    if (customPrice !== undefined && customPrice > 0) {
      resolvedPrice = customPrice;
      resolvedSource = 'manual';
    } else if (useSource === 'primary') {
      resolvedPrice = row.primaryPrice !== null ? Number(row.primaryPrice) : null;
      resolvedSource = row.primarySource ?? null;
    } else {
      resolvedPrice = row.secondaryPrice !== null ? Number(row.secondaryPrice) : null;
      resolvedSource = row.secondarySource ?? null;
    }

    if (resolvedPrice === null || Number.isNaN(resolvedPrice)) {
      throw new NotFoundException('所选源价格为空，无法确认');
    }

    const effectiveDate = row.effectiveDate.toString();

    await this.db.transaction(async (tx) => {
      await tx
        .insert(koilPriceHistory)
        .values({
          date: effectiveDate,
          province: row.province,
          grade: row.grade,
          price: String(resolvedPrice),
          sourceUrl: resolvedSource,
          verifiedBy: '官方已核验',
        })
        .onConflictDoNothing({
          target: [koilPriceHistory.province, koilPriceHistory.grade, koilPriceHistory.date],
        });

      await tx
        .update(koilPricePending)
        .set({
          status: 'accepted',
          resolvedPrice: String(resolvedPrice),
          resolvedSource,
        })
        .where(eq(koilPricePending.id, id));
    });

    await this.setLastUpdatedAtStr(effectiveDate);
    const lastWindow = await this.getLastWindowChecked();
    if (!lastWindow || effectiveDate > lastWindow) {
      await this.setLastWindowChecked(effectiveDate);
    }

    return { success: true };
  }

  async ignorePending(id: string): Promise<{ success: boolean }> {
    const updated = await this.db
      .update(koilPricePending)
      .set({
        status: 'ignored',
        note: '用户忽略',
      })
      .where(eq(koilPricePending.id, id))
      .returning({ id: koilPricePending.id });
    if (updated.length === 0) {
      throw new NotFoundException('待确认记录不存在');
    }
    return { success: true };
  }

  async getPriceDeviationThreshold(): Promise<number> {
    const val = await this.getConfig(CONFIG_KEY_DEVIATION_THRESHOLD);
    if (val === null) return DEFAULT_DEVIATION_THRESHOLD;
    const num = Number(val);
    return Number.isFinite(num) && num > 0 && num <= 100 ? num : DEFAULT_DEVIATION_THRESHOLD;
  }

  async setPriceDeviationThreshold(thresholdPercent: number): Promise<void> {
    if (!Number.isFinite(thresholdPercent) || thresholdPercent <= 0 || thresholdPercent > 100) {
      throw new BadRequestException('偏差阈值需在 0-100 之间');
    }
    await this.setConfig(CONFIG_KEY_DEVIATION_THRESHOLD, String(thresholdPercent));
  }

  async checkPriceDeviation(
    province: string,
    grade: string,
    date: string,
    oilAmount: number,
    actualAmount: number,
  ): Promise<{
    hasDeviation: boolean;
    actualUnitPrice: number;
    referencePrice: number | null;
    referenceDate: string | null;
    referenceGrade: string | null;
    deviationPercent: number | null;
    thresholdPercent: number;
    pendingAfter: boolean;
    isLatestVerified: boolean;
    message: string | null;
  }> {
    const threshold = await this.getPriceDeviationThreshold();
    const actualUnitPrice = actualAmount / oilAmount;

    if (!province || !grade || !date || oilAmount <= 0 || actualAmount <= 0) {
      return {
        hasDeviation: false,
        actualUnitPrice: Number.isFinite(actualUnitPrice) ? actualUnitPrice : 0,
        referencePrice: null,
        referenceDate: null,
        referenceGrade: null,
        deviationPercent: null,
        thresholdPercent: threshold,
        pendingAfter: false,
        isLatestVerified: false,
        message: null,
      };
    }

    const historyPrice = await this.oilPriceService.getHistoryPrice(province, grade, date);

    if (!historyPrice) {
      return {
        hasDeviation: false,
        actualUnitPrice,
        referencePrice: null,
        referenceDate: null,
        referenceGrade: null,
        deviationPercent: null,
        thresholdPercent: threshold,
        pendingAfter: false,
        isLatestVerified: false,
        message: null,
      };
    }

    const referencePrice = historyPrice.price;
    if (referencePrice <= 0) {
      return {
        hasDeviation: false,
        actualUnitPrice,
        referencePrice,
        referenceDate: historyPrice.date,
        referenceGrade: historyPrice.grade,
        deviationPercent: null,
        thresholdPercent: threshold,
        pendingAfter: historyPrice.pendingAfter,
        isLatestVerified: historyPrice.isLatestVerified,
        message: null,
      };
    }

    const deviationPercent = Math.abs((actualUnitPrice - referencePrice) / referencePrice) * 100;
    const hasDeviation = deviationPercent > threshold;

    let message: string | null = null;
    if (hasDeviation) {
      const higher = actualUnitPrice > referencePrice ? '高' : '低';
      message =
        `实际单价 ${actualUnitPrice.toFixed(2)} 元 vs 库内确认价 ${referencePrice.toFixed(2)} 元` +
        `（${historyPrice.date}），偏差 ${deviationPercent.toFixed(1)}%（${higher}于参考价），` +
        '请确认是调价未更新或录入有误';
    }

    return {
      hasDeviation,
      actualUnitPrice,
      referencePrice,
      referenceDate: historyPrice.date,
      referenceGrade: historyPrice.grade,
      deviationPercent,
      thresholdPercent: threshold,
      pendingAfter: historyPrice.pendingAfter,
      isLatestVerified: historyPrice.isLatestVerified,
      message,
    };
  }
}

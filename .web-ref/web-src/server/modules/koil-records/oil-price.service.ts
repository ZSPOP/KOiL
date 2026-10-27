import { Inject, Injectable, Logger } from '@nestjs/common';
import axios from 'axios';
import { eq, and, lte, desc } from 'drizzle-orm';
import { DRIZZLE_DATABASE, type PostgresJsDatabase } from '@lark-apaas/fullstack-nestjs-core';
import { koilPriceHistory } from '@server/database/schema';
import type {
  OilGrade,
  HistoryOilPriceResponse,
  OilPriceInfo,
  OilPriceResponse,
  ReverseGeocodeResponse,
} from '@shared/api.interface';

const GRADE_LABELS: Record<OilGrade, string> = {
  '92': '92号汽油',
  '95': '95号汽油',
  '98': '98号汽油',
  '0': '0号柴油',
};

const PROVINCE_LIST: { code: string; name: string }[] = [
  { code: 'beijing', name: '北京' },
  { code: 'shanghai', name: '上海' },
  { code: 'tianjin', name: '天津' },
  { code: 'chongqing', name: '重庆' },
  { code: 'hebei', name: '河北' },
  { code: 'shanxi', name: '山西' },
  { code: 'liaoning', name: '辽宁' },
  { code: 'jilin', name: '吉林' },
  { code: 'heilongjiang', name: '黑龙江' },
  { code: 'jiangsu', name: '江苏' },
  { code: 'zhejiang', name: '浙江' },
  { code: 'anhui', name: '安徽' },
  { code: 'fujian', name: '福建' },
  { code: 'jiangxi', name: '江西' },
  { code: 'shandong', name: '山东' },
  { code: 'henan', name: '河南' },
  { code: 'hubei', name: '湖北' },
  { code: 'hunan', name: '湖南' },
  { code: 'guangdong', name: '广东' },
  { code: 'guangxi', name: '广西' },
  { code: 'hainan', name: '海南' },
  { code: 'sichuan', name: '四川' },
  { code: 'guizhou', name: '贵州' },
  { code: 'yunnan', name: '云南' },
  { code: 'shaanxi', name: '陕西' },
  { code: 'gansu', name: '甘肃' },
  { code: 'qinghai', name: '青海' },
  { code: 'ningxia', name: '宁夏' },
  { code: 'xinjiang', name: '新疆' },
  { code: 'neimenggu', name: '内蒙古' },
  { code: 'xizang', name: '西藏' },
];

const PROVINCE_NAME_TO_CODE: Record<string, string> = {
  北京: 'beijing',
  上海: 'shanghai',
  天津: 'tianjin',
  重庆: 'chongqing',
  河北: 'hebei',
  山西: 'shanxi',
  辽宁: 'liaoning',
  吉林: 'jilin',
  黑龙江: 'heilongjiang',
  江苏: 'jiangsu',
  浙江: 'zhejiang',
  安徽: 'anhui',
  福建: 'fujian',
  江西: 'jiangxi',
  山东: 'shandong',
  河南: 'henan',
  湖北: 'hubei',
  湖南: 'hunan',
  广东: 'guangdong',
  广西: 'guangxi',
  海南: 'hainan',
  四川: 'sichuan',
  贵州: 'guizhou',
  云南: 'yunnan',
  陕西: 'shaanxi',
  甘肃: 'gansu',
  青海: 'qinghai',
  宁夏: 'ningxia',
  新疆: 'xinjiang',
  内蒙古: 'neimenggu',
  西藏: 'xizang',
};

interface NominatimAddress {
  province?: string;
  state?: string;
  city?: string;
  region?: string;
  country?: string;
}

interface NominatimResponse {
  address?: NominatimAddress;
  display_name?: string;
}

interface ApiZeroPriceItem {
  type: string;
  price: number;
  name: string;
}

interface ApiZeroResponse {
  code: number;
  message?: string;
  msg?: string;
  data?: {
    province?: string;
    update_date?: string;
    next_adjustment?: string;
    forecast?: string;
    prices?: ApiZeroPriceItem[];
  };
}

interface YoujiaChartResponse {
  dates: string[];
  p92: number[];
  p95: number[];
  p98: number[];
  p0: number[];
}

const APIZERO_TYPE_MAP: Record<string, OilGrade> = {
  '92#': '92',
  '95#': '95',
  '98#': '98',
  '0#': '0',
};

const YOUJIA_GRADE_MAP: Record<string, OilGrade> = {
  p92: '92',
  p95: '95',
  p98: '98',
  p0: '0',
};

@Injectable()
export class OilPriceService {
  private readonly logger = new Logger(OilPriceService.name);

  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: PostgresJsDatabase,
  ) {}

  async getHistoryPrice(
    province: string,
    grade: string,
    date: string,
  ): Promise<HistoryOilPriceResponse | null> {
    const gradeLabel = GRADE_LABELS[grade as OilGrade] ?? grade;
    const rows = await this.db
      .select({
        price: koilPriceHistory.price,
        date: koilPriceHistory.date,
        province: koilPriceHistory.province,
        grade: koilPriceHistory.grade,
      })
      .from(koilPriceHistory)
      .where(
        and(
          eq(koilPriceHistory.province, province),
          eq(koilPriceHistory.grade, gradeLabel),
          lte(koilPriceHistory.date, date),
        ),
      )
      .orderBy(desc(koilPriceHistory.date))
      .limit(1);

    if (rows.length === 0) return null;
    const row = rows[0];

    const latestRows = await this.db
      .select({ date: koilPriceHistory.date })
      .from(koilPriceHistory)
      .where(
        and(
          eq(koilPriceHistory.province, province),
          eq(koilPriceHistory.grade, gradeLabel),
        ),
      )
      .orderBy(desc(koilPriceHistory.date))
      .limit(1);
    const latestVerifiedDate: string | null = latestRows[0]?.date?.toString() ?? null;
    const resultDate = row.date;
    const isLatestVerified = latestVerifiedDate !== null && resultDate === latestVerifiedDate;
    const pendingAfter = latestVerifiedDate !== null && date > latestVerifiedDate;

    return {
      price: Number(row.price),
      date: resultDate,
      province: row.province,
      grade: row.grade,
      isLatestVerified,
      latestVerifiedDate,
      pendingAfter,
    };
  }

  getProvinces(): { code: string; name: string }[] {
    return PROVINCE_LIST;
  }

  getProvinceName(codeOrName: string): string {
    const exact = PROVINCE_LIST.find(
      (p) => p.code === codeOrName || p.name === codeOrName,
    );
    return exact?.name ?? codeOrName;
  }

  async getProvinceByIp(ip: string): Promise<string> {
    if (!ip || ip === '::1' || ip === '127.0.0.1' || ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.16.') || ip.startsWith('172.17.') || ip.startsWith('172.18.') || ip.startsWith('172.19.') || ip.startsWith('172.2') || ip.startsWith('172.3') || ip.startsWith('::ffff:127.')) {
      return '';
    }
    try {
      const response = await axios.get(`https://ipapi.co/${ip}/json/`, { timeout: 4000 });
      const data = response.data as { region?: string; country_name?: string };
      if (data?.region && data?.country_name === 'China') {
        const region = data.region.replace(/省|市|自治区|特别行政区|维吾尔|壮族|回族/g, '').trim();
        const exact = PROVINCE_LIST.find(
          (p) => p.code === region || p.name === region,
        );
        if (exact) return exact.name;
        return region;
      }
    } catch (err) {
      this.logger.warn(`IP 定位失败 (${ip}): ${err instanceof Error ? err.message : String(err)}`);
    }
    return '';
  }

  async fetchBothSources(province: string): Promise<{ primary: OilPriceResponse; secondary: OilPriceResponse }> {
    const provinceName = this.getProvinceName(province);
    const fallbackError = (msg: string): OilPriceResponse => ({ success: false, error: msg });

    if (!provinceName) {
      const err = fallbackError('请选择省份以查看油价');
      return { primary: err, secondary: err };
    }

    const [primaryResult, secondaryResult] = await Promise.all([
      this.fetchFromYoujia(provinceName).catch((err) => {
        this.logger.warn(`Youjia 源失败 (${provinceName}): ${err instanceof Error ? err.message : String(err)}`);
        return {
          success: false,
          error: err instanceof Error ? err.message : String(err),
        };
      }),
      this.fetchFromApiZero(provinceName).catch((err) => {
        this.logger.warn(`ApiZero 源失败 (${provinceName}): ${err instanceof Error ? err.message : String(err)}`);
        return {
          success: false,
          error: err instanceof Error ? err.message : String(err),
        };
      }),
    ]);

    return { primary: primaryResult, secondary: secondaryResult };
  }

  async getOilPriceByProvince(province: string): Promise<OilPriceResponse> {
    const provinceName = this.getProvinceName(province);
    if (!provinceName) {
      return {
        success: false,
        error: '请选择省份以查看油价',
      };
    }

    try {
      const result = await this.fetchFromYoujia(provinceName);
      if (result.success && result.data && result.data.length > 0) {
        return result;
      }
    } catch (err) {
      this.logger.warn(
        `Youjia 油价接口失败 (${provinceName}): ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }

    try {
      const result = await this.fetchFromApiZero(provinceName);
      if (result.success && result.data && result.data.length > 0) {
        return result;
      }
    } catch (err) {
      this.logger.warn(
        `ApiZero 油价接口失败 (${provinceName}): ${
          err instanceof Error ? err.message : String(err)
        }`,
      );
    }

    return {
      success: false,
      error: '油价数据获取失败，请稍后重试或手动输入金额',
    };
  }

  async reverseGeocode(
    lat: number,
    lon: number,
  ): Promise<ReverseGeocodeResponse> {
    const services = [
      this.reverseWithNominatim.bind(this),
      this.reverseWithIsp.bind(this),
    ];

    for (const service of services) {
      try {
        const result = await service(lat, lon);
        if (result.success && result.province) {
          return result;
        }
      } catch (err) {
        this.logger.warn(
          `反向地理编码服务失败: ${
            err instanceof Error ? err.message : String(err)
          }`,
        );
      }
    }

    const fallback = this.estimateProvinceByCoords(lat, lon);
    if (fallback) {
      return {
        success: true,
        province: fallback,
        provinceCode: PROVINCE_NAME_TO_CODE[fallback] || '',
        source: '坐标估算（网络受限）',
      };
    }

    return {
      success: false,
      error: '定位解析失败，请手动选择省份',
    };
  }

  private async reverseWithNominatim(
    lat: number,
    lon: number,
  ): Promise<ReverseGeocodeResponse> {
    const response = await axios.get<NominatimResponse>(
      'https://nominatim.openstreetmap.org/reverse',
      {
        params: {
          lat,
          lon,
          format: 'json',
          zoom: 8,
          accept_language: 'zh-CN',
          addressdetails: 1,
        },
        timeout: 5000,
        headers: {
          'User-Agent': 'KOiL/1.0',
        },
      },
    );

    const address = response.data.address;
    if (!address) {
      return { success: false, error: '未能解析到地址信息' };
    }

    const provinceRaw =
      address.province || address.state || address.region || '';
    const province = this.normalizeProvinceName(provinceRaw);
    const provinceCode = PROVINCE_NAME_TO_CODE[province] || '';

    if (!provinceCode) {
      return { success: false, error: `无法识别的省份：${provinceRaw}` };
    }

    return {
      success: true,
      province,
      provinceCode,
      city: address.city || '',
      source: 'Nominatim 反向地理编码',
    };
  }

  private async reverseWithIsp(
    lat: number,
    lon: number,
  ): Promise<ReverseGeocodeResponse> {
    const response = await axios.get(
      `https://api.ispid.org/geo/rev?lat=${lat}&lng=${lon}`,
      {
        timeout: 5000,
      },
    );
    const data = response.data as { province?: string; city?: string; province_code?: string } | undefined;
    if (!data?.province) {
      return { success: false, error: 'ISP 返回数据不完整' };
    }
    const province = this.normalizeProvinceName(data.province);
    const provinceCode = PROVINCE_NAME_TO_CODE[province] || data.province_code || '';
    if (!provinceCode) {
      return { success: false, error: `无法识别的省份：${data.province}` };
    }
    return {
      success: true,
      province,
      provinceCode,
      city: data.city || '',
      source: '坐标反查',
    };
  }

  private estimateProvinceByCoords(lat: number, lon: number): string {
    if (lat >= 39.4 && lat <= 41.1 && lon >= 115.4 && lon <= 117.5) return '北京';
    if (lat >= 38.6 && lat <= 40.3 && lon >= 116.7 && lon <= 118.1) return '天津';
    if (lat >= 30.7 && lat <= 31.9 && lon >= 120.9 && lon <= 122.1) return '上海';
    if (lat >= 28.2 && lat <= 30.3 && lon >= 105.3 && lon <= 107.6) return '重庆';
    if (lat >= 36.0 && lat <= 42.6 && lon >= 113.5 && lon <= 119.9) return '河北';
    if (lat >= 34.5 && lat <= 40.9 && lon >= 110.2 && lon <= 114.7) return '山西';
    if (lat >= 38.4 && lat <= 43.5 && lon >= 118.8 && lon <= 125.8) return '辽宁';
    if (lat >= 40.9 && lat <= 46.3 && lon >= 121.6 && lon <= 131.3) return '吉林';
    if (lat >= 43.4 && lat <= 53.6 && lon >= 121.2 && lon <= 135.1) return '黑龙江';
    if (lat >= 30.8 && lat <= 35.1 && lon >= 116.4 && lon <= 121.9) return '江苏';
    if (lat >= 27.0 && lat <= 31.2 && lon >= 118.0 && lon <= 122.9) return '浙江';
    if (lat >= 29.4 && lat <= 34.6 && lon >= 114.9 && lon <= 119.7) return '安徽';
    if (lat >= 23.5 && lat <= 28.3 && lon >= 116.0 && lon <= 120.5) return '福建';
    if (lat >= 24.5 && lat <= 30.1 && lon >= 113.5 && lon <= 118.5) return '江西';
    if (lat >= 34.4 && lat <= 38.4 && lon >= 114.8 && lon <= 122.7) return '山东';
    if (lat >= 31.4 && lat <= 36.4 && lon >= 110.4 && lon <= 116.7) return '河南';
    if (lat >= 29.0 && lat <= 33.3 && lon >= 108.4 && lon <= 116.1) return '湖北';
    if (lat >= 24.6 && lat <= 30.1 && lon >= 108.8 && lon <= 114.3) return '湖南';
    if (lat >= 20.2 && lat <= 25.5 && lon >= 109.7 && lon <= 117.3) return '广东';
    if (lat >= 20.9 && lat <= 26.4 && lon >= 104.3 && lon <= 112.1) return '广西';
    if (lat >= 18.1 && lat <= 20.2 && lon >= 108.6 && lon <= 111.1) return '海南';
    if (lat >= 26.0 && lat <= 34.3 && lon >= 97.4 && lon <= 108.6) return '四川';
    if (lat >= 24.6 && lat <= 29.2 && lon >= 103.6 && lon <= 109.6) return '贵州';
    if (lat >= 21.1 && lat <= 29.2 && lon >= 97.5 && lon <= 106.2) return '云南';
    if (lat >= 31.7 && lat <= 39.6 && lon >= 105.5 && lon <= 111.2) return '陕西';
    if (lat >= 32.5 && lat <= 42.8 && lon >= 92.3 && lon <= 108.7) return '甘肃';
    if (lat >= 31.6 && lat <= 39.2 && lon >= 89.4 && lon <= 103.1) return '青海';
    if (lat >= 35.2 && lat <= 39.4 && lon >= 104.3 && lon <= 107.7) return '宁夏';
    if (lat >= 34.3 && lat <= 49.2 && lon >= 73.5 && lon <= 96.4) return '新疆';
    if (lat >= 37.4 && lat <= 53.6 && lon >= 97.2 && lon <= 126.1) return '内蒙古';
    if (lat >= 26.8 && lat <= 36.5 && lon >= 78.4 && lon <= 99.1) return '西藏';
    return '';
  }

  private normalizeProvinceName(raw: string): string {
    if (!raw) return '';
    const trimmed = raw.trim().replace(/[省市区自治]+$/g, '');
    for (const name of Object.keys(PROVINCE_NAME_TO_CODE)) {
      const short = name.replace(/[省市区自治]+$/g, '');
      if (trimmed.includes(short) || short.includes(trimmed)) {
        return name;
      }
    }
    if (raw.includes('内蒙古')) return '内蒙古';
    if (raw.includes('广西')) return '广西';
    if (raw.includes('西藏')) return '西藏';
    if (raw.includes('宁夏')) return '宁夏';
    if (raw.includes('新疆')) return '新疆';
    if (raw.includes('香港') || raw.includes('澳门')) return '广东';
    return raw;
  }

  async fetchFromApiZero(province: string): Promise<OilPriceResponse> {
    const url = 'https://v1.apizero.cn/api/oil-price';

    const response = await axios.post<ApiZeroResponse>(
      url,
      { province },
      {
        timeout: 8000,
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );

    const body = response.data;
    if ((body.code !== 200 && body.code !== 0) || !body.data?.prices) {
      throw new Error(body.message || body.msg || '接口返回异常');
    }

    const prices = body.data.prices;
    const data: OilPriceInfo[] = [];

    for (const item of prices) {
      const grade = APIZERO_TYPE_MAP[item.type];
      if (!grade) continue;
      data.push({
        province,
        grade,
        gradeLabel: GRADE_LABELS[grade],
        price: Number(item.price),
        unit: '元/升',
        updateDate: body.data.update_date || '',
        nextAdjustment: body.data.next_adjustment,
        source: 'ApiZero 油价',
      });
    }

    return {
      success: true,
      province,
      updateDate: body.data.update_date,
      nextAdjustment: body.data.next_adjustment,
      forecast: body.data.forecast,
      data,
    };
  }

  async fetchFromYoujia(province: string): Promise<OilPriceResponse> {
    const url = `https://youjia.abapi.cn/api/chart-data.php?province=${encodeURIComponent(province)}`;

    const response = await axios.get<YoujiaChartResponse>(url, {
      timeout: 8000,
    });

    const body = response.data;
    if (!body.dates || body.dates.length === 0 || !body.p92) {
      throw new Error('接口返回数据不完整');
    }

    const lastIdx = body.dates.length - 1;
    const updateDate = body.dates[lastIdx];
    const data: OilPriceInfo[] = [];

    for (const [key, grade] of Object.entries(YOUJIA_GRADE_MAP)) {
      const prices = body[key as keyof YoujiaChartResponse] as number[] | undefined;
      if (!prices || prices.length === 0) continue;
      data.push({
        province,
        grade,
        gradeLabel: GRADE_LABELS[grade],
        price: Number(prices[lastIdx]),
        unit: '元/升',
        updateDate,
        source: '今日油价 youjia.abapi.cn',
      });
    }

    return {
      success: true,
      province,
      updateDate,
      data,
    };
  }
}

export { GRADE_LABELS };

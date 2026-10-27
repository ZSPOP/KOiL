import { logger } from '@lark-apaas/client-toolkit/logger';
import { axiosForBackend } from '@lark-apaas/client-toolkit/utils/getAxiosForBackend';

import type {
  CreateKoilRecordRequest,
  UpdateKoilRecordRequest,
  KoilRecordListResponse,
  KoilTrendData,
  LastKoilRecord,
  OilPriceResponse,
  ProvinceInfo,
  IpLocationResponse,
  ReverseGeocodeResponse,
  DuplicateCheckResponse,
  MileageConflictCheckResponse,
  MileageBoundaryResponse,
  KoilRecordPrecheckResponse,
  ImportPreviewResponse,
  ImportConfirmResponse,
  HistoryOilPriceResponse,
  OilPriceDbStatusResponse,
  OilPriceDbUpdateResponse,
  KoilPricePendingListResponse,
  PriceDeviationCheckRequest,
  PriceDeviationCheckResponse,
  PriceDeviationThresholdResponse,
} from '@shared/api.interface';

export interface GetKoilRecordsParams {
  cursorDate?: string;
  cursorId?: string;
  pageSize?: number;
}

export async function getKoilRecords(
  params?: GetKoilRecordsParams,
): Promise<KoilRecordListResponse> {
  try {
    const query: Record<string, string> = {};
    if (params?.cursorDate) query.cursorDate = params.cursorDate;
    if (params?.cursorId) query.cursorId = params.cursorId;
    if (params?.pageSize) query.pageSize = String(params.pageSize);

    const queryStr = new URLSearchParams(query).toString();
    const url = queryStr ? `/api/koil-records?${queryStr}` : '/api/koil-records';

    const response = await axiosForBackend({
      url,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取加油记录失败', error);
    throw error;
  }
}

export async function checkDuplicate(
  reoilDate: string,
  mileage: number,
  oilAmount?: number,
  actualAmount?: number,
): Promise<DuplicateCheckResponse> {
  try {
    const params = new URLSearchParams();
    params.set('reoilDate', reoilDate);
    params.set('mileage', String(mileage));
    if (oilAmount !== undefined) params.set('oilAmount', String(oilAmount));
    if (actualAmount !== undefined) params.set('actualAmount', String(actualAmount));
    const response = await axiosForBackend({
      url: `/api/koil-records/duplicate-check?${params.toString()}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('检查重复记录失败', error);
    return { exists: false, sameDayCount: 0, similarExists: false };
  }
}

export async function checkMileageConflict(
  reoilDate: string,
  mileage: number,
): Promise<MileageConflictCheckResponse> {
  try {
    const params = new URLSearchParams();
    params.set('reoilDate', reoilDate);
    params.set('mileage', String(mileage));
    const response = await axiosForBackend({
      url: `/api/koil-records/mileage-conflict-check?${params.toString()}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('检查里程冲突失败', error);
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
}

export async function getMileageBoundary(
  reoilDate: string,
): Promise<MileageBoundaryResponse> {
  try {
    const params = new URLSearchParams();
    params.set('reoilDate', reoilDate);
    const response = await axiosForBackend({
      url: `/api/koil-records/mileage-boundary?${params.toString()}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取里程区间边界失败', error);
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
}

export async function precheckKoilRecord(params: {
  reoilDate: string;
  mileage?: number;
  oilAmount?: number;
  actualAmount?: number;
  source?: 'entry_page' | 'backfill_modal' | null;
  excludeId?: string;
  includeBoundary?: boolean;
  includePrevRecord?: boolean;
}): Promise<KoilRecordPrecheckResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/precheck',
      method: 'POST',
      data: params,
    });
    return response.data;
  } catch (error) {
    logger.error('预检请求失败', error);
    return {
      duplicate: { exists: false, sameDayCount: 0, similarExists: false },
      mileageChain: {
        hasConflict: false,
        message: null,
        prevDate: null,
        prevMileage: null,
        nextDate: null,
        nextMileage: null,
        sameDayMileages: [],
      },
      boundary: null,
      prevRecord: null,
      isBeforeFirst: false,
      firstDate: null,
      firstMileage: null,
    };
  }
}

export async function createKoilRecord(
  data: CreateKoilRecordRequest,
): Promise<{ id: string }> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records',
      method: 'POST',
      data,
    });
    return response.data;
  } catch (error) {
    logger.error('创建加油记录失败', error);
    throw error;
  }
}

export async function updateKoilRecord(
  id: string,
  data: UpdateKoilRecordRequest,
): Promise<void> {
  try {
    await axiosForBackend({
      url: `/api/koil-records/${id}`,
      method: 'PATCH',
      data,
    });
  } catch (error) {
    logger.error('更新加油记录失败', error);
    throw error;
  }
}

export async function deleteKoilRecord(id: string): Promise<void> {
  try {
    await axiosForBackend({
      url: `/api/koil-records/${id}`,
      method: 'DELETE',
    });
  } catch (error) {
    logger.error('删除加油记录失败', error);
    throw error;
  }
}

export async function getKoilTrend(): Promise<KoilTrendData> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/trend',
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取趋势数据失败', error);
    throw error;
  }
}

export async function getLatestKoilRecord(): Promise<LastKoilRecord | null> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/latest',
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取最新加油记录失败', error);
    throw error;
  }
}

export async function getPrevKoilRecord(
  reoilDate: string,
  mileage?: number,
  excludeId?: string,
): Promise<LastKoilRecord | null> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/prev-record',
      method: 'GET',
      params: {
        reoilDate,
        ...(mileage !== undefined ? { mileage: String(mileage) } : {}),
        ...(excludeId ? { excludeId } : {}),
      },
    });
    return response.data;
  } catch (error) {
    logger.error('获取前驱加油记录失败', error);
    return null;
  }
}

export async function getOilPrice(province: string): Promise<OilPriceResponse> {
  try {
    const response = await axiosForBackend({
      url: `/api/koil-records/oil-price?province=${encodeURIComponent(province)}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取油价失败', error);
    throw error;
  }
}

export async function getHistoryOilPrice(
  province: string,
  grade: string,
  date: string,
): Promise<HistoryOilPriceResponse | null> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/oil-price/history',
      method: 'GET',
      params: { province, grade, date },
    });
    return response.data;
  } catch (error) {
    logger.error('获取历史油价失败', error);
    return null;
  }
}

export async function getProvinces(): Promise<ProvinceInfo[]> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/oil-price/provinces',
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取省份列表失败', error);
    throw error;
  }
}

export async function getIpLocation(): Promise<IpLocationResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/oil-price/ip-location',
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取 IP 归属地失败', error);
    throw error;
  }
}

export async function getOilPriceDbStatus(): Promise<OilPriceDbStatusResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/oil-price-db/status',
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取油价数据库状态失败', error);
    throw error;
  }
}

export async function getTodayOilPriceFromDb(province: string): Promise<OilPriceResponse> {
  try {
    const response = await axiosForBackend({
      url: `/api/koil-records/oil-price-db/today?province=${encodeURIComponent(province)}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('从油价库获取今日油价失败', error);
    throw error;
  }
}

export async function updateOilPriceDb(): Promise<OilPriceDbUpdateResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/oil-price-db/update',
      method: 'POST',
    });
    return response.data;
  } catch (error) {
    logger.error('更新油价数据库失败', error);
    throw error;
  }
}

export async function setOilPriceDbAutoUpdate(enabled: boolean): Promise<{ enabled: boolean }> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/oil-price-db/auto-update',
      method: 'PATCH',
      data: { enabled },
    });
    return response.data;
  } catch (error) {
    logger.error('设置油价自动更新失败', error);
    throw error;
  }
}

export async function getPriceDeviationThreshold(): Promise<PriceDeviationThresholdResponse> {
  const { data } = await axiosForBackend.get<PriceDeviationThresholdResponse>(
    '/api/koil-records/price-deviation/threshold',
  );
  return data;
}

export async function setPriceDeviationThreshold(thresholdPercent: number): Promise<PriceDeviationThresholdResponse> {
  const { data } = await axiosForBackend.patch<PriceDeviationThresholdResponse>(
    '/api/koil-records/price-deviation/threshold',
    { thresholdPercent },
  );
  return data;
}

export async function checkPriceDeviation(
  params: PriceDeviationCheckRequest,
): Promise<PriceDeviationCheckResponse> {
  const { data } = await axiosForBackend.post<PriceDeviationCheckResponse>(
    '/api/koil-records/price-deviation/check',
    params,
  );
  return data;
}

export async function checkPriceDeviationBatch(
  items: Array<{ id: string; province: string; grade: string; date: string; oilAmount: number; actualAmount: number }>,
): Promise<{ items: Array<{ id: string; result: PriceDeviationCheckResponse }> }> {
  const { data } = await axiosForBackend.post<{ items: Array<{ id: string; result: PriceDeviationCheckResponse }> }>(
    '/api/koil-records/price-deviation/check-batch',
    { items },
  );
  return data;
}

export async function reverseGeocode(
  lat: number,
  lon: number,
): Promise<ReverseGeocodeResponse> {
  try {
    const response = await axiosForBackend({
      url: `/api/koil-records/oil-price/reverse-geocode?lat=${lat}&lon=${lon}`,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('反向地理编码失败', error);
    throw error;
  }
}

export async function exportKoilRecordsCsv(): Promise<number> {
  try {
    const response = await axiosForBackend.get('/api/koil-records/export', {
      responseType: 'blob',
    });
    const count = Number(response.headers['x-export-count'] ?? 0);
    const filenameHeader = response.headers['x-export-filename'];
    const filename = filenameHeader
      ? decodeURIComponent(filenameHeader)
      : `KOiL_加油记录_${new Date().toISOString().slice(0, 10)}.csv`;
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    return count;
  } catch (error) {
    logger.error('导出加油记录失败', error);
    throw error;
  }
}

export async function exportBackupJson(settings: Record<string, string | number | boolean>): Promise<number> {
  try {
    const response = await axiosForBackend.post('/api/koil-records/export-backup', { settings }, {
      responseType: 'blob',
    });
    const count = Number(response.headers['x-export-count'] ?? 0);
    const filenameHeader = response.headers['x-export-filename'];
    const filename = filenameHeader
      ? decodeURIComponent(filenameHeader)
      : `KOiL_完整备份_${new Date().toISOString().slice(0, 10)}.json`;
    const blob = new Blob([response.data], { type: 'application/json;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    return count;
  } catch (error) {
    logger.error('导出完整备份失败', error);
    throw error;
  }
}

export async function previewImportCsv(
  csvContent: string,
): Promise<ImportPreviewResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/import/preview',
      method: 'POST',
      data: { csvContent },
    });
    return response.data;
  } catch (error) {
    logger.error('CSV 导入预览失败', error);
    throw error;
  }
}

export async function confirmImportCsv(
  csvContent: string,
): Promise<ImportConfirmResponse> {
  try {
    const response = await axiosForBackend({
      url: '/api/koil-records/import/confirm',
      method: 'POST',
      data: { csvContent },
    });
    return response.data;
  } catch (error) {
    logger.error('CSV 导入确认失败', error);
    throw error;
  }
}

export async function getOilPricePending(
  status?: string,
): Promise<KoilPricePendingListResponse> {
  try {
    const params = new URLSearchParams();
    if (status) params.set('status', status);
    const queryStr = params.toString();
    const url = queryStr
      ? `/api/koil-records/oil-price-db/pending?${queryStr}`
      : '/api/koil-records/oil-price-db/pending';
    const response = await axiosForBackend({
      url,
      method: 'GET',
    });
    return response.data;
  } catch (error) {
    logger.error('获取待确认油价更新失败', error);
    throw error;
  }
}

export async function acceptOilPricePending(
  id: string,
  useSource: 'primary' | 'secondary',
  customPrice?: number,
): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: `/api/koil-records/oil-price-db/pending/${id}/accept`,
      method: 'POST',
      data: { useSource, customPrice },
    });
    return response.data;
  } catch (error) {
    logger.error('采纳待确认油价失败', error);
    throw error;
  }
}

export async function exportOilPriceHistoryCsv(): Promise<number> {
  try {
    const response = await axiosForBackend.get('/api/koil-records/oil-price-db/export', {
      responseType: 'blob',
    });
    const count = Number(response.headers['x-export-count'] ?? 0);
    const filenameHeader = response.headers['x-export-filename'];
    const filename = filenameHeader
      ? decodeURIComponent(filenameHeader)
      : `KOiL_油价历史记录_${new Date().toISOString().slice(0, 10)}.csv`;
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    return count;
  } catch (error) {
    logger.error('导出油价历史记录失败', error);
    throw error;
  }
}

export async function ignoreOilPricePending(
  id: string,
): Promise<{ success: boolean }> {
  try {
    const response = await axiosForBackend({
      url: `/api/koil-records/oil-price-db/pending/${id}/ignore`,
      method: 'POST',
    });
    return response.data;
  } catch (error) {
    logger.error('忽略待确认油价失败', error);
    throw error;
  }
}

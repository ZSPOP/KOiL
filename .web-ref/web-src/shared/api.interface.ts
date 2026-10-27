export type OilGrade = '92' | '95' | '98' | '0';

export type KoilRecordSource = 'entry_page' | 'backfill_modal';

export interface KoilRecord {
  id: string;
  reoilDate: string;
  mileage: number;
  oilAmount: number;
  payableAmount: number;
  actualAmount: number;
  oilPrice: number | null;
  oilGrade: OilGrade | null;
  province: string | null;
  source: KoilRecordSource | null;
}

export interface KoilRecordWithDerived extends KoilRecord {
  prevReoilDate: string | null;
  prevMileage: number | null;
  prevOilAmount: number | null;
  prevActualAmount: number | null;
  distance: number | null;
  oilDays: number | null;
  oilDateRange: string | null;
  unitPricePayable: number;
  unitPriceActual: number;
  oilConsumption: number | null;
  costPerKm: number | null;
  isAbnormalOilConsumption: boolean;
  oilConsumptionLevel: 'high' | 'low' | 'normal' | null;
  shortDistanceSkipped: boolean;
  priceDeviation: PriceDeviationInfo | null;
}

export interface PriceDeviationInfo {
  hasDeviation: boolean;
  actualUnitPrice: number;
  referencePrice: number;
  referenceDate: string;
  referenceGrade: string;
  deviationPercent: number;
  thresholdPercent: number;
  pendingAfter: boolean;
  isLatestVerified: boolean;
}

export interface CreateKoilRecordRequest {
  reoilDate: string;
  mileage: number;
  oilAmount: number;
  payableAmount: number;
  actualAmount: number;
  oilPrice?: number | null;
  oilGrade?: OilGrade | null;
  province?: string | null;
  source?: KoilRecordSource | null;
}

export interface MileageConflictCheckResponse {
  hasConflict: boolean;
  message: string | null;
  prevDate: string | null;
  prevMileage: number | null;
  nextDate: string | null;
  nextMileage: number | null;
  sameDayMileages: number[];
}

export interface MileageBoundaryResponse {
  prevDate: string | null;
  prevMileage: number | null;
  nextDate: string | null;
  nextMileage: number | null;
  sameDayMileages: number[];
  isBeforeFirst: boolean;
  firstDate: string | null;
  firstMileage: number | null;
}

export interface UpdateKoilRecordRequest {
  reoilDate?: string;
  mileage?: number;
  oilAmount?: number;
  payableAmount?: number;
  actualAmount?: number;
  oilPrice?: number | null;
  oilGrade?: OilGrade | null;
  province?: string | null;
}

export interface KoilSummary {
  totalCount: number;
  totalDistance: number;
  totalOilAmount: number;
  totalPayable: number;
  totalActual: number;
  avgOilConsumption: number;
  avgCostPerKm: number;
  avgUnitPriceActual: number;
  avgDistancePerReoil: number;
  abnormalCount: number;
}

export interface KoilRecordListResponse {
  items: KoilRecordWithDerived[];
  summary: KoilSummary;
  totalCount: number;
  hasMore: boolean;
}

export interface TrendPoint {
  reoilDate: string;
  oilConsumption: number | null;
  costPerKm: number | null;
}

export interface MonthlyCostItem {
  monthLabel: string;
  monthKey: string;
  totalActual: number;
}

export interface KoilTrendData {
  points: TrendPoint[];
  monthlyCost: MonthlyCostItem[];
}

export interface LastKoilRecord {
  reoilDate: string;
  mileage: number;
  oilAmount: number;
  actualAmount: number;
  oilPrice: number | null;
  oilGrade: OilGrade | null;
  distance: number | null;
  avgOilConsumption: number | null;
}

export interface DuplicateCheckResponse {
  exists: boolean;
  sameDayCount: number;
  similarExists: boolean;
}

export interface KoilRecordPrecheckResponse {
  duplicate: DuplicateCheckResponse;
  mileageChain: MileageConflictCheckResponse;
  boundary: MileageBoundaryResponse | null;
  prevRecord: LastKoilRecord | null;
  isBeforeFirst: boolean;
  firstDate: string | null;
  firstMileage: number | null;
}

export interface OilPriceInfo {
  province: string;
  grade: OilGrade;
  gradeLabel: string;
  price: number;
  unit: string;
  updateDate: string;
  nextAdjustment?: string;
  forecast?: string;
  source: string;
  previousPrice?: number;
  priceChange?: number;
  priceChangePercent?: number;
  previousDate?: string;
}

export interface OilPriceResponse {
  success: boolean;
  data?: OilPriceInfo[];
  province?: string;
  updateDate?: string;
  nextAdjustment?: string;
  forecast?: string;
  error?: string;
}

export interface ProvinceInfo {
  code: string;
  name: string;
}

export interface IpLocationResponse {
  province: string;
  provinceCode?: string;
  source: string;
}

export interface ReverseGeocodeResponse {
  success: boolean;
  province?: string;
  provinceCode?: string;
  city?: string;
  source?: string;
  error?: string;
}

export interface ImportPreviewItem {
  rowNumber: number;
  reoilDate?: string;
  mileage?: number;
  oilAmount?: number;
  actualAmount?: number;
  payableAmount?: number;
  oilGrade?: OilGrade | null;
  oilPrice?: number | null;
  province?: string | null;
  source?: string | null;
  status: 'valid' | 'duplicate' | 'error' | 'mileage_conflict';
  errorMessage?: string;
  conflictMessage?: string;
}

export interface ImportPreviewResponse {
  valid: ImportPreviewItem[];
  duplicates: ImportPreviewItem[];
  mileageConflicts: ImportPreviewItem[];
  errors: ImportPreviewItem[];
  totalRows: number;
}

export interface ImportConfirmResponse {
  imported: number;
  skipped: number;
  errors: number;
  mileageConflicts: number;
  duplicates: number;
  errorDetails?: { rowNumber: number; message: string }[];
  skippedDetails?: { rowNumber: number; date?: string; mileage?: number; reason: string }[];
}

export interface ExportBackupResponse {
  version: number;
  exportedAt: string;
  koilRecords: KoilRecordWithDerived[];
  settings: Record<string, string | number | boolean>;
}

export interface ImportCsvRequest {
  csvContent: string;
}

export interface HistoryOilPriceResponse {
  price: number;
  date: string;
  province: string;
  grade: string;
  isLatestVerified: boolean;
  latestVerifiedDate: string | null;
  pendingAfter: boolean;
}

export interface OilPriceDbStatusResponse {
  initialized: boolean;
  latestDate: string | null;
  lastPriceChangeDate: string | null;
  lastWindowChecked: string | null;
  versionDate: string | null;
  provinceCount: number;
  gradeCount: number;
  adjustmentCount: number;
  totalRecords: number;
  lastCheckedAt: string | null;
  lastUpdatedAt: string | null;
  initializedAt: string | null;
  autoUpdateEnabled: boolean;
  checkIntervalHours: number;
  lastUpdateMessage: string | null;
  isStranded: boolean;
  pendingCount: number;
}

export interface OilPriceDbUpdateResponse {
  success: boolean;
  newAdjustments: number;
  newRecords: number;
  pendingCount: number;
  latestDate: string | null;
  message: string;
  error?: string;
}

export interface OilPriceDbSettingsRequest {
  autoUpdateEnabled: boolean;
}

export interface OilPriceDbSettingsResponse {
  autoUpdateEnabled: boolean;
  checkIntervalHours: number;
}

export type KoilPricePendingStatus = 'pending' | 'accepted' | 'ignored';

export interface KoilPricePendingItem {
  id: string;
  province: string;
  grade: string;
  effectiveDate: string;
  primaryPrice: number | null;
  primarySource: string | null;
  secondaryPrice: number | null;
  secondarySource: string | null;
  status: KoilPricePendingStatus;
  resolvedPrice: number | null;
  resolvedSource: string | null;
  note: string | null;
  createdAt: string;
}

export interface KoilPricePendingListResponse {
  items: KoilPricePendingItem[];
  total: number;
}

export interface KoilPricePendingAcceptRequest {
  id: string;
  useSource: 'primary' | 'secondary';
  customPrice?: number;
}

export interface PriceDeviationCheckRequest {
  province: string;
  grade: OilGrade;
  date: string;
  oilAmount: number;
  actualAmount: number;
}

export interface PriceDeviationCheckResponse {
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
}

export interface PriceDeviationThresholdResponse {
  thresholdPercent: number;
}

export interface PriceDeviationThresholdRequest {
  thresholdPercent: number;
}

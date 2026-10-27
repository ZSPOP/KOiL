/* eslint-disable */
/** auto generated, do not edit */
import { sql } from 'drizzle-orm';
import { date, index, jsonb, numeric, pgEnum, pgTable, text, timestamp, uniqueIndex, uuid, varchar, customType } from "drizzle-orm/pg-core"

export const customTimestamptz = customType<{
  data: Date;
  driverData: string;
  config: { precision?: number };
}>({
  dataType(config) {
    const precision = typeof config?.precision !== 'undefined'
      ? ` (${config.precision})`
      : '';
    return `timestamptz${precision}`;
  },
  toDriver(value: Date | string | number) {
    if (value == null) return value as any;
    if (typeof value === 'number') return new Date(value).toISOString();
    if (typeof value === 'string') return value;
    if (value instanceof Date) return value.toISOString();
    throw new Error('Invalid timestamp value');
  },
  fromDriver(value: string | Date): Date {
    if (value instanceof Date) return value;
    return new Date(value);
  },
});

export const userProfile = customType<{
  data: string;
  driverData: string;
}>({
  dataType() {
    return 'user_profile';
  },
  toDriver(value: string) {
    return sql`ROW(${value})::user_profile`;
  },
  fromDriver(value: string) {
    const [userId] = value.slice(1, -1).split(',');
    return userId.trim();
  },
});

export type FileAttachment = {
  bucket_id: string;
  file_path: string;
};

export const fileAttachment = customType<{
  data: FileAttachment;
  driverData: string;
}>({
  dataType() {
    return 'file_attachment';
  },
  toDriver(value: FileAttachment) {
    return sql`ROW(${value.bucket_id},${value.file_path})::file_attachment`;
  },
  fromDriver(value: string): FileAttachment {
    const [bucketId, filePath] = value.slice(1, -1).split(',');
    return { bucket_id: bucketId.trim(), file_path: filePath.trim() };
  },
});

export function escapeLiteral(str: string): string {
  return "'" + str.replace(/'/g, "''") + "'";
}

export const userProfileArray = customType<{
  data: string[];
  driverData: string;
}>({
  dataType() {
    return 'user_profile[]';
  },
  toDriver(value: string[]) {
    if (!value || value.length === 0) {
      return sql`'{}'::user_profile[]`;
    }
    const elements = value.map(id => `ROW(${escapeLiteral(id)})::user_profile`).join(',');
    return sql.raw(`ARRAY[${elements}]::user_profile[]`);
  },
  fromDriver(value: string): string[] {
    if (!value || value === '{}') return [];
    const inner = value.slice(1, -1);
    const matches = inner.match(/\([^)]*\)/g) || [];
    return matches.map(m => m.slice(1, -1).split(',')[0].trim());
  },
});

export const fileAttachmentArray = customType<{
  data: FileAttachment[];
  driverData: string;
}>({
  dataType() {
    return 'file_attachment[]';
  },
  toDriver(value: FileAttachment[]) {
    if (!value || value.length === 0) {
      return sql`'{}'::file_attachment[]`;
    }
    const elements = value.map(f =>
      `ROW(${escapeLiteral(f.bucket_id)},${escapeLiteral(f.file_path)})::file_attachment`
    ).join(',');
    return sql.raw(`ARRAY[${elements}]::file_attachment[]`);
  },
  fromDriver(value: string): FileAttachment[] {
    if (!value || value === '{}') return [];
    const inner = value.slice(1, -1);
    const matches = inner.match(/\([^)]*\)/g) || [];
    return matches.map(m => {
      const [bucketId, filePath] = m.slice(1, -1).split(',');
      return { bucket_id: bucketId.trim(), file_path: filePath.trim() };
    });
  },
});

export const opType = pgEnum("op_type", ['INSERT', 'UPDATE', 'DELETE']);

export const koilPricePending = pgTable("koil_price_pending", {
  id: uuid("id").primaryKey().defaultRandom(),
  province: varchar("province", { length: 50 }).notNull(),
  grade: varchar("grade", { length: 20 }).notNull(),
  effectiveDate: date("effective_date").notNull(),
  primaryPrice: numeric("primary_price"),
  primarySource: varchar("primary_source", { length: 100 }),
  secondaryPrice: numeric("secondary_price"),
  secondarySource: varchar("secondary_source", { length: 100 }),
  status: varchar("status", { length: 20 }).notNull().default('pending'),
  resolvedPrice: numeric("resolved_price"),
  resolvedSource: varchar("resolved_source", { length: 100 }),
  note: text("note"),
  // System field: Creation time (auto-filled, do not modify)
  createdAt: customTimestamptz("_created_at", { precision: 3 }).notNull().default(sql`CURRENT_TIMESTAMP`),
  // System field: Update time (auto-filled, do not modify)
  updatedAt: customTimestamptz("_updated_at", { precision: 3 }).notNull().default(sql`CURRENT_TIMESTAMP`),
}, (table) => [
  uniqueIndex("uniq_koil_price_pending_pgd").on(table.province, table.grade, table.effectiveDate),
  index("idx_koil_price_pending_status").on(table.status, table.effectiveDate),
]);

export const appConfig = pgTable("app_config", {
  id: uuid("id").primaryKey().defaultRandom(),
  configKey: varchar("config_key", { length: 100 }).notNull().unique(),
  configValue: text("config_value").notNull(),
  // System field: Creation time (auto-filled, do not modify)
  createdAt: customTimestamptz("_created_at", { precision: 3 }).notNull().default(sql`CURRENT_TIMESTAMP`),
  // System field: Creator (auto-filled, do not modify)
  createdBy: userProfile("_created_by").default(sql`CASE
    WHEN (current_setting('app.user_id'::text, true) = ''::text) THEN NULL`),
  // System field: Update time (auto-filled, do not modify)
  updatedAt: customTimestamptz("_updated_at", { precision: 3 }).notNull().default(sql`CURRENT_TIMESTAMP`),
  // System field: Updater (auto-filled, do not modify)
  updatedBy: userProfile("_updated_by").default(sql`CASE
    WHEN (current_setting('app.user_id'::text, true) = ''::text) THEN NULL`),
}, (table) => [
  uniqueIndex("app_config_config_key_key").on(table.configKey),
]);

export const koilPriceHistory = pgTable("koil_price_history", {
  id: uuid("id").primaryKey().defaultRandom(),
  date: date("date").notNull(),
  province: varchar("province", { length: 50 }).notNull(),
  grade: varchar("grade", { length: 20 }).notNull(),
  price: numeric("price").notNull(),
  sourceUrl: text("source_url"),
  verifiedBy: text("verified_by"),
  // System field: Creation time (auto-filled, do not modify)
  createdAt: customTimestamptz("_created_at", { precision: 3 }).notNull().default(sql`CURRENT_TIMESTAMP`),
  // System field: Creator (auto-filled, do not modify)
  createdBy: userProfile("_created_by").default(sql`CASE
    WHEN (current_setting('app.user_id'::text, true) = ''::text) THEN NULL`),
  // System field: Update time (auto-filled, do not modify)
  updatedAt: customTimestamptz("_updated_at", { precision: 3 }).notNull().default(sql`CURRENT_TIMESTAMP`),
  // System field: Updater (auto-filled, do not modify)
  updatedBy: userProfile("_updated_by").default(sql`CASE
    WHEN (current_setting('app.user_id'::text, true) = ''::text) THEN NULL`),
}, (table) => [
  uniqueIndex("uniq_koil_price_history_pgd").on(table.province, table.grade, table.date),
  index("idx_koil_price_history_pgd_desc").on(table.province, table.grade, table.date),
]);

export const pgAudit = pgTable("pg_audit", {
  eventId: varchar("event_id", { length: 64 }).primaryKey(),
  eventTime: timestamp("event_time", { mode: 'string' }).notNull(),
  targetTable: varchar("target_table", { length: 255 }).notNull(),
  type: opType("type").notNull(),
  /**
   * 数据变更日志详情
   */
  details: jsonb("details"),
}, (table) => [
  index("idx_pg_audit_table_name").on(table.targetTable, table.eventTime),
  index("idx_pg_audit_table").on(table.targetTable),
]);

export const koilRecord = pgTable("koil_record", {
  id: uuid("id").primaryKey().defaultRandom(),
  reoilDate: date("reoil_date").notNull(),
  mileage: numeric("mileage").notNull(),
  oilAmount: numeric("oil_amount").notNull(),
  payableAmount: numeric("payable_amount").notNull(),
  actualAmount: numeric("actual_amount").notNull(),
  oilPrice: numeric("oil_price"),
  oilGrade: varchar("oil_grade", { length: 20 }),
  source: varchar("source", { length: 20 }),
  province: varchar("province", { length: 50 }),
  // System field: Creation time (auto-filled, do not modify)
  createdAt: customTimestamptz("_created_at", { precision: 3 }).notNull().default(sql`CURRENT_TIMESTAMP`),
  // System field: Creator (auto-filled, do not modify)
  createdBy: userProfile("_created_by").default(sql`CASE
    WHEN (current_setting('app.user_id'::text, true) = ''::text) THEN NULL`),
  // System field: Update time (auto-filled, do not modify)
  updatedAt: customTimestamptz("_updated_at", { precision: 3 }).notNull().default(sql`CURRENT_TIMESTAMP`),
  // System field: Updater (auto-filled, do not modify)
  updatedBy: userProfile("_updated_by").default(sql`CASE
    WHEN (current_setting('app.user_id'::text, true) = ''::text) THEN NULL`),
}, (table) => [
  index("idx_koil_record_reoil_date").on(table.reoilDate),
  // Complex index: CREATE UNIQUE INDEX uniq_koil_record_user_date_mileage ON koil_record USING btree (((_created_by).user_id), reoil_date, mileage),
  // Complex index: CREATE INDEX idx_koil_record_user_date ON koil_record USING btree (((_created_by).user_id), reoil_date DESC, id DESC),
  index("idx_koil_record_province_oil_grade_date").on(table.province, table.oilGrade, table.reoilDate),
]);

// table aliases
export const appConfigTable = appConfig;
export const koilPriceHistoryTable = koilPriceHistory;
export const koilPricePendingTable = koilPricePending;
export const koilRecordTable = koilRecord;
export const pgAuditTable = pgAudit;

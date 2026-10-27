import { useRef, useState, useEffect } from 'react';
import { Upload as UploadIcon, X as XIcon, CheckCircle2 } from 'lucide-react';
import { useTranslation } from '@client/src/hooks/useTranslation';
import { toast } from 'sonner';
import { logger } from '@lark-apaas/client-toolkit/logger';

import { Button } from '@client/src/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@client/src/components/ui/dialog';
import {
  previewImportCsv,
  confirmImportCsv,
} from '@client/src/api/koil-records';
import type {
   ImportPreviewResponse,
   ImportConfirmResponse,
 } from '@shared/api.interface';
import { BounceScroll } from '@client/src/components/ui/bounce-scroll';

export interface ImportDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImported: () => void;
}

export function ImportDialog({ open, onOpenChange, onImported }: ImportDialogProps) {
  const { t } = useTranslation();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [fileName, setFileName] = useState<string>('');
  const [csvContent, setCsvContent] = useState<string>('');
  const [preview, setPreview] = useState<ImportPreviewResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<ImportConfirmResponse | null>(
    null,
  );
  const [activeTab, setActiveTab] = useState<'valid' | 'duplicates' | 'mileage_conflicts' | 'errors'>(
    'valid',
  );
  const [isDragging, setIsDragging] = useState(false);

  useEffect(() => {
    if (!open) {
      setFileName('');
      setCsvContent('');
      setPreview(null);
      setPreviewLoading(false);
      setImporting(false);
      setImportResult(null);
      setActiveTab('valid');
      setIsDragging(false);
    }
  }, [open]);

  const readFile = (file: File) => {
    if (!file.name.toLowerCase().endsWith('.csv')) {
      toast.error(t('import.pleaseUpload'));
      return;
    }
    setFileName(file.name);
    setPreview(null);
    setImportResult(null);
    const reader = new FileReader();
    reader.onload = (e) => {
      let text = String(e.target?.result ?? '');
      if (text.charCodeAt(0) === 0xfeff) {
        text = text.slice(1);
      }
      setCsvContent(text);
      void runPreview(text);
    };
    reader.onerror = () => {
      toast.error(t('import.readFailed'));
      logger.error('CSV 文件读取失败');
    };
    reader.readAsText(file, 'UTF-8');
  };

  const runPreview = async (content: string) => {
    try {
      setPreviewLoading(true);
      const result = await previewImportCsv(content);
      setPreview(result);
    } catch (error) {
      logger.error('CSV 导入预览失败', error);
      toast.error(t('import.previewFailed'));
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) readFile(file);
    e.target.value = '';
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files?.[0];
    if (file) readFile(file);
  };

  const handleConfirmImport = async () => {
    if (!csvContent) return;
    try {
      setImporting(true);
      const result = await confirmImportCsv(csvContent);
      setImportResult(result);
      toast.success(t('import.successCount', { count: result.imported }));
      setTimeout(() => {
        onOpenChange(false);
        onImported();
      }, 1500);
    } catch (error) {
      logger.error('CSV 导入确认失败', error);
      toast.error(t('import.importFailed'));
    } finally {
      setImporting(false);
    }
  };

  const renderList = (
    items: {
      rowNumber: number;
      reoilDate?: string;
      mileage?: number;
      errorMessage?: string;
      conflictMessage?: string;
    }[],
    max = 5,
    showConflict = false,
  ) => {
    const visible = items.slice(0, max);
    const remaining = items.length - max;
    return (
      <div className="space-y-2">
        {visible.map((item) => (
          <div
            key={item.rowNumber}
            className="flex items-center justify-between rounded-xl bg-[var(--glass-bg-strong)] px-3 py-2 text-xs"
          >
            <div className="flex items-center gap-2">
              <span className="inline-flex h-5 min-w-[28px] items-center justify-center rounded-md bg-muted-foreground/10 px-1.5 text-[10px] font-medium text-muted-foreground">
                #{item.rowNumber}
              </span>
              <span className="text-foreground-secondary tabular-nums">
                {item.reoilDate || '-'}
                {item.mileage ? ` · ${item.mileage} ${t('unit.km')}` : ''}
              </span>
            </div>
            {(item.errorMessage || (showConflict && item.conflictMessage)) && (
              <span
                className="max-w-[180px] truncate text-destructive"
                title={item.errorMessage || (showConflict ? item.conflictMessage : '') || ''}
              >
                {item.errorMessage || (showConflict ? item.conflictMessage : '') || ''}
              </span>
            )}
          </div>
        ))}
        {remaining > 0 && (
          <p className="text-center text-[11px] text-muted-foreground">
            {t('import.etcCount', { first: '...', count: items.length })}
          </p>
        )}
        {items.length === 0 && (
          <p className="py-4 text-center text-xs text-muted-foreground">
            {t('import.noData')}
          </p>
        )}
      </div>
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="max-w-lg overflow-hidden p-0"
      >
        <div className="relative px-6 pb-2 pt-6 sm:px-7 sm:pt-7">
          <DialogHeader className="text-left">
             <DialogTitle className="text-xl font-bold text-foreground">
               {t('import.title')}
             </DialogTitle>
          </DialogHeader>
          <button
            type="button"
            onClick={() => onOpenChange(false)}
             className="absolute right-5 top-5 flex size-8 items-center justify-center rounded-full border border-transparent text-muted-foreground transition-all duration-200"
             aria-label={t('common.close')}
          >
            <XIcon className="size-4" />
          </button>
        </div>

        <BounceScroll className="max-h-[65vh] space-y-4 px-6 py-4 sm:px-7">
          {importResult ? (
            <div className="flex flex-col items-center gap-3 py-8 text-center">
              <div className="flex size-16 items-center justify-center rounded-full bg-emerald-500/15">
                <CheckCircle2 className="size-8 text-emerald-500" />
              </div>
              <p className="text-lg font-bold text-foreground">{t('import.completed')}</p>
               <p className="text-sm text-muted-foreground">
                 {t('import.successCount', { count: importResult.imported })}
                 {importResult.duplicates > 0 && (
                   <>
                     {' '}
                     重复跳过 {importResult.duplicates} 条
                   </>
                 )}
                 {importResult.mileageConflicts > 0 && (
                   <>
                     {' '}
                     里程冲突跳过 {importResult.mileageConflicts} 条
                   </>
                 )}
                 {importResult.errors > 0 && (
                   <>
                     {' '}
                     {t('import.errorCount', { count: importResult.errors })}
                   </>
                 )}
               </p>
            </div>
          ) : (
            <>
              <div
                onDragOver={(e) => {
                  e.preventDefault();
                  setIsDragging(true);
                }}
                onDragLeave={() => setIsDragging(false)}
                onDrop={handleDrop}
                onClick={() => fileInputRef.current?.click()}
                className={`flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 py-8 text-center transition-all duration-300 ${
                  isDragging
                    ? 'border-primary bg-primary-soft/50'
                    : 'border-border-light bg-[var(--glass-bg-strong)]'
                }`}
              >
                <UploadIcon className="mb-3 size-8 text-primary" />
                <p className="text-sm font-medium text-foreground">
                   {t('import.uploadHint')}
                 </p>
                 <p className="mt-1 text-xs text-muted-foreground">
                   {t('import.uploadDesc')}
                 </p>
                 {fileName && (
                   <p className="mt-3 text-xs font-medium text-primary">
                     {t('import.fileSelected', { name: fileName })}
                   </p>
                 )}
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".csv"
                  className="hidden"
                  onChange={handleFileChange}
                />
              </div>

              {previewLoading && (
                <div className="flex items-center justify-center py-6 text-sm text-muted-foreground">
                   <span className="mr-2 inline-block size-4 animate-spin rounded-full border-2 border-primary/30 border-t-primary" />
                   {t('import.parsing')}
                </div>
              )}

              {preview && !previewLoading && (
                <div className="space-y-3">
                  <div className="flex gap-1 rounded-full bg-muted-foreground/10 p-1">
                     {[
                       {
                         key: 'valid' as const,
                         label: `${t('import.pending')} ${preview.valid.length}`,
                       },
                       {
                         key: 'duplicates' as const,
                         label: `${t('import.duplicate')} ${preview.duplicates.length}`,
                       },
                       {
                         key: 'mileage_conflicts' as const,
                         label: `里程冲突 ${preview.mileageConflicts.length}`,
                       },
                       {
                         key: 'errors' as const,
                         label: `${t('import.error')} ${preview.errors.length}`,
                       },
                     ].map((tab) => (
                      <button
                        key={tab.key}
                        type="button"
                        onClick={() => setActiveTab(tab.key)}
                        className={`flex-1 rounded-full px-3 py-1.5 text-xs font-medium transition-all duration-200 ${
                          activeTab === tab.key
                            ? 'gradient-primary text-white shadow-sm'
                            : 'text-muted-foreground'
                        }`}
                      >
                        {tab.label}
                      </button>
                    ))}
                  </div>
                   <div className="min-h-[140px]">
                     {activeTab === 'valid' && renderList(preview.valid)}
                     {activeTab === 'duplicates' &&
                       renderList(preview.duplicates)}
                     {activeTab === 'mileage_conflicts' &&
                       renderList(preview.mileageConflicts, 5, true)}
                     {activeTab === 'errors' && renderList(preview.errors)}
                   </div>
                </div>
              )}
            </>
          )}
        </BounceScroll>

        <DialogFooter className="flex-row gap-2 px-6 pb-6 pt-2 sm:px-7 sm:pb-7">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={importing}
             className="flex-1 rounded-full border-border-light bg-[var(--glass-bg-strong)] text-foreground-secondary"
           >
              {t('common.cancel')}
           </Button>
          <Button
            type="button"
            onClick={() => void handleConfirmImport()}
            disabled={importing || !csvContent || !!importResult}
             className="flex-1 rounded-full gradient-primary text-white shadow-lg shadow-black/25 disabled:opacity-50"
           >
              {importing ? t('import.importing') : t('import.confirmImport')}
           </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

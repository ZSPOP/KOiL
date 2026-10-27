import { useState } from 'react';
import {
  MapPinIcon,
  XIcon,
  Loader2Icon,
  ShieldCheckIcon,
  XCircleIcon,
} from 'lucide-react';
import { logger } from '@lark-apaas/client-toolkit/logger';

import { Button } from '@client/src/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@client/src/components/ui/dialog';

interface LocationPermissionDialogProps {
  open: boolean;
  isLocating: boolean;
  locateError: string;
  onAllow: () => void;
  onDeny: () => void;
}

export function LocationPermissionDialog({
  open,
  isLocating,
  locateError,
  onAllow,
  onDeny,
}: LocationPermissionDialogProps) {
  const [showingBrowserPrompt, setShowingBrowserPrompt] = useState(false);

  const handleAllow = (): void => {
    setShowingBrowserPrompt(true);
    try {
      onAllow();
    } catch (error) {
      logger.error('请求定位失败', error);
      setShowingBrowserPrompt(false);
    }
  };

  return (
    <Dialog open={open}>
      <DialogContent showCloseButton={false} className="w-[90%] max-w-md rounded-2xl border-0 p-0 shadow-2xl shadow-black/20 [--glass-bg:hsl(var(--surface-1))]">
        <div className="relative overflow-hidden rounded-2xl">
          <div className="relative px-6 pb-6 pt-8 sm:px-8 sm:pb-8 sm:pt-10">
            <button
              type="button"
              onClick={onDeny}
              className="absolute right-4 top-4 flex size-8 items-center justify-center rounded-full text-muted-foreground transition-colors"
              aria-label="关闭"
            >
              <XIcon className="size-4" />
            </button>

            <DialogHeader className="text-left">
              <div className="mb-5 flex size-14 items-center justify-center rounded-2xl gradient-primary shadow-lg shadow-black/30">
                <MapPinIcon className="size-7 text-white" />
              </div>
              <DialogTitle className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
                允许使用你的位置吗？
              </DialogTitle>
              <DialogDescription className="mt-2.5 text-sm leading-relaxed text-muted-foreground sm:text-[15px]">
                我们将通过设备定位获取你所在的省份，用于自动匹配当地
                <span className="whitespace-nowrap font-medium text-primary">实时油价</span>
                ，让应付金额自动计算更准确。
              </DialogDescription>
            </DialogHeader>

            <div className="mt-6 space-y-3">
              <div className="flex items-start gap-3 rounded-2xl bg-surface-2/60 p-3.5 ring-1 ring-border">
                <ShieldCheckIcon className="mt-0.5 size-5 shrink-0 text-primary" />
                <div className="text-xs leading-relaxed text-muted-foreground sm:text-sm">
                  <div className="font-medium text-foreground">仅用于油价计算</div>
                  <div className="mt-0.5 text-foreground/70">
                    位置信息只在本地使用，不会上传或与你的加油记录关联存储
                  </div>
                </div>
              </div>

              {isLocating && showingBrowserPrompt && (
                <div className="flex items-center gap-3 rounded-2xl bg-muted-foreground/5 p-3.5 ring-1 ring-muted-foreground/10">
                  <Loader2Icon className="size-5 animate-spin text-primary" />
                  <div className="text-sm font-medium text-foreground">
                    正在获取位置…
                    <div className="mt-0.5 text-xs font-normal text-muted-foreground">
                      请在浏览器弹窗中点击「允许」
                    </div>
                  </div>
                </div>
              )}

              {locateError && !isLocating && (
                <div className="flex items-start gap-3 rounded-2xl bg-warning/5 p-3.5 ring-1 ring-warning/20">
                  <XCircleIcon className="mt-0.5 size-5 shrink-0 text-warning" />
                  <div className="text-xs leading-relaxed sm:text-sm">
                    <div className="font-medium text-warning-foreground">
                      {locateError}
                    </div>
                    <div className="mt-0.5 text-muted-foreground">
                      已降级为 IP 归属地，你也可以稍后手动选择省份
                    </div>
                  </div>
                </div>
              )}
            </div>

            <div className="mt-7 flex flex-col gap-3 sm:flex-row-reverse">
              <Button
                onClick={handleAllow}
                disabled={isLocating}
                className="h-12 flex-1 rounded-full text-base font-medium shadow-lg shadow-black/25 transition-all duration-300 gradient-primary text-white"
              >
                {isLocating ? (
                  <>
                    <Loader2Icon className="animate-spin" />
                    定位中…
                  </>
                ) : (
                  <>
                    <MapPinIcon className="size-4.5" />
                    允许定位
                  </>
                )}
              </Button>
              <Button
                variant="outline"
                onClick={onDeny}
                disabled={isLocating}
                className="h-12 flex-1 rounded-full text-base font-medium border-border-light bg-[var(--glass-bg-strong)] text-foreground-secondary"
              >
                暂不允许
              </Button>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

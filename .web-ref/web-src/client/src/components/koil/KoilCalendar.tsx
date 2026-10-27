/**
 * KoilCalendar —— KOiL 应用自研日历组件（成熟版）
 *
 * 设计目标（吸取此前补录日历反复出 bug 的教训）：
 * 1. 零第三方依赖：不依赖 react-day-picker / Radix Popover / Portal；
 * 2. 内联渲染：组件按"内容区流内展开"设计（打开即出现在触发字段下方），
 *    不做绝对定位 / fixed / Portal，从根上消除定位、焦点陷阱、滚动容器、
 *    触控冲突这一类问题；
 * 3. 交互统一：日期单元格用原生 onClick（React 合成事件对鼠标与触摸一致生效），
 *    无需 touch 专属逻辑；热区 ≥ 40px；
 * 4. 主题走 CSS 变量：颜色全部用 var(--xxx)，与应用
 *    `:root / [data-theme=dark]` token 体系天然融合，无需 dark: 类；
 * 5. i18n：locale 由调用方传入（应用 i18n 的 zh-CN / zh-TW / en）；
 * 6. 禁用语义：min/max 按 YYYY-MM-DD 字符串比较（避免时区边界），
 *    max 传今天字符串即"未来禁用"（补录语义）。
 *
 * 并入方式：文件放入 client/src/components/ui/KoilCalendar.tsx，
 * 在补录弹窗（或任何需要日期选择的表单）内以"流内展开"方式使用：
 *
 * ```tsx
 * const [open, setOpen] = useState(false);
 * const [date, setDate] = useState(todayStr());
 * ...
 * <div>
 *   <DateFieldButton onClick={() => setOpen(o => !o)} value={date} />
 *   {open && (
 *     <KoilCalendar
 *       value={date}
 *       onChange={(d) => { setDate(d); setOpen(false); }}
 *       max={todayStr()}
 *       locale={locale}
 *     />
 *   )}
 * </div>
 * ```
 * 注意：组件自身不负责"点击外部关闭"——由调用方在弹窗/Dialog 关闭逻辑
 * 中统一处理（点外部关闭弹窗即收起日历）。
 */

import { useEffect, useMemo, useRef, useState } from 'react';

export type KoilCalendarLocale = 'zh-CN' | 'zh-TW' | 'en';

export interface KoilCalendarProps {
  /** 当前选中日期 YYYY-MM-DD，可为空串 */
  value?: string;
  onChange?: (date: string) => void;
  /** 最小可选日期 YYYY-MM-DD（可为空） */
  min?: string;
  /** 最大可选日期 YYYY-MM-DD（补录场景传"今天"） */
  max?: string;
  locale?: KoilCalendarLocale;
  className?: string;
  /** 背景样式：glass=玻璃态（默认），solid=实色不透明 */
  variant?: 'glass' | 'solid';
}

interface YMD { y: number; m: number; d: number }

const LABELS: Record<KoilCalendarLocale, { week: string[]; month: (y: number, m: number) => string; today: string }> = {
  'zh-CN': { week: ['日', '一', '二', '三', '四', '五', '六'], month: (y, m) => `${y}年${m + 1}月`, today: '今天' },
  'zh-TW': { week: ['日', '一', '二', '三', '四', '五', '六'], month: (y, m) => `${y}年${m + 1}月`, today: '今天' },
  'en':    { week: ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'], month: (y, m) => `${m + 1} / ${y}`, today: 'Today' },
};

const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`);
const todayStr = () => {
  const t = new Date();
  return `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())}`;
};
const parseYMD = (s: string): YMD | null => {
  if (!s) return null;
  const p = s.split('-').map(Number);
  if (p.length !== 3 || p.some(Number.isNaN)) return null;
  return { y: p[0], m: p[1] - 1, d: p[2] };
};
const fmt = (d: YMD) => `${d.y}-${pad(d.m + 1)}-${pad(d.d)}`;
const daysInMonth = (y: number, m: number) => new Date(y, m + 1, 0).getDate();
const firstWeekday = (y: number, m: number) => new Date(y, m, 1).getDay();
const isSame = (a: YMD, b: YMD) => a.y === b.y && a.m === b.m && a.d === b.d;
/** -1 / 0 / 1：字符串比较天然规避时区边界 */
const cmp = (a: YMD, b: YMD) => {
  const va = `${a.y}-${pad(a.m + 1)}-${pad(a.d)}`;
  const vb = `${b.y}-${pad(b.m + 1)}-${pad(b.d)}`;
  return va < vb ? -1 : va > vb ? 1 : 0;
};

export function KoilCalendar({ value = '', onChange, min, max, locale = 'zh-CN', className = '', variant = 'glass' }: KoilCalendarProps) {
  const sel = useMemo(() => parseYMD(value), [value]);
  const [view, setView] = useState<YMD>(() => sel ?? parseYMD(todayStr())!);
  const viewRef = useRef<YMD>(view);
  viewRef.current = view;
  const t = LABELS[locale];
  const minYmd = useMemo(() => parseYMD(min ?? ''), [min]);
  const maxYmd = useMemo(() => parseYMD(max ?? ''), [max]);

  const rootRef = useRef<HTMLDivElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const bgRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef({ on: false, sx: 0, sy: 0, dx: 0, dy: 0, moved: false, v: 0, lastY: 0, lastT: 0 });
  const suppressClickRef = useRef(false);
  const navRef = useRef({ prev: true, next: true });
  const bgTargetRef = useRef<YMD | null>(null);
  const [bgMonth, setBgMonth] = useState<YMD | null>(null);
  const THRESH = 55;

  const isDisabled = (d: YMD) => {
    if (minYmd && cmp(d, minYmd) < 0) return true;
    if (maxYmd && cmp(d, maxYmd) > 0) return true;
    return false;
  };

  const pick = (d: YMD) => {
    if (isDisabled(d)) return;
    onChange?.(fmt(d));
    setView(d);
  };

  const shift = (d: YMD, delta: number): YMD => {
    let { y, m } = d;
    m += delta;
    if (m < 0) { m = 11; y -= 1; }
    if (m > 11) { m = 0; y += 1; }
    return { y, m, d: 1 };
  };
  const goMonth = (delta: number) => {
    setView((prev) => {
      const nv = shift(prev, delta);
      if (minYmd && (nv.y < minYmd.y || (nv.y === minYmd.y && nv.m < minYmd.m))) return prev;
      if (maxYmd && (nv.y > maxYmd.y || (nv.y === maxYmd.y && nv.m > maxYmd.m))) return prev;
      return nv;
    });
  };

  /* ===== 拖动切月（双卡轮播：当前月跟手，相邻月从对面同步露出；无悬浮空白）===== */
  const clamp = (v: number, a: number, b: number) => (v < a ? a : v > b ? b : v);
  const onDragMove = (e: MouseEvent) => {
    const d = dragRef.current;
    if (!d.on) return;
    e.preventDefault();
    const now = Date.now();
    const ny = e.clientY - d.sy;
    const dt = now - d.lastT;
    if (dt > 0) {
      const inst = (ny - d.lastY) / dt; // px/ms
      d.v = d.v * 0.65 + inst * 0.35;    // 平滑速度
    }
    d.lastY = ny; d.lastT = now;
    d.dy = ny;
    if (Math.abs(ny) > 6) d.moved = true;
    const g = gridRef.current, bg = bgRef.current;
    if (!g || !bg) return;
    g.style.transition = 'none';
    bg.style.transition = 'none';
    const dir = ny < 0 ? 1 : -1;            // 上拖=下月(+1)，下拖=上月(-1)
    const canGo = dir > 0 ? navRef.current.next : navRef.current.prev;
    if (canGo) {
      const H = g.offsetHeight || 280;
      const tv = shift(viewRef.current, dir);
      if (!bgTargetRef.current || bgTargetRef.current.y !== tv.y || bgTargetRef.current.m !== tv.m) {
        setBgMonth(tv);
        bgTargetRef.current = tv;
      }
      bg.style.visibility = 'visible';
      bg.style.transform = `translate(0, ${dir > 0 ? H + ny : -H + ny}px)`;
      g.style.transform = `translate(0, ${ny}px)`;
      g.style.opacity = '1';
    } else {
      // 边界橡皮筋：无相邻月可露，当前月轻微阻尼回弹反馈
      bg.style.visibility = 'hidden';
      const travel = clamp(ny * 0.25, -70, 70);
      g.style.transform = `translate(0, ${travel}px)`;
      g.style.opacity = String(Math.max(0.5, 1 - Math.abs(travel) / 200));
    }
  };
  const endDrag = (e?: MouseEvent) => {
    const d = dragRef.current;
    if (!d.on) return;
    d.on = false;
    const root = rootRef.current;
    if (root) root.style.cursor = '';
    document.removeEventListener('mousemove', onDragMove as unknown as EventListener);
    document.removeEventListener('mouseup', endDrag as unknown as EventListener);
    const g = gridRef.current, bg = bgRef.current;
    const fling = clamp(d.v * 200, -140, 140); // 惯性滑行估计
    const total = d.dy + fling;
    let delta = 0;
    if (Math.abs(total) >= 60 || Math.abs(d.dy) >= THRESH) delta = total > 0 ? -1 : 1;
    // 边界拦截：该方向不可切时一律回弹
    if (delta < 0 && !navRef.current.prev) delta = 0; // 上月方向
    if (delta > 0 && !navRef.current.next) delta = 0; // 下月方向
    suppressClickRef.current = d.moved;
    const EASE = 'cubic-bezier(.22,1,.36,1)';
    if (g && bg) {
      if (delta !== 0) {
        const H = g.offsetHeight || 280;
        const dur = clamp(260 + Math.abs(d.v) * 0.5, 280, 460); // 速度越快滑得越久
        g.style.transition = `transform ${dur}ms ${EASE}, opacity ${dur * 0.8}ms ease`;
        g.style.transform = `translate(0, ${delta > 0 ? -H : H}px)`; // 当前月滑出
        g.style.opacity = '0';
        bg.style.transition = `transform ${dur}ms ${EASE}`;
        bg.style.transform = 'translate(0, 0)';                 // 相邻月滑到原位
        window.setTimeout(() => {
          goMonth(delta);                    // 真正切换，重绘当前网格
          g.style.transition = 'none';
          g.style.transform = ''; g.style.opacity = '1';
          bg.style.visibility = 'hidden'; bg.style.transition = 'none'; bg.style.transform = '';
          setBgMonth(null); bgTargetRef.current = null;
        }, dur + 40);
      } else {
        // 不足阈值 / 到边界：带回弹的阻尼回位
        g.style.transition = 'transform .32s cubic-bezier(.34,1.56,.64,1), opacity .28s ease';
        g.style.transform = ''; g.style.opacity = '1';
        bg.style.visibility = 'hidden'; bg.style.transition = 'none'; bg.style.transform = '';
      }
    }
    d.dx = 0; d.dy = 0; d.v = 0; d.lastY = 0; d.lastT = 0;
  };
  const onMouseDown = (e: MouseEvent) => {
    e.stopPropagation();
    if ((e.target as HTMLElement).closest('[data-cal-ctrl]')) return;
    const target = e.currentTarget as HTMLDivElement;
    const d = dragRef.current;
    d.on = true; d.sx = e.clientX; d.sy = e.clientY; d.dx = 0; d.dy = 0;
    d.moved = false; d.v = 0; d.lastY = 0; d.lastT = Date.now();
    target.style.cursor = 'grabbing';
    document.addEventListener('mousemove', onDragMove as unknown as EventListener);
    document.addEventListener('mouseup', endDrag as unknown as EventListener);
  };

  const onTouchStart = (e: TouchEvent) => {
    e.stopPropagation();
    if ((e.target as HTMLElement).closest('[data-cal-ctrl]')) return;
    const t = e.touches[0];
    if (!t) return;
    const d = dragRef.current;
    d.on = true; d.sx = t.clientX; d.sy = t.clientY; d.dx = 0; d.dy = 0;
    d.moved = false; d.v = 0; d.lastY = 0; d.lastT = Date.now();
    document.addEventListener('touchmove', onTouchMove as unknown as EventListener, { passive: false });
    document.addEventListener('touchend', onTouchEnd as unknown as EventListener);
    document.addEventListener('touchcancel', onTouchEnd as unknown as EventListener);
  };
  const onTouchMove = (e: TouchEvent) => {
    const d = dragRef.current;
    if (!d.on) return;
    e.preventDefault();
    const t = e.touches[0];
    if (!t) return;
    const now = Date.now();
    const ny = t.clientY - d.sy;
    const dt = now - d.lastT;
    if (dt > 0) {
      const inst = (ny - d.lastY) / dt;
      d.v = d.v * 0.65 + inst * 0.35;
    }
    d.lastY = ny; d.lastT = now;
    d.dy = ny;
    if (Math.abs(ny) > 6) d.moved = true;
    const g = gridRef.current, bg = bgRef.current;
    if (!g || !bg) return;
    g.style.transition = 'none';
    bg.style.transition = 'none';
    const dir = ny < 0 ? 1 : -1;
    const canGo = dir > 0 ? navRef.current.next : navRef.current.prev;
    if (canGo) {
      const H = g.offsetHeight || 280;
      const tv = shift(viewRef.current, dir);
      if (!bgTargetRef.current || bgTargetRef.current.y !== tv.y || bgTargetRef.current.m !== tv.m) {
        setBgMonth(tv);
        bgTargetRef.current = tv;
      }
      bg.style.visibility = 'visible';
      bg.style.transform = `translate(0, ${dir > 0 ? H + ny : -H + ny}px)`;
      g.style.transform = `translate(0, ${ny}px)`;
      g.style.opacity = '1';
    } else {
      bg.style.visibility = 'hidden';
      const travel = clamp(ny * 0.25, -70, 70);
      g.style.transform = `translate(0, ${travel}px)`;
      g.style.opacity = String(Math.max(0.5, 1 - Math.abs(travel) / 200));
    }
  };
  const onTouchEnd = () => {
    const d = dragRef.current;
    if (!d.on) return;
    d.on = false;
    document.removeEventListener('touchmove', onTouchMove as unknown as EventListener);
    document.removeEventListener('touchend', onTouchEnd as unknown as EventListener);
    document.removeEventListener('touchcancel', onTouchEnd as unknown as EventListener);
    const g = gridRef.current, bg = bgRef.current;
    const fling = clamp(d.v * 200, -140, 140);
    const total = d.dy + fling;
    let delta = 0;
    if (Math.abs(total) >= 60 || Math.abs(d.dy) >= THRESH) delta = total > 0 ? -1 : 1;
    if (delta < 0 && !navRef.current.prev) delta = 0;
    if (delta > 0 && !navRef.current.next) delta = 0;
    suppressClickRef.current = d.moved;
    const EASE = 'cubic-bezier(.22,1,.36,1)';
    if (g && bg) {
      if (delta !== 0) {
        const H = g.offsetHeight || 280;
        const dur = clamp(260 + Math.abs(d.v) * 0.5, 280, 460);
        g.style.transition = `transform ${dur}ms ${EASE}, opacity ${dur * 0.8}ms ease`;
        g.style.transform = `translate(0, ${delta > 0 ? -H : H}px)`;
        g.style.opacity = '0';
        bg.style.transition = `transform ${dur}ms ${EASE}`;
        bg.style.transform = 'translate(0, 0)';
        window.setTimeout(() => {
          goMonth(delta);
          g.style.transition = 'none';
          g.style.transform = ''; g.style.opacity = '1';
          bg.style.visibility = 'hidden'; bg.style.transition = 'none'; bg.style.transform = '';
          setBgMonth(null); bgTargetRef.current = null;
        }, dur + 40);
      } else {
        g.style.transition = 'transform .32s cubic-bezier(.34,1.56,.64,1), opacity .28s ease';
        g.style.transform = ''; g.style.opacity = '1';
        bg.style.visibility = 'hidden'; bg.style.transition = 'none'; bg.style.transform = '';
      }
    }
    d.dx = 0; d.dy = 0; d.v = 0; d.lastY = 0; d.lastT = 0;
  };

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;
    root.addEventListener('mousedown', onMouseDown as unknown as EventListener);
    root.addEventListener('touchstart', onTouchStart as unknown as EventListener, { passive: false });
    return () => {
      root.removeEventListener('mousedown', onMouseDown as unknown as EventListener);
      root.removeEventListener('touchstart', onTouchStart as unknown as EventListener);
      document.removeEventListener('mousemove', onDragMove as unknown as EventListener);
      document.removeEventListener('mouseup', endDrag as unknown as EventListener);
      document.removeEventListener('touchmove', onTouchMove as unknown as EventListener);
      document.removeEventListener('touchend', onTouchEnd as unknown as EventListener);
      document.removeEventListener('touchcancel', onTouchEnd as unknown as EventListener);
    };
  }, []);

  // 网格构建：上月尾部 + 当月 + 下月头部（凑满整行，非当月日期灰显）；任意月可渲染
  const buildCells = (y: number, m: number): YMD[] => {
    const total = daysInMonth(y, m);
    const offset = firstWeekday(y, m);
    const prevY = m === 0 ? y - 1 : y;
    const prevM = m === 0 ? 11 : m - 1;
    const prevTotal = daysInMonth(prevY, prevM);
    const nextY = m === 11 ? y + 1 : y;
    const nextM = m === 11 ? 0 : m + 1;
    const rows = Math.ceil((offset + total) / 7); // 自然行数：通常 5 行，装不下才 6 行
    const tailLen = rows * 7 - (offset + total);
    const head = Array.from({ length: offset }, (_, i) => ({ y: prevY, m: prevM, d: prevTotal - offset + 1 + i }));
    const body = Array.from({ length: total }, (_, i) => ({ y, m, d: i + 1 }));
    const tail = Array.from({ length: tailLen }, (_, i) => ({ y: nextY, m: nextM, d: i + 1 }));
    return [...head, ...body, ...tail];
  };
  const today = parseYMD(todayStr())!;
  const canPrev = !(minYmd && view.y === minYmd.y && view.m <= minYmd.m);
  const canNext = !(maxYmd && view.y === maxYmd.y && view.m >= maxYmd.m);
  navRef.current = { prev: canPrev, next: canNext };

  const cells = buildCells(view.y, view.m);
  const bgCells = bgMonth ? buildCells(bgMonth.y, bgMonth.m) : [];

  const renderDay = (d: YMD, cur: YMD, key: string) => {
    const ghost = d.y !== cur.y || d.m !== cur.m;
    const dis = isDisabled(d);
    const isSel = !!sel && isSame(d, sel);
    const isToday = isSame(d, today);
    const cellStyle: React.CSSProperties = {
      ...dayBtnStyle,
      color: dis
        ? 'var(--text-disabled, rgba(128,128,128,.35))'
        : isSel
          ? 'var(--on-primary, #fff)'
          : ghost
            ? 'var(--text-faint)'
            : 'var(--text)',
      background: isSel ? 'var(--primary)' : 'transparent',
      opacity: ghost && !isSel ? 0.55 : undefined,
    };
    return (
      <button
        key={key}
        type="button"
        data-testid="koil-cal-day"
        className="koil-cal-day"
        disabled={dis}
        onClick={() => {
          if (suppressClickRef.current) { suppressClickRef.current = false; return; }
          pick(d);
        }}
        aria-label={`${t.month(d.y, d.m)} ${d.d}`}
        aria-pressed={isSel}
        style={cellStyle}
      >
        {d.d}
        {isToday && (
          <span style={{ position: 'absolute', left: '50%', bottom: 5, width: 5, height: 5, borderRadius: '50%', background: isSel ? 'var(--on-primary, #fff)' : 'var(--primary)', transform: 'translateX(-50%)' }} />
        )}
      </button>
    );
  };

  return (
    <div
      ref={rootRef}
      className={`koil-cal koil-cal--${variant} ${className}`}
      style={{
        borderRadius: 20,
        padding: 'clamp(10px, 3.5%, 18px) clamp(10px, 3.2%, 16px) clamp(8px, 2.5%, 14px)',
        width: '100%',
        height: 'auto',
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        touchAction: 'none',
        userSelect: 'none',
        cursor: 'grab',
      }}
    >
      {/* 头部：上月 / 标题 / 下月 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <button
          type="button"
          aria-label="previous month"
          data-cal-ctrl="1"
          disabled={!canPrev}
          onClick={() => goMonth(-1)}
          style={navBtnStyle}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 18l-6-6 6-6" /></svg>
        </button>
        <span style={{ fontSize: 14, fontWeight: 600, minWidth: 96, textAlign: 'center' }}>
          {t.month(view.y, view.m)}
        </span>
        <button
          type="button"
          aria-label="next month"
          data-cal-ctrl="1"
          disabled={!canNext}
          onClick={() => goMonth(1)}
          style={navBtnStyle}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 6l6 6-6 6" /></svg>
        </button>
      </div>

      {/* 星期头 */}
      <div style={gridStyle}>
        {t.week.map((w) => (
          <span key={w} style={{ textAlign: 'center', fontSize: 11, color: 'var(--text-faint)', height: 22, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {w}
          </span>
        ))}
      </div>

      {/* 日期网格（双卡轮播：当前层 + 相邻月预览层，拖动时同步移动） */}
      <div style={{ position: 'relative', overflow: 'hidden', width: '100%' }}>
        <div ref={gridRef} style={gridStyle}>
          {cells.map((d, i) => renderDay(d, view, `d${i}`))}
        </div>
        <div
          ref={bgRef}
          aria-hidden="true"
          style={{ ...gridStyle, position: 'absolute', inset: 0, visibility: 'hidden', pointerEvents: 'none' }}
        >
          {bgMonth ? bgCells.map((d, i) => renderDay(d, bgMonth, `b${i}`)) : null}
        </div>
      </div>

      {/* 底部：今天快捷 */}
      <div style={{ display: 'flex', justifyContent: 'flex-start', paddingTop: 2 }}>
        <button
          type="button"
          data-cal-ctrl="1"
          onClick={() => pick(parseYMD(todayStr())!)}
          style={{
            border: 'none',
            background: 'var(--primary-soft)',
            color: 'var(--primary-strong)',
            fontSize: 12,
            fontWeight: 600,
            padding: '8px 14px',
            borderRadius: 999,
            cursor: 'pointer',
            minHeight: 34,
          }}
        >
          {t.today}
        </button>
      </div>
    </div>
  );
}

const gridStyle: React.CSSProperties = { display: 'grid', gridTemplateColumns: 'repeat(7, minmax(0, 1fr))', gap: 2, width: '100%' };
const navBtnStyle: React.CSSProperties = {
  width: 36, height: 36, borderRadius: 12,
  border: '1px solid var(--border-light, var(--border))',
  background: 'transparent', color: 'var(--text-sub)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  cursor: 'pointer',
};
const dayBtnStyle: React.CSSProperties = {
  position: 'relative',
  width: '100%', minWidth: 0, aspectRatio: '1 / 1', borderRadius: '50%', border: 'none',
  fontSize: 13.5, fontVariantNumeric: 'tabular-nums',
  cursor: 'pointer', touchAction: 'manipulation',
  transition: 'background .12s, color .12s',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  lineHeight: 1,
};

export default KoilCalendar;

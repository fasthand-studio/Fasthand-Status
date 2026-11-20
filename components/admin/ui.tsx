import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

const fieldBase =
  'w-full border border-line bg-surface px-2.5 text-[15px] text-ink placeholder:text-muted/70 focus:border-primary focus:outline-none';

export const adminInputClass = cn(fieldBase, 'fh-field');

export const adminSelectClass = cn(adminInputClass, 'appearance-none pr-10');

export const adminTextareaClass = cn(
  fieldBase,
  'fh-field-area min-h-[7rem] py-3 leading-relaxed',
);

export const adminLabelClass = 'flex flex-wrap items-baseline gap-1.5 text-[15px] font-medium text-ink';

export const adminHintClass = 'mt-1 text-[14px] leading-relaxed text-muted';

export const adminCardClass = 'fh-card bg-surface';

export const primaryButtonClass =
  'fh-btn bg-primary text-white transition-opacity hover:opacity-85 disabled:cursor-not-allowed disabled:opacity-50';

// Rule 9: one tinted style for secondary actions; plain styling for the rest.
export const secondaryButtonClass =
  'fh-btn bg-primary/10 text-primary transition-opacity hover:opacity-85 disabled:cursor-not-allowed disabled:opacity-50';

export const smallButtonClass =
  'fh-btn bg-primary/10 text-primary transition-opacity hover:opacity-85 disabled:cursor-not-allowed disabled:opacity-50';

export const dangerButtonClass =
  'fh-btn text-bad transition-opacity hover:opacity-70 disabled:cursor-not-allowed disabled:opacity-50';

export const dangerSolidButtonClass =
  'fh-btn bg-bad text-white transition-opacity hover:opacity-85 disabled:cursor-not-allowed disabled:opacity-50';

export const linkClass =
  'text-primary underline decoration-primary/40 underline-offset-2 hover:decoration-primary';

export function Field({
  label,
  htmlFor,
  hint,
  required = false,
  className,
  children,
}: {
  label: string;
  htmlFor?: string;
  hint?: string;
  required?: boolean;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={htmlFor} className={adminLabelClass}>
        <span>{label}</span>
        <span className={cn('text-[14px] font-normal', required ? 'text-ink/70' : 'text-muted')}>
          {required ? 'Required' : 'Optional'}
        </span>
      </label>
      {children}
      {hint ? <p className={adminHintClass}>{hint}</p> : null}
    </div>
  );
}

export function Notice({
  tone = 'info',
  children,
  className,
}: {
  tone?: 'info' | 'ok' | 'warn' | 'bad';
  children: ReactNode;
  className?: string;
}) {
  const tones = {
    info: 'bg-info-soft',
    ok: 'bg-ok-soft',
    warn: 'bg-warn-soft',
    bad: 'bg-bad-soft',
  } as const;

  return (
    <div
      role={tone === 'bad' ? 'alert' : 'status'}
      className={cn(
        'fh-r-sm px-3 py-2 text-[15px] leading-relaxed text-ink/80',
        tones[tone],
        className,
      )}
    >
      {children}
    </div>
  );
}

export function AdminPageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div className="max-w-2xl">
        <h1 className="text-[20px] font-semibold tracking-[-0.02em] text-ink">{title}</h1>
        {description ? (
          <p className="mt-1 text-[15px] leading-relaxed text-muted">{description}</p>
        ) : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export function StatCard({
  label,
  value,
  detail,
  tone = 'neutral',
}: {
  label: string;
  value: ReactNode;
  detail?: ReactNode;
  tone?: 'neutral' | 'ok' | 'warn' | 'bad';
}) {
  const tones = {
    neutral: 'text-ink',
    ok: 'text-ok',
    warn: 'text-warn',
    bad: 'text-bad',
  } as const;
  return (
    <div className={cn(adminCardClass, 'px-3 py-2.5')}>
      <p className="text-[14px] text-muted">{label}</p>
      <p className={cn('fh-num mt-0.5 text-[20px] font-semibold tracking-[-0.02em]', tones[tone])}>
        {value}
      </p>
      {detail ? <p className="mt-0.5 text-[14px] leading-relaxed text-muted">{detail}</p> : null}
    </div>
  );
}

const STATUS_TONES = {
  investigating: 'bg-bad-soft text-bad',
  identified: 'bg-warn-soft text-warn',
  monitoring: 'bg-info-soft text-info',
  resolved: 'bg-ok-soft text-ok',
} as const;

const IMPACT_TONES = {
  none: 'bg-surface-soft text-muted',
  minor: 'bg-info-soft text-info',
  major: 'bg-warn-soft text-warn',
  critical: 'bg-bad-soft text-bad',
} as const;

const badgeClass = 'fh-pill font-medium';

export function AdminStatusBadge({ status }: { status: keyof typeof STATUS_TONES }) {
  const labels = {
    investigating: 'Investigating',
    identified: 'Identified',
    monitoring: 'Monitoring',
    resolved: 'Resolved',
  } as const;
  return <span className={cn(badgeClass, STATUS_TONES[status])}>{labels[status]}</span>;
}

export function AdminImpactBadge({ impact }: { impact: keyof typeof IMPACT_TONES }) {
  const labels = {
    none: 'No impact',
    minor: 'Minor',
    major: 'Major',
    critical: 'Critical',
  } as const;
  return <span className={cn(badgeClass, IMPACT_TONES[impact])}>{labels[impact]}</span>;
}

export function AdminMaintenanceBadge({ status }: { status: 'scheduled' | 'in_progress' | 'completed' | 'cancelled' }) {
  const labels = {
    scheduled: 'Scheduled',
    in_progress: 'In progress',
    completed: 'Completed',
    cancelled: 'Cancelled',
  } as const;
  const tones = {
    scheduled: IMPACT_TONES.minor,
    in_progress: IMPACT_TONES.major,
    completed: IMPACT_TONES.none,
    cancelled: IMPACT_TONES.none,
  } as const;
  return <span className={cn(badgeClass, tones[status])}>{labels[status]}</span>;
}

export function EmptyAdminState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className={cn(adminCardClass, 'px-4 py-6 text-center')}>
      <p className="text-[17px] font-semibold text-ink">{title}</p>
      {children ? <div className="mt-1 text-[15px] leading-relaxed text-muted">{children}</div> : null}
    </div>
  );
}

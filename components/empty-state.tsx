import { cn } from '@/lib/cn';
import { InboxIcon } from './icons';

export function EmptyState({
  title,
  description,
  className,
}: {
  title: string;
  description: string;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center fh-card bg-surface-soft px-5 py-8 text-center sm:px-6 sm:py-10',
        className,
      )}
    >
      <InboxIcon className="h-6 w-6 text-muted" />
      <p className="mt-2.5 text-[17px] font-semibold">{title}</p>
      <p className="mt-1 max-w-sm text-[14px] leading-relaxed text-muted">{description}</p>
    </div>
  );
}

import { formatDateTime } from '@/lib/format';
import type { IncidentUpdate } from '@/lib/types';
import { IncidentStatusBadge } from './incident-status-badge';
import { Markdown } from './markdown';

export function IncidentTimeline({ updates }: { updates: IncidentUpdate[] }) {
  if (updates.length === 0) {
    return (
      <p className="fh-card bg-surface-soft px-4 py-5 text-center text-[15px] text-muted">
        No public updates have been posted yet.
      </p>
    );
  }

  return (
    <ol className="relative space-y-5">
      {updates.map((update) => (
        <li key={update.id} className="relative pl-5">
          <span
            className="absolute left-0 top-1.5 fh-dot bg-ink"
            aria-hidden="true"
          />
          <div className="flex flex-wrap items-center gap-2">
            <IncidentStatusBadge status={update.status} size="sm" />
            <span className="text-[14px] text-muted">
              {update.author === 'Automated monitoring' ? 'Automated monitoring' : 'FastHand team'}
            </span>
            <time dateTime={update.created_at} className="fh-num text-[14px] text-muted/80">
              {formatDateTime(update.created_at)}
            </time>
          </div>
          <Markdown className="mt-1.5 max-w-3xl">{update.body}</Markdown>
        </li>
      ))}
    </ol>
  );
}

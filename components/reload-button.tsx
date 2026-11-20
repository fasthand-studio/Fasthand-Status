'use client';

export function ReloadButton({ className }: { className?: string }) {
  return (
    <button
      type="button"
      onClick={() => window.location.reload()}
      className={className ?? 'fh-btn bg-primary/10 text-primary transition-opacity hover:opacity-85'}
    >
      Reload
    </button>
  );
}

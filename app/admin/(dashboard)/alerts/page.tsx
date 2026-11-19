import type { Metadata } from 'next';
import { requireAdminContext } from '@/lib/auth';
import { createServiceClient } from '@/lib/supabase/admin';
import { AdminPageHeader, adminCardClass, adminHintClass, adminInputClass, Field, Notice } from '@/components/admin/ui';
import { ActionForm } from '@/components/admin/action-form';
import { SubmitButton } from '@/components/admin/submit-button';
import { saveAlertKeyAction, sendIncidentAlertAction, sendMaintenanceAlertAction } from '../../actions-alerts';
import { FASTY_ALERT_EMAIL } from '@/lib/fast-notifications';

export const metadata: Metadata = { title: 'Fasty Alert — FastHand Ops' };

export default async function AlertSettingsPage() {
  const context = await requireAdminContext();
  const { data: activeIncidents } = await context.supabase
    .from('incidents')
    .select('id, title, impact, started_at')
    .is('resolved_at', null)
    .order('started_at', { ascending: false })
    .limit(20);
  const { data: activeMaintenance } = await context.supabase
    .from('maintenance_windows')
    .select('id, title, status')
    .in('status', ['scheduled', 'in_progress'])
    .order('scheduled_start', { ascending: true })
    .limit(20);
  const { data } = await createServiceClient()
    .from('fasty_alert_settings')
    .select('api_key')
    .eq('id', true)
    .maybeSingle();
  const configured = Boolean((data?.api_key as string | undefined)?.trim());

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <AdminPageHeader title="Fasty Alert" description="Send a warning when monitoring opens an outage and a recovery notice when it resolves." />
      <Notice tone={configured ? 'ok' : 'warn'}>
        {configured ? 'A notification key is saved.' : 'No notification key is saved yet. Apply migration 0005, then add the key here.'}
      </Notice>
      <ActionForm action={saveAlertKeyAction} className={`${adminCardClass} px-3 py-3 sm:p-4`}>
        <Field
          label="Fast Notifications API key"
          htmlFor="api_key"
          required
          hint="Stored in a server-only Supabase table. It is never sent to the Fasty Alert Android app. Saving a new key replaces the current one."
        >
          <input id="api_key" name="api_key" type="password" autoComplete="new-password" required maxLength={512} className={adminInputClass} />
        </Field>
        <p className={adminHintClass}>Notifications are sent to {FASTY_ALERT_EMAIL}. The Android app must register at least one device for this address.</p>
        <SubmitButton>Save API key</SubmitButton>
      </ActionForm>
      <section className="space-y-3">
        <div>
          <h2 className="text-[18px] font-semibold text-ink">Active incidents</h2>
          <p className="mt-1 text-[14px] text-muted">Send a catch-up alert to the app for an incident that was opened before notification delivery was configured.</p>
        </div>
        {activeIncidents?.length ? activeIncidents.map((incident) => (
          <ActionForm key={incident.id} action={sendIncidentAlertAction} className={`${adminCardClass} flex flex-wrap items-center justify-between gap-3 px-3 py-2`}>
            <input type="hidden" name="incident_id" value={incident.id} />
            <div className="min-w-0">
              <p className="font-medium text-ink">{incident.title}</p>
              <p className="mt-0.5 text-[14px] capitalize text-muted">{incident.impact} impact</p>
            </div>
            <SubmitButton>Send alert</SubmitButton>
          </ActionForm>
        )) : <Notice>No active incidents to send.</Notice>}
      </section>
      <section className="space-y-3">
        <div>
          <h2 className="text-[18px] font-semibold text-ink">Scheduled and active maintenance</h2>
          <p className="mt-1 text-[14px] text-muted">Send a catch-up alert for a maintenance window created before notification delivery was configured.</p>
        </div>
        {activeMaintenance?.length ? activeMaintenance.map((window) => (
          <ActionForm key={window.id} action={sendMaintenanceAlertAction} className={`${adminCardClass} flex flex-wrap items-center justify-between gap-3 px-3 py-2`}>
            <input type="hidden" name="maintenance_id" value={window.id} />
            <div className="min-w-0">
              <p className="font-medium text-ink">{window.title}</p>
              <p className="mt-0.5 text-[14px] capitalize text-muted">{window.status === 'in_progress' ? 'In progress' : 'Scheduled'}</p>
            </div>
            <SubmitButton>Send alert</SubmitButton>
          </ActionForm>
        )) : <Notice>No scheduled or active maintenance windows.</Notice>}
      </section>
    </div>
  );
}

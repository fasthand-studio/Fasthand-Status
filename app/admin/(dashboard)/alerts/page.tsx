import type { Metadata } from 'next';
import { requireAdminContext } from '@/lib/auth';
import { createServiceClient } from '@/lib/supabase/admin';
import { AdminPageHeader, adminCardClass, adminHintClass, adminInputClass, Field, Notice } from '@/components/admin/ui';
import { ActionForm } from '@/components/admin/action-form';
import { SubmitButton } from '@/components/admin/submit-button';
import { saveAlertKeyAction } from '../../actions-alerts';
import { FASTY_ALERT_EMAIL } from '@/lib/fast-notifications';

export const metadata: Metadata = { title: 'Fasty Alert — FastHand Ops' };

export default async function AlertSettingsPage() {
  await requireAdminContext();
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
    </div>
  );
}

'use server';

import { revalidatePath } from 'next/cache';
import { getAdminContext } from '@/lib/auth';
import { failed, succeeded, type ActionState } from '@/lib/action-state';
import { createServiceClient } from '@/lib/supabase/admin';
import { sendFastyAlert } from '@/lib/fast-notifications';
import { formText, isUuid } from '@/lib/validation';

export async function saveAlertKeyAction(_state: ActionState, formData: FormData): Promise<ActionState> {
  const context = await getAdminContext();
  if (context.status !== 'admin') return failed('Your admin session expired. Sign in again at /admin/login.');
  const apiKey = formText(formData, 'api_key').trim();
  if (!apiKey || apiKey.length > 512) return failed('Enter a valid notification API key (up to 512 characters).');

  const { error } = await createServiceClient().from('fasty_alert_settings').upsert({
    id: true,
    api_key: apiKey,
    updated_at: new Date().toISOString(),
    updated_by: context.userId,
  });
  if (error) return failed(`Could not save the key. Apply migration 0005 first. (${error.message})`);
  revalidatePath('/admin/alerts');
  return succeeded('Fasty Alert API key saved.');
}

export async function sendIncidentAlertAction(_state: ActionState, formData: FormData): Promise<ActionState> {
  const context = await getAdminContext();
  if (context.status !== 'admin') return failed('Your admin session expired. Sign in again at /admin/login.');
  const incidentId = formText(formData, 'incident_id');
  if (!isUuid(incidentId)) return failed('Choose a valid incident.');

  const client = createServiceClient();
  const { data: incident, error } = await client
    .from('incidents')
    .select('id, title, impact, resolved_at')
    .eq('id', incidentId)
    .is('resolved_at', null)
    .maybeSingle();
  if (error) return failed(`Could not load the incident. (${error.message})`);
  if (!incident) return failed('That incident is no longer active. Refresh the page and try again.');

  try {
    await sendFastyAlert(client, {
      entityType: 'incident',
      entityId: incidentId,
      event: 'catch_up',
      title: `${incident.impact === 'critical' ? 'Critical' : incident.impact === 'major' ? 'Major' : 'Incident'}: ${incident.title}`,
      body: 'An active incident needs attention. Open the operations desk for details and updates.',
      state: 'outage',
      severity: incident.impact === 'critical' || incident.impact === 'major' || incident.impact === 'minor' ? incident.impact : 'none',
    });
    return succeeded('Alert sent to the Fasty Alert account.');
  } catch (caught) {
    return failed(caught instanceof Error ? caught.message : 'Could not send the Fasty Alert notification.');
  }
}

export async function sendMaintenanceAlertAction(_state: ActionState, formData: FormData): Promise<ActionState> {
  const context = await getAdminContext();
  if (context.status !== 'admin') return failed('Your admin session expired. Sign in again at /admin/login.');
  const maintenanceId = formText(formData, 'maintenance_id');
  if (!isUuid(maintenanceId)) return failed('Choose a valid maintenance window.');

  const client = createServiceClient();
  const { data: window, error } = await client
    .from('maintenance_windows')
    .select('id, title, body, status')
    .eq('id', maintenanceId)
    .in('status', ['scheduled', 'in_progress'])
    .maybeSingle();
  if (error) return failed(`Could not load the maintenance window. (${error.message})`);
  if (!window) return failed('That maintenance window is no longer active. Refresh the page and try again.');

  try {
    await sendFastyAlert(client, {
      entityType: 'maintenance',
      entityId: maintenanceId,
      event: 'catch_up',
      title: `Maintenance ${window.status === 'in_progress' ? 'in progress' : 'scheduled'}: ${window.title}`,
      body: window.body || 'Open the operations desk for the maintenance schedule and details.',
      state: 'info',
      severity: 'info',
    });
    return succeeded('Maintenance alert sent to the Fasty Alert account.');
  } catch (caught) {
    return failed(caught instanceof Error ? caught.message : 'Could not send the Fasty Alert notification.');
  }
}

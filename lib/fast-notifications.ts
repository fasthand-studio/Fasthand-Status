import type { SupabaseClient } from '@supabase/supabase-js';

const ENDPOINT = 'https://fastnotifications.fasthand.workers.dev/v1/push';
export const FASTY_ALERT_EMAIL = 'fuoahgfiasgfiasfidhgafoahgfhacjakljdfhbcuasocgsadfga@fasthandstudio.asia';

export interface FastyAlertEvent {
  title: string;
  body: string;
  entityType: 'incident' | 'maintenance';
  entityId: string;
  event: string;
  state: 'outage' | 'recovered' | 'info';
  severity: 'critical' | 'major' | 'minor' | 'none' | 'info' | 'recovered';
}

export async function sendFastyAlert(
  client: SupabaseClient,
  event: FastyAlertEvent,
): Promise<void> {
  const { data, error } = await client
    .from('fasty_alert_settings')
    .select('api_key')
    .eq('id', true)
    .maybeSingle();
  if (error) throw new Error(`Could not load Fasty Alert settings: ${error.message}`);
  const apiKey = (data?.api_key as string | undefined)?.trim();
  if (!apiKey) throw new Error('Fast Notifications API key is not configured. Save it in /admin/alerts.');

  const response = await fetch(ENDPOINT, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: FASTY_ALERT_EMAIL,
      title: event.title,
      body: event.body,
      data: {
        kind: 'fasty-alert',
        entityType: event.entityType,
        entityId: event.entityId,
        event: event.event,
        state: event.state,
        severity: event.severity,
        sound: 'alarm',
      },
    }),
    signal: AbortSignal.timeout(10_000),
  });
  const result = await response.json().catch(() => null) as { success?: boolean; status?: string } | null;
  if (!response.ok || result?.success !== true) {
    throw new Error(`Fasty Alert push failed (${response.status})${result?.status ? `: ${result.status}` : ''}.`);
  }
}

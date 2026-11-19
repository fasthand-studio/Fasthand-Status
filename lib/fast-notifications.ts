import type { SupabaseClient } from '@supabase/supabase-js';

const ENDPOINT = 'https://fastnotifications.fasthand.workers.dev/v1/push';
export const FASTY_ALERT_EMAIL = 'fuoahgfiasgfiasfidhgafoahgfhacjakljdfhbcuasocgsadfga@fasthandstudio.asia';

export async function sendFastyAlert(
  client: SupabaseClient,
  event: { title: string; body: string; incidentId: string; state: 'outage' | 'recovered' },
): Promise<void> {
  const { data, error } = await client
    .from('fasty_alert_settings')
    .select('api_key')
    .eq('id', true)
    .maybeSingle();
  if (error) throw new Error(`Could not load Fasty Alert settings: ${error.message}`);
  const apiKey = (data?.api_key as string | undefined)?.trim();
  if (!apiKey) {
    console.warn('Fasty Alert is not configured; skipping notification. Set its API key in /admin/alerts.');
    return;
  }

  const response = await fetch(ENDPOINT, {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: FASTY_ALERT_EMAIL,
      title: event.title,
      body: event.body,
      data: { kind: 'fasty-alert', incidentId: event.incidentId, state: event.state, sound: 'alarm' },
    }),
    signal: AbortSignal.timeout(10_000),
  });
  if (!response.ok) throw new Error(`Fasty Alert push failed (${response.status}).`);
}

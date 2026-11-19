'use server';

import { revalidatePath } from 'next/cache';
import { getAdminContext } from '@/lib/auth';
import { failed, succeeded, type ActionState } from '@/lib/action-state';
import { createServiceClient } from '@/lib/supabase/admin';
import { formText } from '@/lib/validation';

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

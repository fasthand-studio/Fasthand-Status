import { getPublicClient } from '@/lib/supabase/public';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const dynamic = 'force-dynamic';

export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!UUID_PATTERN.test(id)) return Response.json({ error: 'Invalid incident id.' }, { status: 400 });

  const client = getPublicClient();
  if (!client) return Response.json({ error: 'Status data is not configured.' }, { status: 503 });

  const { data: incident, error: incidentError } = await client
    .from('incidents')
    .select('id, title, status, impact, started_at, resolved_at, auto_detected')
    .eq('id', id)
    .maybeSingle();

  if (incidentError) {
    return Response.json({ error: 'Could not load incident details.' }, { status: 503 });
  }
  if (!incident) return Response.json({ error: 'Incident not found.' }, { status: 404 });

  const [{ data: updates, error: updatesError }, { data: links, error: linksError }] = await Promise.all([
    client
      .from('incident_updates')
      .select('id, status, body, author, created_at')
      .eq('incident_id', id)
      .order('created_at', { ascending: true }),
    client.from('incident_services').select('service_id').eq('incident_id', id),
  ]);

  if (updatesError || linksError) {
    return Response.json({ error: 'Could not load the incident timeline.' }, { status: 503 });
  }

  const serviceIds = (links ?? []).map((row) => row.service_id as string);
  let services: { id: string; name: string; slug: string }[] = [];
  if (serviceIds.length > 0) {
    const { data, error } = await client
      .from('services')
      .select('id, name, slug')
      .in('id', serviceIds)
      .eq('is_public', true)
      .order('name', { ascending: true });
    if (error) return Response.json({ error: 'Could not load affected services.' }, { status: 503 });
    services = data ?? [];
  }

  return Response.json({ incident, updates: updates ?? [], services });
}

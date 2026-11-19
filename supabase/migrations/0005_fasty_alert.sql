-- Fasty Alert notification service settings.
-- Apply after migrations 0001–0004. The API key is never readable by app clients;
-- the trusted server uses the service-role key to load it when dispatching alerts.

create table public.fasty_alert_settings (
  id boolean primary key default true check (id),
  api_key text not null default '',
  updated_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null
);

insert into public.fasty_alert_settings (id, api_key) values (true, '')
on conflict (id) do nothing;

alter table public.fasty_alert_settings enable row level security;
revoke all on public.fasty_alert_settings from anon, authenticated;
grant all on public.fasty_alert_settings to service_role;

comment on table public.fasty_alert_settings is
  'Server-only Fasty Alert API key. Never expose this table through a client query.';

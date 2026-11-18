
# FastHand Status

**Public Status Page · Internal Operations Desk**

[Live Status Page](https://status.fasthandstudio.asia) · [Operations Desk](https://status.fasthandstudio.asia/admin) · [Repository](https://github.com/fasthand-studio/Status-Page) · [Runbook](docs/RUNBOOK.md)

FastHand Status monitors the availability of FastHand services and publishes verified operational information. It includes a public status website and a private operations desk for managing services, monitors, incidents and scheduled maintenance.

Built with Next.js 15, React 19, TypeScript, Tailwind CSS v4, Supabase and Vercel.

> **Core rule:** The status page only reports what the stored monitoring data can prove. Missing checks are not healthy checks. Stale results are not recovery. Empty history is not fabricated uptime.

---

## System at a glance

| Component | Implementation |
|---|---|
| Public website | Next.js App Router |
| Operations desk | Protected `/admin` routes |
| Authentication | Supabase Auth + `admin_users` allowlist |
| Database | Supabase PostgreSQL |
| Database security | Row Level Security |
| Monitoring | Server-side HTTP checks |
| Scheduler | Supabase `pg_cron` + `pg_net` |
| Hosting | Vercel |
| AI copy drafting | Optional Groq integration |

<details>
<summary><strong>Important project links</strong></summary>

| Resource | Address |
|---|---|
| Live website | https://status.fasthandstudio.asia |
| Admin login | https://status.fasthandstudio.asia/admin/login |
| Admin dashboard | https://status.fasthandstudio.asia/admin |
| Repository | `fasthand-studio/Status-Page` |
| Deployment branch | `main` |
| Operational runbook | `docs/RUNBOOK.md` |

The admin routes are available on whichever domain the project is deployed to. Access requires an authenticated Supabase account with an `admin_users` allowlist entry.

</details>

## 01 / Public website

The public website does not require an account. It exposes service availability, incidents, maintenance notices and historical information.

| Page | Route | Information |
|---|---|---|
| Status overview | `/` | Overall status, active incidents, maintenance and 30-day service history |
| Service directory | `/services` | Searchable service groups, 7/30/90-day uptime and 90-day history |
| Incident directory | `/incidents` | Published incident history |
| Incident details | `/incidents/[id]` | Individual incident and Markdown update timeline |
| Maintenance | `/maintenance` | Scheduled, active and completed maintenance |
| History | `/history` | Resolved incidents grouped by month and maintenance archive |

## 02 / Operations desk

**Admin entry:** `/admin`

The operations desk controls the information displayed on the public website. Services, groups, monitors and their ordering are database records, not hardcoded application content.

| Section | Route | Operations |
|---|---|---|
| Overview | `/admin` | Overall health, attention list, runner activity and check freshness |
| Incidents | `/admin/incidents` | Create, publish, update and resolve incidents |
| Services | `/admin/services` | Manage services, groups, monitor attachments and public visibility |
| Groups | `/admin/groups` | Create, rename, reorder and remove service groups |
| Monitors | `/admin/monitors` | Configure endpoints, expected responses, intervals and thresholds |
| Maintenance | `/admin/maintenance` | Publish and manage maintenance windows |

### Access control

Authentication alone does not grant administrative access. Every admin request must pass the Supabase session check and the `admin_users` allowlist check.

The admin area is protected by middleware, re-checked inside server actions, and backed by PostgreSQL RLS. There is no public registration, password-reset interface or non-functional social login.

## 03 / Monitoring architecture

```text
                 SUPABASE DATABASE
                         |
                    pg_cron
                         |
                    pg_net
                         |
                   Every 5 min
                         |
                         v
              POST /api/checks/run
                         |
                         v
                Monitor execution
                         |
              +----------+----------+
              |                     |
              v                     v
         check_results          monitor_runs
              |
              v
       Incident automation
              |
        +-----+------+
        |            |
        v            v
    Open/update   Resolve
    incident      incident
        |
        v
    monitor_status
        |
        v
      Next.js
        |
        v
   Public status page
```

A scheduled run executes every enabled monitor and records the result in PostgreSQL. The runner writes a `monitor_runs` record for every invocation, identifying whether the source was `pg-cron` or `manual`.

Each monitor performs an HTTP request using its configured method, expected status range, optional response phrase and timeout. Redirects are followed manually, with every destination validated again.

Results are stored in `check_results`. The record contains the HTTP status, response time, timestamp and error information. A 280-character response excerpt is retained only when a check fails.

Incident automation uses consecutive results rather than a single failed request. Once the configured failure threshold is reached, an incident is opened. Once the recovery threshold is reached, it is resolved with an update authored by **Automated monitoring**.

A partial unique index prevents duplicate automated incidents.

**Retention:** Results older than `CHECK_RETENTION_DAYS` are removed at the end of each run. The default retention period is 120 days.

### Data visibility

Public pages use aggregate database views. Monitor URLs, raw errors and failure excerpts remain private to administrators.

The public website therefore cannot accidentally expose internal monitoring endpoints or raw response data.

## 04 / Health states and uptime

A service's state is determined by its monitoring evidence, incidents and maintenance windows.

| State | Exact condition |
|---|---|
| Operational | Fresh passing monitor result |
| Degraded | Open incident affecting the service with `minor` impact |
| Outage | Open incident affecting the service with `major` or `critical` impact |
| Maintenance | Applicable scheduled or in-progress maintenance |
| Unknown | Monitor exists, but its latest result is stale |
| No check | No monitor is attached, or no check has ever completed |

### Monitoring freshness

A result is considered stale after:

`max(3 × check interval, 15 minutes)`

A stale monitor becomes **Unknown**. It is never automatically treated as recovered.

### Uptime calculation

Uptime is calculated from actual successful checks within the selected UTC period:

\[
\text{Uptime} = \frac{\text{Successful checks}}{\text{Total checks}} \times 100
\]

| Situation | Display behaviour |
|---|---|
| No check exists | No check |
| Result is stale | Unknown |
| Entire day has no checks | Grey history bar |
| Day has no checks | Excluded from uptime |
| Calculated uptime is 99.999% | Displayed as 99.99% |

Percentages are truncated to two decimal places. They are never rounded upward.

The overall banner deliberately says **All monitored systems operational**, rather than claiming that every FastHand service is operational. Unmonitored services cannot prove their health.

## 05 / Database structure

| Table | Responsibility |
|---|---|
| `service_groups` | Public service categories and display order |
| `services` | Service inventory, descriptions and visibility |
| `monitors` | Monitoring configuration and health-check rules |
| `check_results` | Individual monitoring results and failure evidence |
| `monitor_runs` | Runner execution history and source |
| `incidents` | Incident records and current status |
| `incident_updates` | Published incident timeline |
| `incident_services` | Services affected by incidents |
| `maintenance_windows` | Scheduled maintenance records |
| `maintenance_services` | Services affected by maintenance |
| `admin_users` | Administrative access allowlist |

### Database migrations

Execute these files in order using **Supabase → SQL Editor**.

| Order | Migration | Purpose |
|---|---|---|
| 01 | `0001_init.sql` | Database schema, enums, triggers, RLS and initial views |
| 02 | `0002_seed.sql` | Starting groups, services and verified monitor |
| 03 | `0003_secure_checks.sql` | Private raw check results and daily aggregate views |
| 04 | `0004_public_monitor_status.sql` | Public monitor-status view |

The seed migration uses `ON CONFLICT DO NOTHING`. It can be reapplied without duplicating the starting records. It does not create an administrator.

## 06 / Installation and deployment

### Requirements

| Requirement | Version / service |
|---|---|
| Node.js | 22+ |
| Database | Hosted Supabase PostgreSQL |
| Scheduling | `pg_cron` and `pg_net` |
| Hosting | Vercel |
| Optional AI | Groq API |

There is no Docker, local PostgreSQL installation, Supabase CLI daemon or local database stack.

### Environment configuration

Create the local environment file:

```bash
cp .env.example .env.local
```

| Variable | Required | Description |
|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | Yes | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Yes | Public Supabase key |
| `SUPABASE_SERVICE_ROLE_KEY` | Yes | Server-side privileged access |
| `NEXT_PUBLIC_SITE_URL` | No | Canonical website URL |
| `GROQ` | No | Groq API key for copy drafting |
| `GROQ_MODEL` | No | Optional model selection |
| `SUPABASE_URL` | No | CLI connection URL |
| `CHECK_RETENTION_DAYS` | No | History retention, default 120 |
| `CHECK_SOURCE` | No | Manual check source label |

The service-role key must remain server-side. Never place it in a `NEXT_PUBLIC_` variable.

### First installation

1. Create a Supabase project and collect the project URL, anon key and service-role key.
2. Apply migrations `0001_init.sql` through `0004_public_monitor_status.sql` in order.
3. Create an administrator through Supabase Authentication.
4. Add the existing user to the allowlist using `npm run grant-admin -- you@example.com`.
5. Set the Supabase Site URL to `https://status.fasthandstudio.asia`.
6. Configure `.env.local`, install dependencies and start the application.

```bash
npm install
npm run dev
```

Open `http://localhost:3000/admin/login`.

### Deployment

Import `fasthand-studio/Status-Page` into Vercel using the Next.js framework preset.

Configure the production environment variables, deploy, and attach the custom domain. Pushes to `main` trigger the CI checks and Vercel deployment.

Vercel Deployment Protection must not block the scheduler's requests. The custom domain must be reachable by Supabase `pg_net`.

## 07 / Scheduling

Supabase `pg_cron` and `pg_net` are the only scheduled monitoring system. GitHub Actions is not used to execute checks.

The scheduler runs every five minutes and calls:

`POST /api/checks/run`

Before creating the job, store the Supabase service-role key in Vault under:

`status_service_key`

Enable `pg_cron` and `pg_net` in the Supabase dashboard, then execute:

```sql
select cron.schedule(
  'fasthand-status-checks',
  '*/5 * * * *',
  $$
  select net.http_post(
    url := 'https://status.fasthandstudio.asia/api/checks/run',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || (
        select decrypted_secret
        from vault.decrypted_secrets
        where name = 'status_service_key'
      )
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 30000
  );
  $$
);
```

**Verify the scheduler:**

```sql
select jobid, jobname, command
from cron.job
where jobname = 'fasthand-status-checks';

select start_time, status, return_message
from cron.job_run_details
where jobname = 'fasthand-status-checks'
order by start_time desc
limit 5;
```

To stop the scheduler:

```sql
select cron.unschedule('fasthand-status-checks');
```

## 08 / Security

The security model is built around restricted administrative access, database-level permissions and controlled outbound monitoring requests.

| Area | Protection |
|---|---|
| Admin access | Supabase session + `admin_users` allowlist |
| Database | RLS on every table |
| Raw monitoring data | Admin-only |
| Check runner | Service-role bearer authentication |
| Incident drafting | Admin session, origin checks and rate limits |
| Outbound HTTP checks | Private-address and redirect validation |
| Markdown | HTML disabled through `react-markdown` |
| Credentials | Server-side environment variables |

The monitoring system blocks loopback, private, link-local, metadata, multicast and reserved destinations. Redirects are followed manually and validated at every hop.

The incident-draft endpoint limits request bodies to 8 KB and applies a rate limit of ten drafts per administrator per ten minutes. Groq credentials never leave the server.

### Known security limitations

Admin authentication is password-only; Supabase MFA can be enabled separately. The draft rate limit is per warm function instance, not globally distributed. DNS rebinding is not fully prevented because the monitor guard does not pin resolved addresses. There is also no dedicated audit-log system beyond stored timestamps and update authors.

## 09 / Commands

| Command | Description |
|---|---|
| `npm run dev` | Development server |
| `npm run build` | Production build |
| `npm run start` | Production server |
| `npm run typecheck` | TypeScript validation |
| `npm run checks` | Execute enabled monitors manually |
| `npm run grant-admin -- <email>` | Grant admin access |
| `npm run grant-admin -- --list` | List administrators |
| `npm run grant-admin -- --revoke <email>` | Revoke admin access |

## 10 / Verification

Run the checks before deploying changes:

```bash
npm run typecheck
npm run build
```

Then verify the public page and protected endpoints:

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  https://status.fasthandstudio.asia/

curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  https://status.fasthandstudio.asia/api/checks/run

curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  https://status.fasthandstudio.asia/api/incident-draft
```

Anonymous requests to protected endpoints should be rejected. Confirm the latest scheduled run in `monitor_runs` and verify that its source is `pg-cron`.

There is no unit-test suite in this repository. CI runs TypeScript validation and the production build.

## 11 / Troubleshooting

| Problem | Likely cause | Resolution |
|---|---|---|
| All services show Unknown | Scheduler stopped | Inspect `cron.job_run_details` |
| Scheduled checks return 401 | Stale Vault key | Update Vault and Vercel credentials |
| Service shows No check | Monitor missing or never executed | Attach a monitor and run it manually |
| Monitor suddenly fails | Endpoint behaviour changed | Verify status, phrase, redirect and timeout |
| Admin login succeeds but access is denied | Missing allowlist entry | Run `npm run grant-admin -- <email>` |
| Draft writer is unavailable | Missing `GROQ` variable | Configure the key and redeploy |
| Groq returns model error | Model unavailable to the API key | Select a supported model using `GROQ_MODEL` |

Detailed incident response and recovery procedures are maintained in [`docs/RUNBOOK.md`](docs/RUNBOOK.md).

## 12 / Project structure

```text
app/
├── (public)/       Public status pages
├── admin/          Protected operations desk
└── api/            Check runner and incident drafting

components/         Public and admin interface components

lib/                Supabase, authentication, checks,
                    status calculations, automation,
                    validation and AI integration

supabase/
└── migrations/     Database schema, seed data and RLS

scripts/            Check runner and admin CLI

docs/
└── RUNBOOK.md      Operational procedures

.github/
└── workflows/      CI: typecheck and build

middleware.ts       Admin session protection
```

## 13 / Deliberate limitations

This project intentionally does not generate fake monitoring history, offer non-functional email or SMS subscriptions, or claim coverage for unmonitored services.

There are no individual public service-detail pages. Monitoring runs on a five-minute schedule, and incidents are opened only after their configured failure thresholds are reached.

Groq-generated text is a drafting aid, not an authoritative source. It cannot publish incidents and does not receive monitoring evidence. Administrators remain responsible for verifying every public update.

---

**FastHand Status**  
`fasthand-studio/Status-Page` · `main` · Next.js · Supabase · Vercel

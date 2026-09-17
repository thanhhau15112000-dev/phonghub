# PhongHub production deployment

Deployment target:

`Cloudflare DNS/proxy -> Render Docker Web Service -> Spring Boot + Thymeleaf -> Supabase Auth/Postgres`

The Cloudflare Worker files in the repository are not part of this deployment path. Cloudflare is used as the DNS/proxy layer; Render runs the Spring application.

## Render

Create the service from the repository Blueprint (`render.yaml`) or configure the equivalent values in the Render Dashboard:

- Runtime: Docker
- Region: Singapore
- Health check: `/health`
- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`
- `SUPABASE_JWKS_URI`
- `SUPABASE_JWT_ISSUER`
- `SUPABASE_JWT_AUDIENCE=authenticated`

The secret values must be entered in Render's secret/environment-variable UI. They are intentionally not stored in `render.yaml`.

The application binds to Render's `PORT` environment variable and `0.0.0.0`. Flyway runs the versioned migrations in `classpath:db/migration` during startup.

## First Admin account

Do not create the first Admin with a Flyway migration. Supabase Auth owns the identity and password, while `public.users` stores the application profile and role.

1. Deploy once with the production environment variables so Flyway creates the schema.
2. In Supabase Dashboard, open Authentication > Users and create one confirmed user with the email and password chosen by the owner.
3. Copy that Auth user's UUID.
4. In Supabase SQL Editor, insert the matching application profile. Replace the placeholders with the values selected by the owner:

```sql
insert into public.users (
    id, username, email, full_name, phone, role, status, must_change_password
) values (
    '<AUTH_USER_UUID>',
    '<ADMIN_USERNAME>',
    '<AUTH_USER_EMAIL>',
    '<ADMIN_FULL_NAME>',
    null,
    'ADMIN',
    'ACTIVE',
    false
);
```

The `id` and `email` must match the Supabase Auth user. The password is never stored in `public.users`.

## Cloudflare

After the Render service is healthy:

1. Add or select the domain in the Cloudflare account.
2. Add the DNS record requested by Render for the custom domain and keep proxying enabled only after the origin works directly.
3. Use HTTPS with Full (strict) TLS after Render's certificate is active.
4. Cache static assets such as `/css/*`; bypass cache for HTML pages, `/api/*`, `/login`, and `/session/*`.

## Smoke checks

Run these checks against the Render URL before attaching the public domain:

1. `GET /health` returns `200` and a JSON status of `UP`.
2. `GET /` redirects to `/login` when there is no session.
3. The login form accepts the provisioned Admin account and redirects to the dashboard.
4. A first-login account is redirected to `/account/password` and cannot access business pages until the password is changed.
5. `POST /api/auth/refresh` works with only a valid refresh token; no expired access token is required.
6. Create a property and room from the UI, then verify the rows in Supabase.
7. Confirm logout invalidates the browser session.

The free Render plan may sleep when idle; treat the first request after idle as a cold-start check, not an application failure.

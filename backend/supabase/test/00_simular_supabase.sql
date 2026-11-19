do $$ begin create role anon nologin; exception when duplicate_object then null; end $$;
do $$ begin create role authenticated nologin; exception when duplicate_object then null; end $$;
create schema auth;
create table auth.users (id uuid primary key default gen_random_uuid(), email text, is_anonymous boolean not null default false, last_sign_in_at timestamptz);
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
grant usage on schema auth to anon, authenticated;
grant usage on schema public to anon, authenticated;
grant execute on function auth.uid() to anon, authenticated;
insert into auth.users(id, email, is_anonymous) values
 ('11111111-1111-4111-8111-111111111111', null, true),
 ('22222222-2222-4222-8222-222222222222', null, true),
 ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'admin@ejemplo.cl', false);

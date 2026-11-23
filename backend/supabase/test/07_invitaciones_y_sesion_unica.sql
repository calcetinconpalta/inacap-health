-- 1.3.3 · Tareas 12 y 13: toda cuenta nace como alumno, el equipo se pre-registra (el rol
-- solo se aplica con el correo CONFIRMADO) y una sola sesión activa por cuenta.
-- Ejecutar después de 00–05 (usa al administrador de 00).
\set ON_ERROR_STOP 1

insert into auth.users (id, email, is_anonymous, email_confirmed_at) values
 ('c1000000-0000-4000-8000-0000000000c1', 'tutora.nueva@ejemplo.cl', false, now()),   -- pre-registrada y confirmada
 ('c2000000-0000-4000-8000-0000000000c2', 'docente.nuevo@ejemplo.cl', false, null),   -- pre-registrado, SIN confirmar
 ('c3000000-0000-4000-8000-0000000000c3', 'alumna@ejemplo.cl', false, now()),         -- no pre-registrada
 ('c4000000-0000-4000-8000-0000000000c4', null, true, null)                            -- anónima
on conflict do nothing;

-- ===== El administrador pre-registra =====
reset role;
set role authenticated;
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
do $$ begin
  if public.invitar_personal('Tutora.Nueva@EJEMPLO.cl ', 'tutor', 'Tutora Nueva') <> 'asignado' then
    raise exception 'FALLO: cuenta existente y confirmada no recibió el rol de inmediato';
  end if;
  if public.invitar_personal('docente.nuevo@ejemplo.cl', 'docente', 'Docente Nuevo') <> 'invitado' then
    raise exception 'FALLO: cuenta sin confirmar recibió el rol';
  end if;
  if public.invitar_personal('futuro@ejemplo.cl', 'bienestar', 'Persona Futura') <> 'invitado' then
    raise exception 'FALLO: correo sin cuenta debía quedar invitado';
  end if;
end $$;
do $$ declare ok boolean := false; begin
  begin perform public.invitar_personal('x@ejemplo.cl', 'superadmin', 'Nadie'); exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: aceptó un rol inválido'; end if;
end $$;
do $$ declare ok boolean := false; begin
  begin perform public.invitar_personal('y@ejemplo.cl', 'tutor', E'<script>'); exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: aceptó un nombre visible inválido'; end if;
end $$;

-- ===== Nadie más puede pre-registrar ni ver las invitaciones =====
set request.jwt.claim.sub = 'c3000000-0000-4000-8000-0000000000c3';
do $$ declare ok boolean := false; begin
  begin perform public.invitar_personal('c3@ejemplo.cl', 'admin', 'Yo Mismo'); exception when sqlstate 'PGRST' then ok := true; end;
  if not ok then raise exception 'FALLO: una alumna se pre-registró como admin'; end if;
end $$;
do $$ declare ok boolean := false; begin
  begin perform 1 from public.personal_invitado; exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FALLO: se puede leer personal_invitado directo'; end if;
end $$;

-- ===== correo pre-registrado + confirmado → rol =====
set request.jwt.claim.sub = 'c1000000-0000-4000-8000-0000000000c1';
do $$ begin
  if public.reclamar_rol_invitado() is distinct from 'tutor' then raise exception 'FALLO: la tutora confirmada no tiene rol'; end if;
end $$;

-- ===== no pre-registrada → alumna (sin rol) =====
set request.jwt.claim.sub = 'c3000000-0000-4000-8000-0000000000c3';
do $$ begin
  if public.reclamar_rol_invitado() is not null then raise exception 'FALLO: una cuenta no pre-registrada recibió rol'; end if;
end $$;

-- ===== pre-registrado pero NO confirmado → sin rol (evita quedarse con el correo de otra persona) =====
set request.jwt.claim.sub = 'c2000000-0000-4000-8000-0000000000c2';
do $$ begin
  if public.reclamar_rol_invitado() is not null then raise exception 'FALLO: cuenta sin confirmar recibió rol'; end if;
end $$;

-- Al confirmar el correo, el disparador aplica el rol solo.
reset role;
update auth.users set email_confirmed_at = now() where id = 'c2000000-0000-4000-8000-0000000000c2';
set role authenticated;
set request.jwt.claim.sub = 'c2000000-0000-4000-8000-0000000000c2';
do $$ begin
  if public.rol_actual() is distinct from 'docente' then raise exception 'FALLO: al confirmar no se aplicó el rol de docente'; end if;
end $$;

-- Una cuenta que se crea ya confirmada con un correo invitado también lo recibe (disparador en insert).
reset role;
insert into auth.users (id, email, is_anonymous, email_confirmed_at)
values ('c5000000-0000-4000-8000-0000000000c5', 'FUTURO@ejemplo.cl', false, now());
set role authenticated;
set request.jwt.claim.sub = 'c5000000-0000-4000-8000-0000000000c5';
do $$ begin
  if public.rol_actual() is distinct from 'bienestar' then raise exception 'FALLO: cuenta nueva confirmada no recibió su rol'; end if;
end $$;
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
do $$ begin
  if exists (select 1 from public.listar_invitaciones()) then raise exception 'FALLO: quedaron invitaciones ya usadas'; end if;
end $$;

-- ===== Una sola sesión activa por cuenta =====
reset role;
insert into auth.sessions (id, user_id) values
 ('5a000000-0000-4000-8000-00000000005a', 'c3000000-0000-4000-8000-0000000000c3');   -- teléfono A (B inicia sesión después)
set role authenticated;
select set_config('request.headers', '{"cf-connecting-ip":"198.51.100.7"}', false);
select set_config('request.method', 'GET', false);
set request.jwt.claim.sub = 'c3000000-0000-4000-8000-0000000000c3';

-- Teléfono A inicia sesión y la registra.
select set_config('request.jwt.claims', '{"sub":"c3000000-0000-4000-8000-0000000000c3","session_id":"5a000000-0000-4000-8000-00000000005a","is_anonymous":false}', false);
select set_config('request.path', '/rpc/registrar_sesion', false);
select public.antes_de_cada_peticion();
select public.registrar_sesion();
select set_config('request.path', '/rest/v1/registros_biometricos', false);
select public.antes_de_cada_peticion();   -- A funciona

-- Teléfono B inicia sesión más tarde (recién ahí existe su sesión en auth.sessions)…
reset role;
insert into auth.sessions (id, user_id) values ('5b000000-0000-4000-8000-00000000005b', 'c3000000-0000-4000-8000-0000000000c3');
set role authenticated;
select set_config('request.headers', '{"cf-connecting-ip":"198.51.100.7"}', false);
select set_config('request.method', 'GET', false);
set request.jwt.claim.sub = 'c3000000-0000-4000-8000-0000000000c3';
-- …y registrar_sesion pasa aunque A era la vigente…
select set_config('request.jwt.claims', '{"sub":"c3000000-0000-4000-8000-0000000000c3","session_id":"5b000000-0000-4000-8000-00000000005b","is_anonymous":false}', false);
select set_config('request.path', '/rpc/registrar_sesion', false);
select public.antes_de_cada_peticion();
select public.registrar_sesion();
reset role;
do $$ begin
  if exists (select 1 from auth.sessions where id = '5a000000-0000-4000-8000-00000000005a') then raise exception 'FALLO: no se revocó la sesión del teléfono A'; end if;
  if not exists (select 1 from auth.sessions where id = '5b000000-0000-4000-8000-00000000005b') then raise exception 'FALLO: se borró la sesión nueva'; end if;
end $$;
set role authenticated;
select set_config('request.path', '/rest/v1/registros_biometricos', false);
select public.antes_de_cada_peticion();   -- B funciona

-- …y el teléfono A, con su token de acceso aún vigente, es rechazado.
select set_config('request.jwt.claims', '{"sub":"c3000000-0000-4000-8000-0000000000c3","session_id":"5a000000-0000-4000-8000-00000000005a","is_anonymous":false}', false);
do $$ declare ok boolean := false; begin
  begin perform public.antes_de_cada_peticion();
  exception when sqlstate 'PGRST' then ok := sqlerrm like '%SESION_REEMPLAZADA%'; end;
  if not ok then raise exception 'FALLO: el teléfono anterior siguió funcionando'; end if;
end $$;

-- Las cuentas anónimas no se ven afectadas.
set request.jwt.claim.sub = 'c4000000-0000-4000-8000-0000000000c4';
select set_config('request.jwt.claims', '{"sub":"c4000000-0000-4000-8000-0000000000c4","session_id":"5c000000-0000-4000-8000-00000000005c","is_anonymous":true}', false);
select public.registrar_sesion();
select public.antes_de_cada_peticion();
reset role;
do $$ begin
  if exists (select 1 from public.sesiones_vigentes where user_id = 'c4000000-0000-4000-8000-0000000000c4') then
    raise exception 'FALLO: se registró sesión única para una cuenta anónima';
  end if;
end $$;

reset request.jwt.claim.sub;
select set_config('request.jwt.claims', '', false);
select set_config('request.path', '', false);
\echo 'TODAS LAS PRUEBAS DE INVITACIONES Y SESIÓN ÚNICA PASARON'

-- v3: límites de uso, bloqueo por IP, limpieza de textos, atención profesional y gestión del equipo.
\set ON_ERROR_STOP 1
insert into auth.users (id, email, is_anonymous) values
 ('51000000-0000-4000-8000-000000000051', null, true),                       -- estudiante S
 ('b1000000-0000-4000-8000-0000000000b1', 'nutri@ejemplo.cl', false),        -- profesional 1
 ('b2000000-0000-4000-8000-0000000000b2', 'psico@ejemplo.cl', false),        -- profesional 2
 ('ee000000-0000-4000-8000-0000000000ee', 'nuevo@ejemplo.cl', false)
on conflict do nothing;
-- 1.3.3: el rol solo se asigna a cuentas con el correo confirmado.
update auth.users set email_confirmed_at = now() where email in ('nutri@ejemplo.cl', 'psico@ejemplo.cl', 'nuevo@ejemplo.cl');
insert into public.personal (user_id, rol, nombre_visible) values
 ('b1000000-0000-4000-8000-0000000000b1', 'bienestar', 'Nutri Paula'),
 ('b2000000-0000-4000-8000-0000000000b2', 'bienestar', 'Psico Tomás')
on conflict do nothing;

set role authenticated;
set request.jwt.claim.sub = '51000000-0000-4000-8000-000000000051';
insert into usuarios (id) values ('51000000-0000-4000-8000-000000000051') on conflict do nothing;

-- ===== Limpieza de textos en el servidor =====
insert into conversaciones (id, tema) values ('5c000000-0000-4000-8000-0000000005c0', 'habitos');
insert into mensajes (conversacion_id, rol_autor, texto) values ('5c000000-0000-4000-8000-0000000005c0', 'estudiante', E'  Hola\u200B\x07 equipo\n\n\n\n¿cómo duermo mejor?  ');
do $$ begin
  if (select texto from mensajes where conversacion_id = '5c000000-0000-4000-8000-0000000005c0') <> E'Hola equipo\n\n¿cómo duermo mejor?' then
    raise exception 'FALLO: texto no saneado: %', (select texto from mensajes where conversacion_id = '5c000000-0000-4000-8000-0000000005c0');
  end if;
end $$;
do $$ declare ok boolean := false; begin
  begin insert into mensajes (conversacion_id, rol_autor, texto) values ('5c000000-0000-4000-8000-0000000005c0', 'estudiante', E'\u200B  \x07 ');
  exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: se aceptó un mensaje vacío'; end if;
end $$;
do $$ declare ok boolean := false; begin
  begin insert into derivaciones (id, canal, codigo, fecha) values (gen_random_uuid(), 'cesfam', '<script>', now());
  exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: se aceptó un código de derivación inválido'; end if;
end $$;
do $$ declare ok boolean := false; begin
  begin insert into registros_biometricos (id, tipo, agua_ml, fecha) values (gen_random_uuid(), 'agua', 250, now() + interval '30 days');
  exception when check_violation then ok := true; end;
  if not ok then raise exception 'FALLO: se aceptó una fecha futura'; end if;
end $$;

-- ===== Límite de uso por persona: 30 mensajes por minuto =====
do $$ declare ok boolean := false; i int; begin
  for i in 1..29 loop
    insert into mensajes (conversacion_id, rol_autor, texto) values ('5c000000-0000-4000-8000-0000000005c0', 'estudiante', 'mensaje ' || i);
  end loop;
  begin insert into mensajes (conversacion_id, rol_autor, texto) values ('5c000000-0000-4000-8000-0000000005c0', 'estudiante', 'uno de más');
  exception when sqlstate 'PGRST' then ok := true; end;   -- PostgREST lo devuelve como HTTP 429
  if not ok then raise exception 'FALLO: no se aplicó el límite de mensajes'; end if;
end $$;

-- ===== Derivación y atención profesional =====
insert into derivaciones (id, canal, codigo, fecha) values ('5d000000-0000-4000-8000-0000000005d0', 'bienestar_presencial', 'inh-abc777', now());
do $$ begin
  if (select codigo from derivaciones where id = '5d000000-0000-4000-8000-0000000005d0') <> 'INH-ABC777' then raise exception 'FALLO: el código no se normalizó'; end if;
end $$;
do $$ declare ok boolean := false; begin
  begin perform public.iniciar_atencion('5d000000-0000-4000-8000-0000000005d0'); exception when sqlstate 'PGRST' then ok := true; end;
  if not ok then raise exception 'FALLO: un estudiante inició una atención'; end if;
end $$;

set request.jwt.claim.sub = 'b1000000-0000-4000-8000-0000000000b1';
select public.iniciar_atencion('5d000000-0000-4000-8000-0000000005d0');
do $$ declare conv uuid; begin
  select id into conv from conversaciones where derivacion_id = '5d000000-0000-4000-8000-0000000005d0';
  if conv is null then raise exception 'FALLO: el profesional no ve su atención'; end if;
  if public.iniciar_atencion('5d000000-0000-4000-8000-0000000005d0') <> conv then raise exception 'FALLO: se duplicó la atención'; end if;
  insert into mensajes (conversacion_id, rol_autor, texto) values (conv, 'profesional', 'Hola, soy Paula, nutricionista. ¿Te acomoda conversar por aquí?');
  if (select estado from derivaciones where id = '5d000000-0000-4000-8000-0000000005d0') <> 'en_contacto' then raise exception 'FALLO: la derivación no pasó a en contacto'; end if;
end $$;

set request.jwt.claim.sub = 'b2000000-0000-4000-8000-0000000000b2';
do $$ begin
  if exists (select 1 from conversaciones where tipo = 'atencion') then raise exception 'FALLO: otro profesional ve una atención ajena'; end if;
end $$;
set request.jwt.claim.sub = 'd0000000-0000-4000-8000-00000000000d';
do $$ begin
  if exists (select 1 from conversaciones where tipo = 'atencion') then raise exception 'FALLO: un docente ve una atención (es confidencial)'; end if;
  if exists (select 1 from mensajes m join conversaciones c on c.id = m.conversacion_id where c.tipo = 'atencion') then raise exception 'FALLO: un docente ve mensajes de atención'; end if;
end $$;
set request.jwt.claim.sub = '70000000-0000-4000-8000-000000000071';
do $$ declare ok boolean := false; begin
  if exists (select 1 from conversaciones where tipo = 'atencion') then raise exception 'FALLO: un tutor ve una atención'; end if;
  begin insert into mensajes (conversacion_id, rol_autor, texto) select id, 'profesional', 'me hago pasar' from public.conversaciones where tipo = 'atencion';
        if not found then ok := true; end if;
  exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: un tutor escribió como profesional'; end if;
end $$;

set request.jwt.claim.sub = '51000000-0000-4000-8000-000000000051';
do $$ begin
  if (select count(*) from mensajes m join conversaciones c on c.id = m.conversacion_id where c.tipo = 'atencion') <> 2 then
    raise exception 'FALLO: el estudiante no ve su chat de atención';
  end if;
end $$;

-- ===== Gestión del equipo: solo el admin =====
set request.jwt.claim.sub = 'b1000000-0000-4000-8000-0000000000b1';
do $$ declare ok boolean := false; begin
  begin perform public.asignar_rol('nuevo@ejemplo.cl', 'admin', 'Intruso'); exception when sqlstate 'PGRST' then ok := true; end;
  if not ok then raise exception 'FALLO: un profesional se dio rol de admin a otro'; end if;
end $$;
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
select public.asignar_rol('NUEVO@ejemplo.cl', 'tutor', 'Tutor Nuevo');
do $$ declare r jsonb; begin
  if not exists (select 1 from public.listar_equipo() where email = 'nuevo@ejemplo.cl' and rol = 'tutor' and activo) then raise exception 'FALLO: no se asignó el rol'; end if;
  perform public.desactivar_personal('ee000000-0000-4000-8000-0000000000ee');
  if exists (select 1 from public.listar_equipo() where email = 'nuevo@ejemplo.cl' and activo) then raise exception 'FALLO: no se desactivó'; end if;
  perform public.bloquear_ip('203.0.113.9', 30, 'Prueba');
  r := public.resumen_seguridad();
  if jsonb_array_length(r -> 'ips_bloqueadas') < 1 then raise exception 'FALLO: bloqueo de IP'; end if;
  if jsonb_array_length(r -> 'auditoria') < 4 then raise exception 'FALLO: auditoría incompleta (%)', jsonb_array_length(r -> 'auditoria'); end if;
end $$;
-- 1.3.3: un correo sin cuenta queda PRE-REGISTRADO (invitación), sin rol hasta que cree la cuenta y confirme.
select public.asignar_rol('sin-cuenta@ejemplo.cl', 'tutor', 'Nadie');
do $$ begin
  if not exists (select 1 from public.listar_invitaciones() where correo = 'sin-cuenta@ejemplo.cl' and rol = 'tutor') then
    raise exception 'FALLO: el correo sin cuenta no quedó pre-registrado';
  end if;
  if exists (select 1 from public.listar_equipo() where email = 'sin-cuenta@ejemplo.cl') then
    raise exception 'FALLO: un correo sin cuenta apareció en el equipo';
  end if;
  perform public.cancelar_invitacion('sin-cuenta@ejemplo.cl');
end $$;

-- ===== Bloqueo y límite por IP (la función que PostgREST llama antes de cada petición) =====
select set_config('request.headers', '{"cf-connecting-ip":"203.0.113.9"}', false);
select set_config('request.method', 'GET', false);
do $$ declare ok boolean := false; begin
  begin perform public.antes_de_cada_peticion(); exception when sqlstate 'PGRST' then ok := true; end;
  if not ok then raise exception 'FALLO: una IP bloqueada pudo consultar'; end if;
end $$;
select set_config('request.headers', '{"x-forwarded-for":"198.51.100.7, 10.0.0.1"}', false);
select set_config('request.method', 'POST', false);
do $$ declare ok boolean := false; i int; begin
  if public.ip_cliente() <> '198.51.100.7' then raise exception 'FALLO: IP del cliente mal leída (%)', public.ip_cliente(); end if;
  for i in 1..120 loop perform public.antes_de_cada_peticion(); end loop;   -- dentro del límite
  begin perform public.antes_de_cada_peticion(); exception when sqlstate 'PGRST' then ok := true; end;
  if not ok then raise exception 'FALLO: no se aplicó el límite por IP'; end if;
end $$;
select set_config('request.method', 'GET', false);
do $$ begin perform public.antes_de_cada_peticion(); end $$;   -- las lecturas no se cuentan

reset role;
reset request.jwt.claim.sub;
\echo 'TODAS LAS PRUEBAS DE SEGURIDAD v3 Y ATENCIÓN PROFESIONAL PASARON'

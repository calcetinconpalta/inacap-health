-- 1.3.3 · Tarea 18c: sede del alumno. Solo sedes de la lista; el resumen oculta las sedes con < 5 personas.
-- Ejecutar después de 00–07 (usa al administrador de 00).
\set ON_ERROR_STOP 1

reset role;
insert into public.sedes (nombre) values ('Osorno') on conflict do nothing;
insert into auth.users (id, email, is_anonymous)
select ('d0000000-0000-4000-8000-0000000000' || lpad(i::text, 2, '0'))::uuid, null, true from generate_series(1, 7) i
on conflict do nothing;
-- 6 en Valdivia (se muestra) y 1 en Osorno (se oculta).
insert into public.usuarios (id, sede)
select ('d0000000-0000-4000-8000-0000000000' || lpad(i::text, 2, '0'))::uuid, case when i = 7 then 'Osorno' else 'Valdivia' end
from generate_series(1, 7) i
on conflict (id) do update set sede = excluded.sede;

-- Una sede que no está en la lista se rechaza (no se puede mandar una dirección ni texto libre).
set role authenticated;
set request.jwt.claim.sub = 'd0000000-0000-4000-8000-000000000001';
do $$ declare ok boolean := false; begin
  begin update public.usuarios set sede = 'Calle Falsa 123' where id = 'd0000000-0000-4000-8000-000000000001';
  exception when foreign_key_violation then ok := true; end;
  if not ok then raise exception 'FALLO: se aceptó una sede que no está en la lista'; end if;
end $$;

-- El resumen (solo admin) muestra Valdivia y oculta Osorno.
do $$ declare ok boolean := false; begin
  begin perform public.resumen_por_sede(); exception when insufficient_privilege then ok := true; end;
  if not ok then raise exception 'FALLO: un estudiante vio el resumen por sede'; end if;
end $$;
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
do $$ declare r jsonb := public.resumen_por_sede(); begin
  if (r -> 'por_sede' ->> 'Valdivia')::int < 6 then raise exception 'FALLO: falta Valdivia: %', r; end if;
  if r -> 'por_sede' ? 'Osorno' then raise exception 'FALLO: se mostró una sede con menos de 5 personas: %', r; end if;
  if (r ->> 'sedes_ocultas')::int < 1 then raise exception 'FALLO: no se informó la sede oculta: %', r; end if;
end $$;

-- Revocar borra también la sede.
set request.jwt.claim.sub = 'd0000000-0000-4000-8000-000000000002';
select public.eliminar_mis_datos('revocacion');
reset role;
do $$ begin
  if (select sede from public.usuarios where id = 'd0000000-0000-4000-8000-000000000002') <> 'sin_dato' then
    raise exception 'FALLO: revocar no borró la sede';
  end if;
end $$;
reset request.jwt.claim.sub;
\echo 'TODAS LAS PRUEBAS DE SEDE PASARON'

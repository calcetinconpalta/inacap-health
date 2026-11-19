-- Roles del personal, orientación entre pares y derivaciones cifradas.
-- Cada bloque se detiene con "FALLO: ..." si algo no se comporta como debe.
\set ON_ERROR_STOP 1
insert into auth.users (id, email, is_anonymous) values
 ('c0000000-0000-4000-8000-00000000000c', null, true),          -- estudiante C
 ('e0000000-0000-4000-8000-00000000000e', null, true),          -- estudiante E
 ('70000000-0000-4000-8000-000000000071', 'tutor1@ejemplo.cl', false),
 ('70000000-0000-4000-8000-000000000072', 'tutor2@ejemplo.cl', false),
 ('d0000000-0000-4000-8000-00000000000d', 'docente@ejemplo.cl', false),
 ('b0000000-0000-4000-8000-00000000000b', 'bienestar@ejemplo.cl', false)
on conflict do nothing;
insert into public.personal (user_id, rol, nombre_visible) values
 ('70000000-0000-4000-8000-000000000071', 'tutor', 'Tutora Camila'),
 ('70000000-0000-4000-8000-000000000072', 'tutor', 'Tutor Diego'),
 ('d0000000-0000-4000-8000-00000000000d', 'docente', 'Prof. Rojas'),
 ('b0000000-0000-4000-8000-00000000000b', 'bienestar', 'Bienestar Valdivia')
on conflict do nothing;

set role authenticated;

-- ===== Estudiante C abre una conversación =====
set request.jwt.claim.sub = 'c0000000-0000-4000-8000-00000000000c';
insert into usuarios (id) values ('c0000000-0000-4000-8000-00000000000c') on conflict do nothing;
insert into conversaciones (id, tema) values ('cc000000-0000-4000-8000-0000000000cc', 'alimentacion');
insert into mensajes (conversacion_id, rol_autor, texto) values ('cc000000-0000-4000-8000-0000000000cc', 'estudiante', 'Hola, ¿cómo armo colaciones sanas?');
do $$ declare ok boolean := false; begin
  begin insert into mensajes (conversacion_id, rol_autor, texto) values ('cc000000-0000-4000-8000-0000000000cc', 'tutor', 'me hago pasar por tutor');
  exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: un estudiante pudo escribir como tutor'; end if;
end $$;
do $$ declare ok boolean := false; begin
  begin perform public.tomar_conversacion('cc000000-0000-4000-8000-0000000000cc'); exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: un estudiante pudo tomar una conversación'; end if;
end $$;

-- Derivación con contacto cifrado (texto ilegible) y una con formato inválido
insert into derivaciones (id, canal, codigo, fecha, contacto_cifrado) values
 ('dd000000-0000-4000-8000-0000000000dd', 'bienestar_correo', 'INH-ABC123', now(),
  '{"v":1,"alg":"RSA-OAEP-256+A256GCM","kid":"ab12","ek":"AAAA","iv":"BBBB","ct":"CCCC"}');
do $$ declare ok boolean := false; begin
  begin insert into derivaciones (id, canal, codigo, fecha, contacto_cifrado) values (gen_random_uuid(), 'cesfam', 'INH-ZZZ999', now(), '{"nombre":"Ana en claro"}');
  exception when check_violation then ok := true; end;
  if not ok then raise exception 'FALLO: se aceptó un contacto sin cifrar'; end if;
end $$;

-- ===== Otro estudiante no ve nada de C =====
set request.jwt.claim.sub = 'e0000000-0000-4000-8000-00000000000e';
do $$ begin
  if (select count(*) from conversaciones) <> 0 then raise exception 'FALLO: E ve conversaciones de C'; end if;
  if (select count(*) from mensajes) <> 0 then raise exception 'FALLO: E ve mensajes de C'; end if;
  if (select count(*) from derivaciones) <> 0 then raise exception 'FALLO: E ve derivaciones de C'; end if;
end $$;

-- ===== Tutor 1 ve la conversación sin asignar, la toma y responde =====
set request.jwt.claim.sub = '70000000-0000-4000-8000-000000000071';
do $$ begin
  if public.rol_actual() <> 'tutor' then raise exception 'FALLO: rol del tutor'; end if;
  if (select count(*) from conversaciones) <> 1 then raise exception 'FALLO: el tutor no ve la conversación abierta'; end if;
  if (select count(*) from derivaciones) <> 0 then raise exception 'FALLO: un tutor ve derivaciones'; end if;
end $$;
select public.tomar_conversacion('cc000000-0000-4000-8000-0000000000cc');
insert into mensajes (conversacion_id, rol_autor, texto) values ('cc000000-0000-4000-8000-0000000000cc', 'tutor', 'Una fruta y un puñado de frutos secos son buena opción.');

-- ===== Tutor 2 ya no la ve (está asignada a otro) =====
set request.jwt.claim.sub = '70000000-0000-4000-8000-000000000072';
do $$ begin
  if (select count(*) from conversaciones) <> 0 then raise exception 'FALLO: tutor 2 ve una conversación ajena'; end if;
end $$;

-- ===== Docente supervisa, observa y deriva =====
set request.jwt.claim.sub = 'd0000000-0000-4000-8000-00000000000d';
do $$ begin
  if (select count(*) from mensajes) <> 2 then raise exception 'FALLO: el docente no ve todos los mensajes'; end if;
end $$;
select public.supervisar('cc000000-0000-4000-8000-0000000000cc', 'observacion', 'Respuesta adecuada, sin indicar dietas.');
select public.supervisar('cc000000-0000-4000-8000-0000000000cc', 'derivada', 'Mejor que lo vea un profesional.');
do $$ begin
  if (select estado from conversaciones where id = 'cc000000-0000-4000-8000-0000000000cc') <> 'derivada' then raise exception 'FALLO: no quedó derivada'; end if;
  if (select count(*) from mensajes where rol_autor = 'sistema') <> 1 then raise exception 'FALLO: falta el mensaje de derivación'; end if;
  if (select count(*) from supervision) <> 2 then raise exception 'FALLO: bitácora de supervisión'; end if;
end $$;

-- ===== C ya no puede escribir en una conversación derivada =====
set request.jwt.claim.sub = 'c0000000-0000-4000-8000-00000000000c';
do $$ declare ok boolean := false; begin
  begin insert into mensajes (conversacion_id, rol_autor, texto) values ('cc000000-0000-4000-8000-0000000000cc', 'estudiante', 'hola?');
  exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: se pudo escribir en una conversación derivada'; end if;
  if (select count(*) from supervision) <> 0 then raise exception 'FALLO: un estudiante ve la bitácora'; end if;
end $$;

-- ===== Bienestar ve y gestiona la derivación cifrada; nadie más puede =====
set request.jwt.claim.sub = 'b0000000-0000-4000-8000-00000000000b';
do $$ begin
  if (select count(*) from derivaciones where contacto_cifrado is not null) <> 1 then raise exception 'FALLO: Bienestar no ve la derivación'; end if;
  if (select count(*) from conversaciones) <> 0 then raise exception 'FALLO: Bienestar ve conversaciones'; end if;
end $$;
select public.gestionar_derivacion('dd000000-0000-4000-8000-0000000000dd', 'en_contacto');
set request.jwt.claim.sub = '70000000-0000-4000-8000-000000000071';
do $$ declare ok boolean := false; begin
  begin perform public.gestionar_derivacion('dd000000-0000-4000-8000-0000000000dd', 'atendida'); exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: un tutor gestionó una derivación'; end if;
end $$;

-- ===== Nadie del personal puede abrir conversaciones como estudiante =====
do $$ declare ok boolean := false; begin
  begin insert into usuarios (id) values ('70000000-0000-4000-8000-000000000071'); insert into conversaciones (tema) values ('otro');
  exception when others then ok := true; end;
  if not ok then raise exception 'FALLO: un tutor abrió una conversación como estudiante'; end if;
end $$;

-- ===== Revocar conserva la derivación con contacto; borrar conversaciones mantiene la bitácora =====
set request.jwt.claim.sub = 'c0000000-0000-4000-8000-00000000000c';
select public.eliminar_mis_datos('revocacion');
select public.borrar_mis_conversaciones();
reset role;
do $$ begin
  if (select estado from derivaciones where id = 'dd000000-0000-4000-8000-0000000000dd') <> 'en_contacto' then raise exception 'FALLO: la revocación borró la derivación pedida'; end if;
  if (select count(*) from conversaciones where usuario_id = 'c0000000-0000-4000-8000-00000000000c') <> 0 then raise exception 'FALLO: no se borraron las conversaciones'; end if;
  if (select count(*) from supervision where conversacion_id is null) <> 2 then raise exception 'FALLO: se perdió la bitácora de supervisión'; end if;
end $$;

-- ===== Eliminar todo borra también la derivación y la cuenta anónima =====
set role authenticated;
set request.jwt.claim.sub = 'c0000000-0000-4000-8000-00000000000c';
select public.eliminar_mis_datos('eliminacion');
reset role;
reset request.jwt.claim.sub;
do $$ begin
  if exists (select 1 from derivaciones where id = 'dd000000-0000-4000-8000-0000000000dd') then raise exception 'FALLO: quedó la derivación tras eliminar todo'; end if;
  if exists (select 1 from auth.users where id = 'c0000000-0000-4000-8000-00000000000c') then raise exception 'FALLO: quedó la cuenta anónima'; end if;
end $$;

-- ===== Una cuenta con correo también se elimina por completo; el personal nunca =====
insert into auth.users (id, email, is_anonymous) values ('f0000000-0000-4000-8000-00000000000f', 'ana@ejemplo.cl', false);
set role authenticated;
set request.jwt.claim.sub = 'f0000000-0000-4000-8000-00000000000f';
insert into usuarios (id) values ('f0000000-0000-4000-8000-00000000000f');
select public.eliminar_mis_datos('eliminacion');
set request.jwt.claim.sub = '70000000-0000-4000-8000-000000000071';
select public.eliminar_mis_datos('eliminacion');   -- un tutor no se borra a sí mismo por error
reset role;
reset request.jwt.claim.sub;
do $$ begin
  if exists (select 1 from auth.users where id = 'f0000000-0000-4000-8000-00000000000f') then raise exception 'FALLO: quedó la cuenta con correo'; end if;
  if not exists (select 1 from auth.users where id = '70000000-0000-4000-8000-000000000071') then raise exception 'FALLO: se borró la cuenta de un tutor'; end if;
end $$;

-- ===== El dashboard incluye las métricas nuevas =====
set role authenticated;
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
do $$ declare r jsonb := public.dashboard_resumen(30); begin
  if not (r ? 'pulso' and r ? 'pasos' and r ? 'orientacion' and r ? 'derivaciones_contacto') then raise exception 'FALLO: faltan métricas en el dashboard'; end if;
  if (r -> 'orientacion' ->> 'supervisiones')::int <> 2 then raise exception 'FALLO: conteo de supervisiones'; end if;
end $$;
reset role;
\echo 'TODAS LAS PRUEBAS DE ROLES, ORIENTACIÓN Y DERIVACIONES PASARON'

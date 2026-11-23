-- =====================================================================
-- INACAP Health · Base de datos SQL (Supabase / PostgreSQL)
-- =====================================================================
-- Cómo usarlo: Supabase > SQL Editor > New query > pegar TODO > Run.
-- Se puede ejecutar más de una vez (es idempotente).
--
-- Qué guarda: datos ESTRUCTURADOS y RELACIONADOS, anonimizados.
--   usuarios ──< consentimientos
--            ──< registros_biometricos   (IMC y agua)
--            ──< resultados_scoff
--            ──< derivaciones
--   eventos_privacidad  (borrados/revocaciones, SIN id de usuario)
--   administradores     (quién puede ver el dashboard)
--
-- Nunca llegan aquí: nombre, patologías, alergias, medicamentos, peso,
-- estatura ni respuestas individuales del SCOFF (ver Anonimizacion.kt).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Tablas
-- ---------------------------------------------------------------------

create table if not exists public.administradores (
    user_id   uuid primary key references auth.users (id) on delete cascade,
    email     text,
    creado_en timestamptz not null default now()
);
comment on table public.administradores is
    'Usuarios (con correo y contraseña) que pueden ver el dashboard. Agregar a mano.';

create table if not exists public.usuarios (
    -- Mismo id que la cuenta ANÓNIMA de Supabase Auth: no hay correo ni nombre.
    id             uuid primary key default auth.uid()
                   references auth.users (id) on delete cascade,
    rango_edad     text not null default 'sin_dato'
                   check (rango_edad in ('<18', '18-24', '25-29', '30-39', '40+', 'sin_dato')),
    sexo           text not null default 'sin_dato'
                   check (sexo in ('Femenino', 'Masculino', 'Otro', 'Prefiero no decir', 'sin_dato')),
    version_app    text check (char_length(version_app) <= 20),
    plataforma     text not null default 'android' check (plataforma in ('android', 'ios', 'web')),
    creado_en      timestamptz not null default now(),
    actualizado_en timestamptz not null default now()
);
comment on table public.usuarios is 'Un registro por instalación que aceptó compartir datos anonimizados.';

create table if not exists public.consentimientos (
    id         uuid primary key,  -- generado en el teléfono (evita duplicados)
    usuario_id uuid not null default auth.uid()
               references public.usuarios (id) on delete cascade,
    tipo       text not null check (tipo in ('terminos', 'mayoria_edad', 'nube')),
    version    int  not null check (version between 1 and 1000),
    aceptado   boolean not null,
    fecha      timestamptz not null,
    recibido_en timestamptz not null default now()
);
comment on table public.consentimientos is 'Historial (auditoría) de aceptaciones y revocaciones.';
alter table public.consentimientos drop constraint if exists consentimientos_tipo_check;
alter table public.consentimientos add constraint consentimientos_tipo_check
    check (tipo in ('terminos', 'mayoria_edad', 'nube', 'orientacion'));

create table if not exists public.registros_biometricos (
    id              uuid primary key,
    usuario_id      uuid not null default auth.uid()
                    references public.usuarios (id) on delete cascade,
    tipo            text not null,
    imc             numeric(4, 1) check (imc between 5 and 100),
    categoria_imc   text check (categoria_imc in ('bajo_rango', 'en_rango', 'sobre_rango')),
    nivel_actividad text check (nivel_actividad in ('bajo', 'moderado', 'alto')),
    agua_ml         int check (agua_ml between 1 and 5000),
    fecha           timestamptz not null,
    recibido_en     timestamptz not null default now()
);
-- v2: pulso (HU-05) y pasos (HU-10). ALTER para bases creadas con la versión anterior.
alter table public.registros_biometricos add column if not exists frecuencia_cardiaca int check (frecuencia_cardiaca between 30 and 220);
alter table public.registros_biometricos add column if not exists pasos int check (pasos between 0 and 200000);
alter table public.registros_biometricos add column if not exists fuente text;
alter table public.registros_biometricos drop constraint if exists registros_biometricos_fuente_check;
alter table public.registros_biometricos add constraint registros_biometricos_fuente_check
    check (fuente in ('app', 'camara', 'health_connect', 'sensor', 'reloj'));
alter table public.registros_biometricos drop constraint if exists registros_biometricos_tipo_check;
alter table public.registros_biometricos add constraint registros_biometricos_tipo_check
    check (tipo in ('imc', 'agua', 'pulso', 'pasos'));
alter table public.registros_biometricos drop constraint if exists registro_coherente;
alter table public.registros_biometricos add constraint registro_coherente check (
    (tipo = 'imc'   and imc is not null and agua_ml is null and frecuencia_cardiaca is null and pasos is null) or
    (tipo = 'agua'  and agua_ml is not null and imc is null and frecuencia_cardiaca is null and pasos is null) or
    (tipo = 'pulso' and frecuencia_cardiaca is not null and imc is null and agua_ml is null and pasos is null) or
    (tipo = 'pasos' and pasos is not null and imc is null and agua_ml is null and frecuencia_cardiaca is null)
);

create table if not exists public.resultados_scoff (
    id                  uuid primary key,
    usuario_id          uuid not null default auth.uid()
                        references public.usuarios (id) on delete cascade,
    puntaje             int not null check (puntaje between 0 and 5),
    requiere_derivacion boolean not null,
    fecha               timestamptz not null,
    recibido_en         timestamptz not null default now()
);

create table if not exists public.derivaciones (
    id          uuid primary key,
    usuario_id  uuid not null default auth.uid()
                references public.usuarios (id) on delete cascade,
    canal       text not null check (canal in ('bienestar_presencial', 'bienestar_correo', 'cesfam')),
    codigo      text not null check (char_length(codigo) <= 20),
    fecha       timestamptz not null,
    recibido_en timestamptz not null default now()
);
-- v2 (HU-06 + E2E): datos de contacto CIFRADOS en el teléfono con la clave pública de
-- Bienestar. Aquí solo hay texto ilegible; se descifra en el computador de Bienestar.
alter table public.derivaciones add column if not exists contacto_cifrado jsonb
    check (contacto_cifrado is null or (
        jsonb_typeof(contacto_cifrado) = 'object'
        and contacto_cifrado ?& array['v', 'alg', 'ek', 'iv', 'ct']
        and octet_length(contacto_cifrado::text) < 8192));
alter table public.derivaciones add column if not exists estado text not null default 'pendiente';
alter table public.derivaciones drop constraint if exists derivaciones_estado_check;
alter table public.derivaciones add constraint derivaciones_estado_check
    check (estado in ('pendiente', 'en_contacto', 'atendida'));
alter table public.derivaciones add column if not exists gestionada_por uuid references auth.users (id) on delete set null;
alter table public.derivaciones add column if not exists actualizada_en timestamptz;

-- Sin usuario_id a propósito: solo cuenta CUÁNTOS borrados/revocaciones hubo.
create table if not exists public.eventos_privacidad (
    id    bigint generated always as identity primary key,
    tipo  text not null check (tipo in ('eliminacion', 'revocacion')),
    fecha timestamptz not null default now()
);

create index if not exists idx_consentimientos_usuario on public.consentimientos (usuario_id, tipo, fecha desc);
create index if not exists idx_registros_usuario_fecha  on public.registros_biometricos (usuario_id, fecha);
create index if not exists idx_registros_tipo_fecha     on public.registros_biometricos (tipo, fecha);
create index if not exists idx_scoff_fecha              on public.resultados_scoff (fecha);
create index if not exists idx_scoff_usuario            on public.resultados_scoff (usuario_id);
create index if not exists idx_derivaciones_fecha       on public.derivaciones (fecha);
create index if not exists idx_derivaciones_usuario     on public.derivaciones (usuario_id);
create index if not exists idx_usuarios_creado          on public.usuarios (creado_en);

-- ---------------------------------------------------------------------
-- 2. Funciones auxiliares
-- ---------------------------------------------------------------------

-- Versión inicial; en la sección v2 se redefine usando los roles del personal.
create or replace function public.es_admin()
returns boolean
language sql stable security definer
set search_path = public
as $$
    select exists (select 1 from public.administradores where user_id = auth.uid());
$$;

-- ---------------------------------------------------------------------
-- 3. Seguridad por fila (RLS)
--    · Cada instalación solo puede ver, escribir y borrar SUS filas.
--    · Los administradores solo pueden LEER (para el dashboard).
--    · Rol "anon" (sin sesión) no puede hacer nada.
-- ---------------------------------------------------------------------

alter table public.administradores       enable row level security;
alter table public.usuarios              enable row level security;
alter table public.consentimientos       enable row level security;
alter table public.registros_biometricos enable row level security;
alter table public.resultados_scoff      enable row level security;
alter table public.derivaciones          enable row level security;
alter table public.eventos_privacidad    enable row level security;

revoke all on public.administradores, public.usuarios, public.consentimientos,
    public.registros_biometricos, public.resultados_scoff, public.derivaciones,
    public.eventos_privacidad
    from anon, authenticated;

grant select on public.administradores to authenticated;
grant select, insert, update, delete on public.usuarios, public.consentimientos,
    public.registros_biometricos, public.resultados_scoff, public.derivaciones
    to authenticated;
grant select on public.eventos_privacidad to authenticated;

-- administradores: cada admin puede comprobar que lo es.
drop policy if exists admin_lee_su_fila on public.administradores;
create policy admin_lee_su_fila on public.administradores
    for select to authenticated using (user_id = auth.uid());

-- usuarios
drop policy if exists usuarios_select on public.usuarios;
drop policy if exists usuarios_insert on public.usuarios;
drop policy if exists usuarios_update on public.usuarios;
drop policy if exists usuarios_delete on public.usuarios;
create policy usuarios_select on public.usuarios for select to authenticated
    using (id = auth.uid() or public.es_admin());
create policy usuarios_insert on public.usuarios for insert to authenticated
    with check (id = auth.uid());
create policy usuarios_update on public.usuarios for update to authenticated
    using (id = auth.uid()) with check (id = auth.uid());
create policy usuarios_delete on public.usuarios for delete to authenticated
    using (id = auth.uid());

-- Tablas hijas: mismas reglas sobre usuario_id.
do $$
declare
    t text;
begin
    foreach t in array array['consentimientos', 'registros_biometricos', 'resultados_scoff', 'derivaciones']
    loop
        execute format('drop policy if exists %1$s_select on public.%1$s', t);
        execute format('drop policy if exists %1$s_insert on public.%1$s', t);
        execute format('drop policy if exists %1$s_update on public.%1$s', t);
        execute format('drop policy if exists %1$s_delete on public.%1$s', t);
        execute format('create policy %1$s_select on public.%1$s for select to authenticated
                        using (usuario_id = auth.uid() or public.es_admin())', t);
        execute format('create policy %1$s_insert on public.%1$s for insert to authenticated
                        with check (usuario_id = auth.uid())', t);
        execute format('create policy %1$s_update on public.%1$s for update to authenticated
                        using (usuario_id = auth.uid()) with check (usuario_id = auth.uid())', t);
        execute format('create policy %1$s_delete on public.%1$s for delete to authenticated
                        using (usuario_id = auth.uid())', t);
    end loop;
end $$;

-- eventos_privacidad: solo administradores leen; nadie escribe directo.
drop policy if exists privacidad_select on public.eventos_privacidad;
create policy privacidad_select on public.eventos_privacidad for select to authenticated
    using (public.es_admin());

-- Los consentimientos son un historial: no se pueden modificar, solo agregar.
create or replace function public.bloquear_edicion_consentimiento()
returns trigger language plpgsql as $$
begin
    if new.tipo is distinct from old.tipo or new.version is distinct from old.version
       or new.aceptado is distinct from old.aceptado or new.fecha is distinct from old.fecha then
        raise exception 'El historial de consentimientos no se puede modificar';
    end if;
    return new;
end $$;
drop trigger if exists consentimientos_inmutables on public.consentimientos;
create trigger consentimientos_inmutables before update on public.consentimientos
    for each row execute function public.bloquear_edicion_consentimiento();

-- actualizado_en automático
create or replace function public.tocar_actualizado_en()
returns trigger language plpgsql as $$
begin
    new.actualizado_en := now();
    return new;
end $$;
drop trigger if exists usuarios_actualizado_en on public.usuarios;
create trigger usuarios_actualizado_en before update on public.usuarios
    for each row execute function public.tocar_actualizado_en();

-- 4. Derecho de supresión: ver eliminar_mis_datos() en la sección v2 (más abajo).

-- ---------------------------------------------------------------------
-- 5. Retención: datos con más de 24 meses se eliminan.
--    Ejecutar a mano o programar con pg_cron (ver backend/README.md).
-- ---------------------------------------------------------------------

create or replace function public.purgar_datos_antiguos(meses int default 24)
returns jsonb
language plpgsql volatile security definer
set search_path = public
as $$
declare
    limite timestamptz := now() - make_interval(months => meses);
    n_reg int; n_scoff int; n_der int; n_usr int;
begin
    if auth.uid() is not null and not public.es_admin() then
        raise exception 'Acceso solo para administradores' using errcode = '42501';
    end if;
    delete from public.registros_biometricos where fecha < limite;  get diagnostics n_reg = row_count;
    delete from public.resultados_scoff      where fecha < limite;  get diagnostics n_scoff = row_count;
    delete from public.derivaciones          where fecha < limite;  get diagnostics n_der = row_count;
    -- Usuarios sin actividad en el período (y sus consentimientos, en cascada).
    delete from public.usuarios where actualizado_en < limite
        and not exists (select 1 from public.registros_biometricos r where r.usuario_id = usuarios.id)
        and not exists (select 1 from public.resultados_scoff s where s.usuario_id = usuarios.id);
    get diagnostics n_usr = row_count;
    return jsonb_build_object('registros', n_reg, 'scoff', n_scoff, 'derivaciones', n_der, 'usuarios', n_usr);
end $$;

revoke all on function public.purgar_datos_antiguos(int) from public, anon;
grant execute on function public.purgar_datos_antiguos(int) to authenticated;


-- =====================================================================
-- v2 · Roles del personal (HU-13 "login con roles"), orientación entre
-- pares supervisada y gestión de derivaciones.
-- El personal inicia sesión con correo y contraseña (Supabase emite un JWT);
-- su rol se lee de la tabla "personal", nunca del cliente.
-- =====================================================================

create table if not exists public.personal (
    user_id        uuid primary key references auth.users (id) on delete cascade,
    rol            text not null check (rol in ('tutor', 'docente', 'bienestar', 'admin')),
    nombre_visible text not null check (char_length(nombre_visible) between 2 and 40),
    activo         boolean not null default true,
    creado_en      timestamptz not null default now()
);
comment on table public.personal is
    'Tutores pares, docentes supervisores, equipo de Bienestar y administradores. Agregar a mano (ver README).';

create or replace function public.rol_actual()
returns text
language sql stable security definer
set search_path = public
as $$
    select coalesce(
        (select rol from public.personal where user_id = auth.uid() and activo),
        case when exists (select 1 from public.administradores where user_id = auth.uid()) then 'admin' end
    );
$$;

create or replace function public.tiene_rol(variadic roles text[])
returns boolean
language sql stable security definer
set search_path = public
as $$
    -- Quien está en "administradores" además de tener otro rol (p. ej. bienestar) también cuenta como admin.
    select coalesce(public.rol_actual() = any (roles), false)
        or ('admin' = any (roles)
            and exists (select 1 from public.personal p where p.user_id = auth.uid() and p.activo)
            and exists (select 1 from public.administradores a where a.user_id = auth.uid()));
$$;

create or replace function public.es_admin()
returns boolean
language sql stable security definer
set search_path = public
as $$
    select public.tiene_rol('admin');
$$;

alter table public.personal enable row level security;
revoke all on public.personal from anon, authenticated;
grant select on public.personal to authenticated;
drop policy if exists personal_select on public.personal;
create policy personal_select on public.personal for select to authenticated
    using (user_id = auth.uid() or public.tiene_rol('admin', 'docente'));

grant execute on function public.rol_actual() to authenticated;

-- ---------- Derivaciones: Bienestar las ve y gestiona su estado ----------

drop policy if exists derivaciones_select on public.derivaciones;
create policy derivaciones_select on public.derivaciones for select to authenticated
    using (usuario_id = auth.uid() or public.tiene_rol('bienestar', 'admin'));

create or replace function public.gestionar_derivacion(p_id uuid, p_estado text)
returns void
language plpgsql volatile security definer
set search_path = public
as $$
begin
    if not public.tiene_rol('bienestar') then
        raise exception 'Solo el equipo de Bienestar puede gestionar derivaciones' using errcode = '42501';
    end if;
    if p_estado not in ('pendiente', 'en_contacto', 'atendida') then
        raise exception 'Estado inválido';
    end if;
    update public.derivaciones
       set estado = p_estado, gestionada_por = auth.uid(), actualizada_en = now()
     where id = p_id;
end $$;
revoke all on function public.gestionar_derivacion(uuid, text) from public, anon;
grant execute on function public.gestionar_derivacion(uuid, text) to authenticated;

-- ---------- Orientación entre pares (HU-13) ----------

create table if not exists public.conversaciones (
    id             uuid primary key default gen_random_uuid(),
    usuario_id     uuid not null default auth.uid() references public.usuarios (id) on delete cascade,
    tema           text not null check (tema in ('alimentacion', 'hidratacion', 'actividad', 'habitos', 'otro')),
    estado         text not null default 'abierta' check (estado in ('abierta', 'cerrada', 'derivada')),
    tutor_id       uuid references auth.users (id) on delete set null,
    creada_en      timestamptz not null default now(),
    actualizada_en timestamptz not null default now()
);

create table if not exists public.mensajes (
    id              uuid primary key default gen_random_uuid(),
    conversacion_id uuid not null references public.conversaciones (id) on delete cascade,
    autor_id        uuid not null default auth.uid(),
    rol_autor       text not null check (rol_autor in ('estudiante', 'tutor', 'docente', 'sistema')),
    texto           text not null check (char_length(texto) between 1 and 1000),
    creado_en       timestamptz not null default now()
);

-- Bitácora de supervisión: se conserva (sin contenido) aunque se borre la conversación.
create table if not exists public.supervision (
    id              bigint generated always as identity primary key,
    conversacion_id uuid references public.conversaciones (id) on delete set null,
    docente_id      uuid not null default auth.uid() references auth.users (id),
    accion          text not null check (accion in ('revisada', 'observacion', 'derivada', 'cerrada', 'reabierta')),
    nota            text check (char_length(nota) <= 1000),
    creada_en       timestamptz not null default now()
);

create index if not exists idx_conversaciones_usuario on public.conversaciones (usuario_id);
create index if not exists idx_conversaciones_estado  on public.conversaciones (estado, actualizada_en desc);
create index if not exists idx_mensajes_conversacion  on public.mensajes (conversacion_id, creado_en);
create index if not exists idx_supervision_conv       on public.supervision (conversacion_id);

alter table public.conversaciones enable row level security;
alter table public.mensajes       enable row level security;
alter table public.supervision    enable row level security;
revoke all on public.conversaciones, public.mensajes, public.supervision from anon, authenticated;
grant select, insert on public.conversaciones, public.mensajes to authenticated;
grant select on public.supervision to authenticated;

-- Quién ve una conversación: la persona que la abrió, el tutor asignado (o cualquier
-- tutor si está abierta y sin asignar), y los docentes supervisores.
create or replace function public.puede_ver_conversacion(c public.conversaciones)
returns boolean
language sql stable
as $$
    select c.usuario_id = auth.uid()
        or public.tiene_rol('docente')
        or (public.tiene_rol('tutor') and (c.tutor_id = auth.uid() or (c.tutor_id is null and c.estado = 'abierta')));
$$;

drop policy if exists conversaciones_select on public.conversaciones;
drop policy if exists conversaciones_insert on public.conversaciones;
create policy conversaciones_select on public.conversaciones for select to authenticated
    using (public.puede_ver_conversacion(conversaciones));
create policy conversaciones_insert on public.conversaciones for insert to authenticated
    with check (usuario_id = auth.uid() and estado = 'abierta' and tutor_id is null and public.rol_actual() is null);

drop policy if exists mensajes_select on public.mensajes;
drop policy if exists mensajes_insert on public.mensajes;
create policy mensajes_select on public.mensajes for select to authenticated
    using (exists (select 1 from public.conversaciones c where c.id = conversacion_id));  -- aplica la RLS de conversaciones
create policy mensajes_insert on public.mensajes for insert to authenticated
    with check (
        autor_id = auth.uid()
        and exists (
            select 1 from public.conversaciones c
             where c.id = conversacion_id and c.estado = 'abierta'
               and (
                    (rol_autor = 'estudiante' and c.usuario_id = auth.uid())
                 or (rol_autor = 'tutor'      and public.tiene_rol('tutor') and c.tutor_id = auth.uid())
                 or (rol_autor = 'docente'    and public.tiene_rol('docente'))
               )
        )
    );

drop policy if exists supervision_select on public.supervision;
create policy supervision_select on public.supervision for select to authenticated
    using (public.tiene_rol('docente', 'admin'));

create or replace function public.tocar_conversacion()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    update public.conversaciones set actualizada_en = now() where id = new.conversacion_id;
    return new;
end $$;
drop trigger if exists mensajes_tocan_conversacion on public.mensajes;
create trigger mensajes_tocan_conversacion after insert on public.mensajes
    for each row execute function public.tocar_conversacion();

-- Un tutor toma una conversación abierta y sin asignar.
create or replace function public.tomar_conversacion(p_id uuid)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
    if not public.tiene_rol('tutor') then raise exception 'Solo tutores pares' using errcode = '42501'; end if;
    update public.conversaciones set tutor_id = auth.uid(), actualizada_en = now()
     where id = p_id and estado = 'abierta' and tutor_id is null;
    if not found then raise exception 'La conversación ya fue tomada o no está abierta'; end if;
end $$;

-- Cambios de estado permitidos según el rol. "derivada" deja un mensaje que
-- recomienda apoyo profesional (los casos con señales de TCA no se atienden entre pares).
create or replace function public.cambiar_estado_conversacion(p_id uuid, p_estado text, p_nota text default null)
returns void language plpgsql volatile security definer set search_path = public as $$
declare
    c public.conversaciones;
    rol text := public.rol_actual();
begin
    select * into c from public.conversaciones where id = p_id;
    if not found then raise exception 'No existe'; end if;
    if p_estado not in ('abierta', 'cerrada', 'derivada') then raise exception 'Estado inválido'; end if;
    if not (
        (rol is null and c.usuario_id = auth.uid() and p_estado = 'cerrada')
        or (rol = 'tutor' and c.tutor_id = auth.uid() and p_estado in ('cerrada', 'derivada'))
        or (rol = 'docente')
    ) then
        raise exception 'No tienes permiso para este cambio' using errcode = '42501';
    end if;
    update public.conversaciones set estado = p_estado, actualizada_en = now() where id = p_id;
    if p_estado = 'derivada' then
        insert into public.mensajes (conversacion_id, autor_id, rol_autor, texto) values (p_id, auth.uid(), 'sistema',
            'Para este tema te recomendamos conversar con un profesional. En la app, entra a Bienestar > Apoyo profesional: es confidencial.');
    end if;
    if rol = 'docente' then
        insert into public.supervision (conversacion_id, accion, nota)
        values (p_id, case p_estado when 'abierta' then 'reabierta' else p_estado end, p_nota);
    end if;
end $$;

-- El docente deja constancia de su revisión u observaciones.
create or replace function public.supervisar(p_id uuid, p_accion text, p_nota text default null)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
    if not public.tiene_rol('docente') then raise exception 'Solo docentes supervisores' using errcode = '42501'; end if;
    if p_accion in ('derivada', 'cerrada', 'reabierta') then
        perform public.cambiar_estado_conversacion(p_id, case p_accion when 'reabierta' then 'abierta' else p_accion end, p_nota);
    else
        insert into public.supervision (conversacion_id, accion, nota) values (p_id, p_accion, p_nota);
    end if;
end $$;

-- La persona borra sus conversaciones cuando quiera.
create or replace function public.borrar_mis_conversaciones()
returns void language sql volatile security definer set search_path = public as $$
    delete from public.conversaciones where usuario_id = auth.uid();
$$;

revoke all on function public.tomar_conversacion(uuid), public.cambiar_estado_conversacion(uuid, text, text),
    public.supervisar(uuid, text, text), public.borrar_mis_conversaciones() from public, anon;
grant execute on function public.tomar_conversacion(uuid), public.cambiar_estado_conversacion(uuid, text, text),
    public.supervisar(uuid, text, text), public.borrar_mis_conversaciones() to authenticated;

-- ---------- Derecho de supresión (v2) ----------
-- "revocacion": borra las estadísticas pero conserva las derivaciones con contacto y el chat,
-- que la persona pidió por separado. "eliminacion": borra todo, incluida la cuenta anónima.
create or replace function public.eliminar_mis_datos(motivo text default 'eliminacion')
returns void
language plpgsql volatile security definer
set search_path = public, auth
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'Se requiere sesión' using errcode = '28000';
    end if;
    if motivo = 'revocacion' then
        insert into public.eventos_privacidad (tipo) values ('revocacion');
        delete from public.registros_biometricos where usuario_id = uid;
        delete from public.resultados_scoff where usuario_id = uid;
        delete from public.consentimientos where usuario_id = uid;
        delete from public.derivaciones where usuario_id = uid and contacto_cifrado is null;
        update public.usuarios set rango_edad = 'sin_dato', sexo = 'sin_dato' where id = uid;
    else
        insert into public.eventos_privacidad (tipo) values ('eliminacion');
        delete from public.usuarios where id = uid;  -- borra en cascada todo lo demás
        -- Borra la cuenta (anónima o con correo), nunca la de alguien del personal.
        delete from auth.users
         where id = uid
           and not exists (select 1 from public.personal where user_id = uid)
           and not exists (select 1 from public.administradores where user_id = uid);
    end if;
end $$;
revoke all on function public.eliminar_mis_datos(text) from public, anon;
grant execute on function public.eliminar_mis_datos(text) to authenticated;

-- ---------------------------------------------------------------------
-- 6. Dashboard: devuelve SOLO cifras agregadas (nunca filas de personas).
--    Las fechas se agrupan por día en hora de Chile.
-- ---------------------------------------------------------------------

create or replace function public.dashboard_resumen(dias int default 30)
returns jsonb
language plpgsql stable security definer
set search_path = public
as $$
declare
    d     int := greatest(1, least(coalesce(dias, 30), 365));
    desde timestamptz := now() - make_interval(days => d);
    zona  text := 'America/Santiago';
    resultado jsonb;
begin
    if not public.es_admin() then
        raise exception 'Acceso solo para administradores' using errcode = '42501';
    end if;

    with
    imc_ultimo as (   -- último IMC de cada usuario en el período
        select distinct on (usuario_id) usuario_id, imc, categoria_imc
        from public.registros_biometricos
        where tipo = 'imc' and fecha >= desde
        order by usuario_id, fecha desc
    ),
    agua_dia as (     -- total de agua por usuario y día
        select usuario_id, (fecha at time zone zona)::date as dia, sum(agua_ml) as ml
        from public.registros_biometricos
        where tipo = 'agua' and fecha >= desde
        group by 1, 2
    ),
    terminos_ultimo as (
        select distinct on (usuario_id) usuario_id, version, aceptado
        from public.consentimientos
        where tipo = 'terminos'
        order by usuario_id, fecha desc
    ),
    activos as (
        select usuario_id from public.registros_biometricos where fecha >= desde
        union
        select usuario_id from public.resultados_scoff where fecha >= desde
        union
        select usuario_id from public.derivaciones where fecha >= desde
    ),
    dias_serie as (
        select generate_series(
            (desde at time zone zona)::date, (now() at time zone zona)::date, interval '1 day'
        )::date as dia
    ),
    serie as (
        select
            s.dia,
            (select count(*) from public.usuarios u where (u.creado_en at time zone zona)::date = s.dia) as nuevos_usuarios,
            (select count(*) from public.registros_biometricos r where r.tipo = 'imc'  and (r.fecha at time zone zona)::date = s.dia) as registros_imc,
            (select count(*) from public.registros_biometricos r where r.tipo = 'agua' and (r.fecha at time zone zona)::date = s.dia) as registros_agua,
            (select count(*) from public.resultados_scoff x where (x.fecha at time zone zona)::date = s.dia) as tests_scoff,
            (select count(*) from public.derivaciones x where (x.fecha at time zone zona)::date = s.dia) as derivaciones
        from dias_serie s
    )
    select jsonb_build_object(
        'generado_en', now(),
        'periodo_dias', d,
        'usuarios', jsonb_build_object(
            'total',   (select count(*) from public.usuarios),
            'nuevos',  (select count(*) from public.usuarios where creado_en >= desde),
            'activos', (select count(*) from activos)
        ),
        'demografia', jsonb_build_object(
            'rango_edad', (select coalesce(jsonb_object_agg(rango_edad, n), '{}'::jsonb)
                           from (select rango_edad, count(*) n from public.usuarios group by 1) t),
            'sexo',       (select coalesce(jsonb_object_agg(sexo, n), '{}'::jsonb)
                           from (select sexo, count(*) n from public.usuarios group by 1) t)
        ),
        'imc', jsonb_build_object(
            'registros',  (select count(*) from public.registros_biometricos where tipo = 'imc' and fecha >= desde),
            'usuarios',   (select count(*) from imc_ultimo),
            'promedio',   (select round(avg(imc), 1) from imc_ultimo),
            'categorias', (select coalesce(jsonb_object_agg(categoria_imc, n), '{}'::jsonb)
                           from (select categoria_imc, count(*) n from imc_ultimo group by 1) t)
        ),
        'agua', jsonb_build_object(
            'registros',          (select count(*) from public.registros_biometricos where tipo = 'agua' and fecha >= desde),
            'promedio_diario_ml', (select coalesce(round(avg(ml)), 0) from agua_dia),
            'dias_registrados',   (select count(*) from agua_dia)
        ),
        'scoff', jsonb_build_object(
            'tests',              (select count(*) from public.resultados_scoff where fecha >= desde),
            'sugiere_derivacion', (select count(*) from public.resultados_scoff where fecha >= desde and requiere_derivacion),
            'puntajes',           (select coalesce(jsonb_object_agg(puntaje::text, n), '{}'::jsonb)
                                   from (select puntaje, count(*) n from public.resultados_scoff
                                         where fecha >= desde group by 1) t)
        ),
        'derivaciones', jsonb_build_object(
            'total',     (select count(*) from public.derivaciones where fecha >= desde),
            'por_canal', (select coalesce(jsonb_object_agg(canal, n), '{}'::jsonb)
                          from (select canal, count(*) n from public.derivaciones
                                where fecha >= desde group by 1) t)
        ),
        'consentimientos', jsonb_build_object(
            'terminos_por_version', (select coalesce(jsonb_object_agg('v' || version, n), '{}'::jsonb)
                                     from (select version, count(*) n from terminos_ultimo
                                           where aceptado group by 1) t),
            'registrados_periodo',  (select count(*) from public.consentimientos where fecha >= desde)
        ),
        'privacidad', jsonb_build_object(
            'eliminaciones',       (select count(*) from public.eventos_privacidad where tipo = 'eliminacion' and fecha >= desde),
            'revocaciones',        (select count(*) from public.eventos_privacidad where tipo = 'revocacion'  and fecha >= desde),
            'eliminaciones_total', (select count(*) from public.eventos_privacidad where tipo = 'eliminacion'),
            'revocaciones_total',  (select count(*) from public.eventos_privacidad where tipo = 'revocacion')
        ),
        'pulso', jsonb_build_object(
            'mediciones', (select count(*) from public.registros_biometricos where tipo = 'pulso' and fecha >= desde),
            'promedio',   (select round(avg(frecuencia_cardiaca)) from public.registros_biometricos where tipo = 'pulso' and fecha >= desde),
            'por_fuente', (select coalesce(jsonb_object_agg(coalesce(fuente, 'app'), n), '{}'::jsonb)
                           from (select fuente, count(*) n from public.registros_biometricos
                                 where tipo = 'pulso' and fecha >= desde group by 1) t)
        ),
        'pasos', jsonb_build_object(
            'dias_registrados', (select count(*) from public.registros_biometricos where tipo = 'pasos' and fecha >= desde),
            'promedio_diario',  (select coalesce(round(avg(pasos)), 0) from public.registros_biometricos where tipo = 'pasos' and fecha >= desde)
        ),
        'orientacion', jsonb_build_object(
            'conversaciones', (select count(*) from public.conversaciones where creada_en >= desde),
            'abiertas',       (select count(*) from public.conversaciones where estado = 'abierta'),
            'sin_tutor',      (select count(*) from public.conversaciones where estado = 'abierta' and tutor_id is null),
            'derivadas',      (select count(*) from public.conversaciones where estado = 'derivada' and actualizada_en >= desde),
            'mensajes',       (select count(*) from public.mensajes where creado_en >= desde),
            'supervisiones',  (select count(*) from public.supervision where creada_en >= desde)
        ),
        'derivaciones_contacto', jsonb_build_object(
            'con_contacto', (select count(*) from public.derivaciones where contacto_cifrado is not null and fecha >= desde),
            'pendientes',   (select count(*) from public.derivaciones where contacto_cifrado is not null and estado = 'pendiente')
        ),
        'retencion', jsonb_build_object(
            'meses', 24,
            'registros_vencidos',
                (select count(*) from public.registros_biometricos where fecha < now() - interval '24 months')
              + (select count(*) from public.resultados_scoff where fecha < now() - interval '24 months')
              + (select count(*) from public.derivaciones where fecha < now() - interval '24 months')
        ),
        'serie', (select coalesce(jsonb_agg(to_jsonb(serie) order by dia), '[]'::jsonb) from serie)
    ) into resultado;

    return resultado;
end $$;

revoke all on function public.dashboard_resumen(int) from public, anon;
grant execute on function public.dashboard_resumen(int) to authenticated;

-- =====================================================================
-- v3 · Atención profesional, límites de uso (por persona y por IP),
-- limpieza y validación en el servidor, gestión del equipo y auditoría.
-- =====================================================================

-- ---------- Utilidades de seguridad ----------

-- Error con código HTTP propio para PostgREST (p. ej. 429 o 403).
create or replace function public.rechazar(estado int, mensaje text)
returns void language plpgsql as $$
begin
    raise sqlstate 'PGRST' using
        message = json_build_object('code', 'INH' || estado, 'message', mensaje)::text,
        detail  = json_build_object('status', estado, 'headers', json_build_object())::text;
end $$;

-- Quita caracteres de control e invisibles, recorta y limita el largo.
create or replace function public.limpiar_texto(t text, maximo int)
returns text language sql immutable as $$
    select nullif(
        left(
            btrim(
                regexp_replace(
                    regexp_replace(coalesce(t, ''), '[\x00-\x08\x0B\x0C\x0E-\x1F\x7F\u200b-\u200f\u2028-\u202e\u2060-\u2064\ufeff]', '', 'g'),
                    '\n{3,}', E'\n\n', 'g')
            ), maximo),
        '');
$$;

-- ---------- Auditoría (acciones del personal) ----------

create table if not exists public.auditoria (
    id      bigint generated always as identity primary key,
    actor   uuid,
    accion  text not null check (char_length(accion) <= 60),
    detalle jsonb not null default '{}'::jsonb,
    fecha   timestamptz not null default now()
);
alter table public.auditoria enable row level security;
revoke all on public.auditoria from anon, authenticated;
grant select on public.auditoria to authenticated;
drop policy if exists auditoria_select on public.auditoria;
create policy auditoria_select on public.auditoria for select to authenticated using (public.tiene_rol('admin'));

create or replace function public.auditar(accion text, detalle jsonb default '{}'::jsonb)
returns void language sql volatile security definer set search_path = public as $$
    insert into public.auditoria (actor, accion, detalle) values (auth.uid(), accion, coalesce(detalle, '{}'::jsonb));
$$;
revoke all on function public.auditar(text, jsonb) from public, anon, authenticated;

-- ---------- Límite de uso por persona (rate limiting) ----------

create unlogged table if not exists public.limites_uso (
    clave   text not null,                 -- usuario o IP
    accion  text not null,
    ventana timestamptz not null,
    cuenta  int not null default 0,
    primary key (clave, accion, ventana)
);
alter table public.limites_uso enable row level security;
revoke all on public.limites_uso from anon, authenticated;

-- true si la clave ya superó [maximo] acciones en la ventana de [segundos].
create or replace function public.superar_limite(p_clave text, p_accion text, p_maximo int, p_segundos int)
returns boolean language plpgsql volatile security definer set search_path = public as $$
declare
    v timestamptz := to_timestamp(floor(extract(epoch from now()) / p_segundos) * p_segundos);
    n int;
begin
    insert into public.limites_uso as l (clave, accion, ventana, cuenta) values (p_clave, p_accion, v, 1)
    on conflict (clave, accion, ventana) do update set cuenta = l.cuenta + 1
    returning l.cuenta into n;
    if random() < 0.02 then delete from public.limites_uso where ventana < now() - interval '1 day'; end if;
    return n > p_maximo;
end $$;
revoke all on function public.superar_limite(text, text, int, int) from public, anon, authenticated;

-- Límites por persona para cada tipo de dato (ajustables aquí).
create or replace function public.limitar_por_usuario()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    maximo int; segundos int;
begin
    case tg_table_name
        when 'mensajes'              then maximo := 30;  segundos := 60;      -- 30 mensajes por minuto
        when 'conversaciones'        then maximo := 5;   segundos := 3600;    -- 5 consultas nuevas por hora
        when 'derivaciones'          then maximo := 5;   segundos := 86400;   -- 5 derivaciones por día
        when 'registros_biometricos' then maximo := 500; segundos := 3600;
        when 'resultados_scoff'      then maximo := 20;  segundos := 86400;
        when 'consentimientos'       then maximo := 100; segundos := 3600;
        else maximo := 1000; segundos := 3600;
    end case;
    if auth.uid() is not null and public.superar_limite(auth.uid()::text, tg_table_name, maximo, segundos) then
        perform public.rechazar(429, 'Demasiadas solicitudes seguidas. Intenta de nuevo en unos minutos.');
    end if;
    return new;
end $$;

do $$
declare t text;
begin
    foreach t in array array['mensajes', 'conversaciones', 'derivaciones', 'registros_biometricos', 'resultados_scoff', 'consentimientos']
    loop
        execute format('drop trigger if exists %1$s_limite on public.%1$s', t);
        execute format('create trigger %1$s_limite before insert on public.%1$s for each row execute function public.limitar_por_usuario()', t);
    end loop;
end $$;

-- ---------- Límite y bloqueo por IP (antes de CADA petición a la API) ----------

create table if not exists public.ips_bloqueadas (
    ip       text primary key check (char_length(ip) <= 64),
    hasta    timestamptz not null,
    motivo   text check (char_length(motivo) <= 200),
    creado   timestamptz not null default now()
);
alter table public.ips_bloqueadas enable row level security;
revoke all on public.ips_bloqueadas from anon, authenticated;
grant select on public.ips_bloqueadas to authenticated;
drop policy if exists ips_select on public.ips_bloqueadas;
create policy ips_select on public.ips_bloqueadas for select to authenticated using (public.tiene_rol('admin'));

create unlogged table if not exists public.rechazos (
    id     bigint generated always as identity primary key,
    ip     text,
    motivo text,
    fecha  timestamptz not null default now()
);
alter table public.rechazos enable row level security;
revoke all on public.rechazos from anon, authenticated;

create or replace function public.ip_cliente()
returns text language sql stable as $$
    select coalesce(
        nullif(current_setting('request.headers', true)::json ->> 'cf-connecting-ip', ''),
        nullif(btrim(split_part(current_setting('request.headers', true)::json ->> 'x-forwarded-for', ',', 1)), ''),
        'desconocida');
$$;

-- PostgREST la llama antes de cada petición (ver "Activar" más abajo).
create or replace function public.antes_de_cada_peticion()
returns void language plpgsql volatile security definer set search_path = public as $$
declare
    v_ip text := public.ip_cliente();
    escritura boolean := coalesce(current_setting('request.method', true), 'GET') <> 'GET'
                         and current_setting('transaction_read_only', true) = 'off';
begin
    if exists (select 1 from public.ips_bloqueadas b where b.ip = v_ip and b.hasta > now()) then
        perform public.rechazar(403, 'Acceso bloqueado temporalmente.');
    end if;
    -- Las lecturas corren en transacciones de solo lectura: se cuentan solo las escrituras.
    if escritura and v_ip <> 'desconocida' then
        if public.superar_limite(v_ip, 'peticiones', 120, 60) then          -- 120 escrituras por minuto por IP
            insert into public.rechazos (ip, motivo) values (v_ip, 'limite_ip');
            -- Quien insiste (más de 10 rechazos en 10 min) queda bloqueado 15 minutos.
            if (select count(*) from public.rechazos r where r.ip = v_ip and r.fecha > now() - interval '10 minutes') > 10 then
                insert into public.ips_bloqueadas (ip, hasta, motivo) values (v_ip, now() + interval '15 minutes', 'Bloqueo automático por exceso de peticiones')
                on conflict (ip) do update set hasta = excluded.hasta, motivo = excluded.motivo;
            end if;
            perform public.rechazar(429, 'Demasiadas solicitudes desde tu red. Intenta en un minuto.');
        end if;
    end if;
end $$;
grant execute on function public.antes_de_cada_peticion() to anon, authenticated;

-- Activar el control por IP en la API (Supabase lo permite con el rol authenticator).
do $$ begin
    alter role authenticator set pgrst.db_pre_request = 'public.antes_de_cada_peticion';
exception when others then
    raise notice 'No se pudo activar db_pre_request: %', sqlerrm;
end $$;
notify pgrst, 'reload config';

-- ---------- Limpieza y validación en el servidor ----------

create or replace function public.sanear_entrada()
returns trigger language plpgsql as $$
begin
    case tg_table_name
        when 'mensajes' then
            new.texto := public.limpiar_texto(new.texto, 1000);
            if new.texto is null then raise exception 'El mensaje está vacío' using errcode = '22023'; end if;
        when 'supervision' then
            new.nota := public.limpiar_texto(new.nota, 1000);
        when 'personal' then
            new.nombre_visible := public.limpiar_texto(new.nombre_visible, 40);
            if new.nombre_visible is null or new.nombre_visible !~ '^[[:alpha:][:space:].''-]{2,40}$' then
                raise exception 'Nombre visible inválido' using errcode = '22023';
            end if;
        when 'derivaciones' then
            new.codigo := upper(btrim(new.codigo));
            if new.codigo !~ '^INH-[A-Z0-9]{6}$' then raise exception 'Código de derivación inválido' using errcode = '22023'; end if;
        when 'usuarios' then
            if new.version_app is not null and new.version_app !~ '^[0-9]{1,3}(\.[0-9]{1,3}){0,3}$' then
                raise exception 'Versión inválida' using errcode = '22023';
            end if;
        else null;
    end case;
    return new;
end $$;

do $$
declare t text;
begin
    foreach t in array array['mensajes', 'supervision', 'personal', 'derivaciones', 'usuarios']
    loop
        execute format('drop trigger if exists %1$s_sanear on public.%1$s', t);
        execute format('create trigger %1$s_sanear before insert or update on public.%1$s for each row execute function public.sanear_entrada()', t);
    end loop;
end $$;

-- Fechas imposibles (futuro lejano o antes de la app) se rechazan.
do $$
declare t text;
begin
    foreach t in array array['registros_biometricos', 'resultados_scoff', 'derivaciones', 'consentimientos']
    loop
        execute format('alter table public.%1$s drop constraint if exists %1$s_fecha_valida', t);
        execute format('alter table public.%1$s add constraint %1$s_fecha_valida check (fecha > ''2024-01-01'' and fecha < now() + interval ''1 day'') not valid', t);
    end loop;
end $$;

-- ---------- Atención profesional (chat desde una derivación) ----------

alter table public.conversaciones add column if not exists tipo text not null default 'orientacion';
alter table public.conversaciones drop constraint if exists conversaciones_tipo_check;
alter table public.conversaciones add constraint conversaciones_tipo_check check (tipo in ('orientacion', 'atencion'));
alter table public.conversaciones add column if not exists derivacion_id uuid references public.derivaciones (id) on delete set null;
alter table public.conversaciones add column if not exists profesional_id uuid references auth.users (id) on delete set null;
alter table public.conversaciones drop constraint if exists conversaciones_tema_check;
alter table public.conversaciones add constraint conversaciones_tema_check
    check (tema in ('alimentacion', 'hidratacion', 'actividad', 'habitos', 'otro', 'apoyo_profesional'));
alter table public.mensajes drop constraint if exists mensajes_rol_autor_check;
alter table public.mensajes add constraint mensajes_rol_autor_check
    check (rol_autor in ('estudiante', 'tutor', 'docente', 'profesional', 'sistema'));

-- Orientación: estudiante, tutor asignado (o libre) y docentes.
-- Atención: solo la persona y el profesional que la atiende (confidencialidad clínica).
create or replace function public.puede_ver_conversacion(c public.conversaciones)
returns boolean language sql stable as $$
    select c.usuario_id = auth.uid()
        or (c.tipo = 'orientacion' and (
               public.tiene_rol('docente')
            or (public.tiene_rol('tutor') and (c.tutor_id = auth.uid() or (c.tutor_id is null and c.estado = 'abierta')))))
        or (c.tipo = 'atencion' and public.tiene_rol('bienestar') and c.profesional_id = auth.uid());
$$;

drop policy if exists conversaciones_insert on public.conversaciones;
create policy conversaciones_insert on public.conversaciones for insert to authenticated
    with check (usuario_id = auth.uid() and estado = 'abierta' and tutor_id is null and tipo = 'orientacion'
                and profesional_id is null and public.rol_actual() is null);

drop policy if exists mensajes_insert on public.mensajes;
create policy mensajes_insert on public.mensajes for insert to authenticated
    with check (
        autor_id = auth.uid()
        and exists (
            select 1 from public.conversaciones c
             where c.id = conversacion_id and c.estado = 'abierta'
               and (
                    (rol_autor = 'estudiante'  and c.usuario_id = auth.uid())
                 or (rol_autor = 'tutor'       and c.tipo = 'orientacion' and public.tiene_rol('tutor') and c.tutor_id = auth.uid())
                 or (rol_autor = 'docente'     and c.tipo = 'orientacion' and public.tiene_rol('docente'))
                 or (rol_autor = 'profesional' and c.tipo = 'atencion' and public.tiene_rol('bienestar') and c.profesional_id = auth.uid())
               )
        )
    );

-- El profesional abre (o retoma) el chat de atención de una derivación.
create or replace function public.iniciar_atencion(p_derivacion uuid)
returns uuid language plpgsql volatile security definer set search_path = public as $$
declare
    d public.derivaciones;
    conv uuid;
begin
    if not public.tiene_rol('bienestar') then perform public.rechazar(403, 'Solo profesionales de Bienestar'); end if;
    select * into d from public.derivaciones where id = p_derivacion;
    if not found then raise exception 'La derivación no existe'; end if;
    select id into conv from public.conversaciones
     where derivacion_id = p_derivacion and tipo = 'atencion' and estado = 'abierta' limit 1;
    if conv is null then
        insert into public.conversaciones (usuario_id, tema, tipo, derivacion_id, profesional_id)
        values (d.usuario_id, 'apoyo_profesional', 'atencion', p_derivacion, auth.uid())
        returning id into conv;
        insert into public.mensajes (conversacion_id, autor_id, rol_autor, texto) values (conv, auth.uid(), 'sistema',
            'Un profesional de Bienestar Estudiantil abrió esta conversación por tu solicitud de apoyo ' || d.codigo || '. Es confidencial: solo tú y el profesional la ven.');
        update public.derivaciones set estado = 'en_contacto', gestionada_por = auth.uid(), actualizada_en = now() where id = p_derivacion;
        perform public.auditar('atencion_iniciada', jsonb_build_object('derivacion', p_derivacion));
    end if;
    return conv;
end $$;

-- Cambios de estado (v3: el profesional también puede cerrar su atención).
create or replace function public.cambiar_estado_conversacion(p_id uuid, p_estado text, p_nota text default null)
returns void language plpgsql volatile security definer set search_path = public as $$
declare
    c public.conversaciones;
    rol text := public.rol_actual();
begin
    select * into c from public.conversaciones where id = p_id;
    if not found then raise exception 'No existe'; end if;
    if p_estado not in ('abierta', 'cerrada', 'derivada') then raise exception 'Estado inválido'; end if;
    if not (
        (rol is null and c.usuario_id = auth.uid() and p_estado = 'cerrada')
        or (rol = 'tutor' and c.tipo = 'orientacion' and c.tutor_id = auth.uid() and p_estado in ('cerrada', 'derivada'))
        or (rol = 'docente' and c.tipo = 'orientacion')
        or (rol = 'bienestar' and c.tipo = 'atencion' and c.profesional_id = auth.uid() and p_estado in ('cerrada', 'abierta'))
    ) then
        perform public.rechazar(403, 'No tienes permiso para este cambio');
    end if;
    update public.conversaciones set estado = p_estado, actualizada_en = now() where id = p_id;
    if p_estado = 'derivada' then
        insert into public.mensajes (conversacion_id, autor_id, rol_autor, texto) values (p_id, auth.uid(), 'sistema',
            'Para este tema te recomendamos conversar con un profesional. En la app, entra a Bienestar > Apoyo profesional: es confidencial.');
    end if;
    if rol = 'docente' then
        insert into public.supervision (conversacion_id, accion, nota)
        values (p_id, case p_estado when 'abierta' then 'reabierta' else p_estado end, p_nota);
    end if;
end $$;

revoke all on function public.iniciar_atencion(uuid) from public, anon;
grant execute on function public.iniciar_atencion(uuid) to authenticated;

-- ---------- Gestión del equipo (solo administradores) ----------
-- La persona crea su cuenta con correo (dashboard o app) y el admin le asigna el rol.

create or replace function public.asignar_rol(p_email text, p_rol text, p_nombre text)
returns void language plpgsql volatile security definer set search_path = public, auth as $$
declare
    uid uuid;
begin
    if not public.tiene_rol('admin') then perform public.rechazar(403, 'Solo administradores'); end if;
    if p_rol not in ('tutor', 'docente', 'bienestar', 'admin') then raise exception 'Rol inválido'; end if;
    select id into uid from auth.users where lower(email) = lower(btrim(p_email)) and not coalesce(is_anonymous, false);
    if uid is null then raise exception 'No hay una cuenta con ese correo. La persona primero debe crear su cuenta.'; end if;
    insert into public.personal (user_id, rol, nombre_visible, activo) values (uid, p_rol, p_nombre, true)
    on conflict (user_id) do update set rol = excluded.rol, nombre_visible = excluded.nombre_visible, activo = true;
    perform public.auditar('rol_asignado', jsonb_build_object('usuario', uid, 'rol', p_rol));
end $$;

create or replace function public.desactivar_personal(p_user uuid)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
    if not public.tiene_rol('admin') then perform public.rechazar(403, 'Solo administradores'); end if;
    if p_user = auth.uid() then raise exception 'No puedes desactivarte a ti mismo'; end if;
    update public.personal set activo = false where user_id = p_user;
    perform public.auditar('rol_desactivado', jsonb_build_object('usuario', p_user));
end $$;

create or replace function public.listar_equipo()
returns table (user_id uuid, email text, rol text, nombre_visible text, activo boolean, ultimo_acceso timestamptz)
language plpgsql stable security definer set search_path = public, auth as $$
begin
    if not public.tiene_rol('admin') then perform public.rechazar(403, 'Solo administradores'); end if;
    return query
        select p.user_id, u.email::text, p.rol, p.nombre_visible, p.activo, u.last_sign_in_at
          from public.personal p join auth.users u on u.id = p.user_id
         order by p.activo desc, p.rol, p.nombre_visible;
end $$;

create or replace function public.bloquear_ip(p_ip text, p_minutos int, p_motivo text)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
    if not public.tiene_rol('admin') then perform public.rechazar(403, 'Solo administradores'); end if;
    insert into public.ips_bloqueadas (ip, hasta, motivo) values (btrim(p_ip), now() + make_interval(mins => greatest(1, least(p_minutos, 43200))), public.limpiar_texto(p_motivo, 200))
    on conflict (ip) do update set hasta = excluded.hasta, motivo = excluded.motivo;
    perform public.auditar('ip_bloqueada', jsonb_build_object('ip', p_ip, 'minutos', p_minutos));
end $$;

create or replace function public.desbloquear_ip(p_ip text)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
    if not public.tiene_rol('admin') then perform public.rechazar(403, 'Solo administradores'); end if;
    delete from public.ips_bloqueadas where ip = btrim(p_ip);
    perform public.auditar('ip_desbloqueada', jsonb_build_object('ip', p_ip));
end $$;

create or replace function public.resumen_seguridad()
returns jsonb language plpgsql stable security definer set search_path = public as $$
begin
    if not public.tiene_rol('admin') then perform public.rechazar(403, 'Solo administradores'); end if;
    return jsonb_build_object(
        'rechazos_24h', (select count(*) from public.rechazos where fecha > now() - interval '24 hours'),
        'ips_bloqueadas', (select coalesce(jsonb_agg(jsonb_build_object('ip', ip, 'hasta', hasta, 'motivo', motivo) order by hasta desc), '[]'::jsonb)
                           from public.ips_bloqueadas where hasta > now()),
        'auditoria', (select coalesce(jsonb_agg(x order by x.fecha desc), '[]'::jsonb) from (
                          select a.fecha, a.accion, a.detalle, coalesce(p.nombre_visible, 'Sistema') as actor
                            from public.auditoria a left join public.personal p on p.user_id = a.actor
                           order by a.fecha desc limit 50) x)
    );
end $$;

revoke all on function public.asignar_rol(text, text, text), public.desactivar_personal(uuid), public.listar_equipo(),
    public.bloquear_ip(text, int, text), public.desbloquear_ip(text), public.resumen_seguridad() from public, anon;
grant execute on function public.asignar_rol(text, text, text), public.desactivar_personal(uuid), public.listar_equipo(),
    public.bloquear_ip(text, int, text), public.desbloquear_ip(text), public.resumen_seguridad() to authenticated;

-- Auditar también la gestión de derivaciones.
create or replace function public.gestionar_derivacion(p_id uuid, p_estado text)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
    if not public.tiene_rol('bienestar') then perform public.rechazar(403, 'Solo el equipo de Bienestar puede gestionar derivaciones'); end if;
    if p_estado not in ('pendiente', 'en_contacto', 'atendida') then raise exception 'Estado inválido'; end if;
    update public.derivaciones set estado = p_estado, gestionada_por = auth.uid(), actualizada_en = now() where id = p_id;
    perform public.auditar('derivacion_gestionada', jsonb_build_object('derivacion', p_id, 'estado', p_estado));
end $$;

-- =====================================================================
-- v4 (1.3.3) · Toda cuenta nace como ALUMNO; el equipo se PRE-REGISTRA.
--   El administrador registra el correo y el rol (personal_invitado). El rol se
--   asigna solo cuando una cuenta con ese correo existe Y TIENE EL CORREO CONFIRMADO:
--   así nadie puede registrarse con el correo de otra persona y quedarse con su rol.
-- =====================================================================

create table if not exists public.personal_invitado (
    correo         text primary key check (char_length(correo) <= 254 and correo ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    rol            text not null check (rol in ('tutor', 'docente', 'bienestar', 'admin')),
    nombre_visible text not null check (char_length(nombre_visible) between 2 and 40),
    creado_por     uuid default auth.uid() references auth.users (id) on delete set null,
    creado_en      timestamptz not null default now()
);
comment on table public.personal_invitado is
    'Correos del equipo pre-registrados por un administrador. Se convierten en "personal" al confirmar el correo.';
alter table public.personal_invitado enable row level security;
revoke all on public.personal_invitado from anon, authenticated;   -- solo por las funciones de abajo

create or replace function public.sanear_invitado()
returns trigger language plpgsql as $$
begin
    new.correo := lower(btrim(new.correo));
    new.nombre_visible := public.limpiar_texto(new.nombre_visible, 40);
    -- Mismo formato que personal.nombre_visible: si se valida aquí, la asignación posterior no puede fallar.
    if new.nombre_visible is null or new.nombre_visible !~ '^[[:alpha:][:space:].''-]{2,40}$' then
        raise exception 'Nombre visible inválido' using errcode = '22023';
    end if;
    return new;
end $$;
drop trigger if exists personal_invitado_sanear on public.personal_invitado;
create trigger personal_invitado_sanear before insert or update on public.personal_invitado
    for each row execute function public.sanear_invitado();

-- Pasa la invitación de [p_user] (si su correo está confirmado) a la tabla personal. Devuelve el rol o null.
create or replace function public.aplicar_invitacion(p_user uuid)
returns text language plpgsql volatile security definer set search_path = public, auth as $$
declare
    u   record;
    inv record;
begin
    select id, lower(btrim(email)) as correo, email_confirmed_at, coalesce(is_anonymous, false) as anonimo
      into u from auth.users where id = p_user;
    if u.id is null or u.anonimo or u.correo is null or u.email_confirmed_at is null then return null; end if;
    select * into inv from public.personal_invitado i where i.correo = u.correo;
    if not found then return null; end if;
    insert into public.personal (user_id, rol, nombre_visible, activo) values (u.id, inv.rol, inv.nombre_visible, true)
    on conflict (user_id) do update set rol = excluded.rol, nombre_visible = excluded.nombre_visible, activo = true;
    delete from public.personal_invitado i where i.correo = u.correo;
    perform public.auditar('rol_por_invitacion', jsonb_build_object('usuario', u.id, 'rol', inv.rol));
    return inv.rol;
end $$;
revoke all on function public.aplicar_invitacion(uuid) from public, anon, authenticated;

-- La app la llama al iniciar sesión (en vez de rol_actual): aplica la invitación pendiente
-- de la propia cuenta (solo con el correo confirmado) y devuelve el rol vigente.
create or replace function public.reclamar_rol_invitado()
returns text language plpgsql volatile security definer set search_path = public as $$
begin
    if auth.uid() is null then return null; end if;
    perform public.aplicar_invitacion(auth.uid());
    return public.rol_actual();
end $$;
revoke all on function public.reclamar_rol_invitado() from public, anon;
grant execute on function public.reclamar_rol_invitado() to authenticated;

-- También al confirmar el correo (o al crear una cuenta ya confirmada), sin esperar a la app.
-- Nunca debe impedir la confirmación: cualquier error se ignora (la app vuelve a intentarlo al entrar).
create or replace function public.invitacion_al_confirmar()
returns trigger language plpgsql security definer set search_path = public, auth as $$
begin
    if new.email_confirmed_at is not null and not coalesce(new.is_anonymous, false) then
        begin
            perform public.aplicar_invitacion(new.id);
        exception when others then
            raise warning 'No se aplicó la invitación de %: %', new.id, sqlerrm;
        end;
    end if;
    return new;
end $$;
do $$ begin
    drop trigger if exists invitacion_al_confirmar on auth.users;
    create trigger invitacion_al_confirmar after insert or update of email_confirmed_at, email on auth.users
        for each row execute function public.invitacion_al_confirmar();
exception when insufficient_privilege then
    raise notice 'Sin permiso para crear el disparador en auth.users: el rol se aplicará al iniciar sesión.';
end $$;

-- Pre-registro desde el panel. Si la cuenta ya existe y tiene el correo confirmado, se asigna de inmediato.
create or replace function public.invitar_personal(p_email text, p_rol text, p_nombre text)
returns text language plpgsql volatile security definer set search_path = public, auth as $$
declare
    v_correo text := lower(btrim(coalesce(p_email, '')));
    v_uid    uuid;
begin
    if not public.tiene_rol('admin') then perform public.rechazar(403, 'Solo administradores'); end if;
    if p_rol is null or p_rol not in ('tutor', 'docente', 'bienestar', 'admin') then
        raise exception 'Rol inválido' using errcode = '22023';
    end if;
    insert into public.personal_invitado (correo, rol, nombre_visible, creado_por, creado_en)
    values (v_correo, p_rol, p_nombre, auth.uid(), now())
    on conflict (correo) do update
        set rol = excluded.rol, nombre_visible = excluded.nombre_visible, creado_por = excluded.creado_por, creado_en = now();
    perform public.auditar('personal_invitado', jsonb_build_object('rol', p_rol));
    select id into v_uid from auth.users where lower(btrim(email)) = v_correo and not coalesce(is_anonymous, false);
    if v_uid is not null and public.aplicar_invitacion(v_uid) is not null then return 'asignado'; end if;
    return 'invitado';
end $$;

-- asignar_rol (lo usaba el panel) ahora pre-registra: ya no asigna a cuentas sin correo confirmado
-- ni falla si la persona aún no tiene cuenta.
create or replace function public.asignar_rol(p_email text, p_rol text, p_nombre text)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
    perform public.invitar_personal(p_email, p_rol, p_nombre);
end $$;

create or replace function public.listar_invitaciones()
returns table (correo text, rol text, nombre_visible text, creado_en timestamptz)
language plpgsql stable security definer set search_path = public as $$
begin
    if not public.tiene_rol('admin') then perform public.rechazar(403, 'Solo administradores'); end if;
    return query select i.correo, i.rol, i.nombre_visible, i.creado_en from public.personal_invitado i order by i.creado_en desc;
end $$;

create or replace function public.cancelar_invitacion(p_email text)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
    if not public.tiene_rol('admin') then perform public.rechazar(403, 'Solo administradores'); end if;
    delete from public.personal_invitado i where i.correo = lower(btrim(p_email));
    perform public.auditar('invitacion_cancelada', '{}'::jsonb);
end $$;

revoke all on function public.invitar_personal(text, text, text), public.asignar_rol(text, text, text),
    public.listar_invitaciones(), public.cancelar_invitacion(text) from public, anon;
grant execute on function public.invitar_personal(text, text, text), public.asignar_rol(text, text, text),
    public.listar_invitaciones(), public.cancelar_invitacion(text) to authenticated;

-- =====================================================================
-- v4 (1.3.3) · Una sola sesión activa por cuenta con correo.
--   Al iniciar sesión, la app llama registrar_sesion(): queda vigente la sesión de ese
--   teléfono y se revocan las demás (sus tokens de renovación dejan de servir). Hasta que
--   venza su token de acceso, el teléfono anterior es rechazado en cada petición con
--   SESION_REEMPLAZADA (ver antes_de_cada_peticion), y la app vuelve a «Iniciar sesión».
--   Las cuentas anónimas no se ven afectadas.
-- =====================================================================

create table if not exists public.sesiones_vigentes (
    user_id        uuid primary key references auth.users (id) on delete cascade,
    session_id     uuid not null,
    actualizada_en timestamptz not null default now()
);
alter table public.sesiones_vigentes enable row level security;
revoke all on public.sesiones_vigentes from anon, authenticated;

create or replace function public.registrar_sesion()
returns void language plpgsql volatile security definer set search_path = public, auth as $$
declare
    v_sid uuid := nullif(auth.jwt() ->> 'session_id', '')::uuid;
begin
    if auth.uid() is null or v_sid is null or coalesce((auth.jwt() ->> 'is_anonymous')::boolean, false) then return; end if;
    insert into public.sesiones_vigentes (user_id, session_id, actualizada_en) values (auth.uid(), v_sid, now())
    on conflict (user_id) do update set session_id = excluded.session_id, actualizada_en = now();
    -- Revoca las otras sesiones (sus refresh tokens se borran en cascada).
    begin
        delete from auth.sessions where user_id = auth.uid() and id <> v_sid;
    exception when undefined_table or insufficient_privilege then
        null;   -- sin acceso a auth.sessions: igual las rechaza antes_de_cada_peticion
    end;
end $$;
revoke all on function public.registrar_sesion() from public, anon;
grant execute on function public.registrar_sesion() to authenticated;

-- Control por IP (igual que antes) + rechazo de sesiones reemplazadas.
create or replace function public.antes_de_cada_peticion()
returns void language plpgsql volatile security definer set search_path = public as $$
declare
    v_ip text := public.ip_cliente();
    escritura boolean := coalesce(current_setting('request.method', true), 'GET') <> 'GET'
                         and current_setting('transaction_read_only', true) = 'off';
    v_sid text := nullif(auth.jwt() ->> 'session_id', '');
begin
    if exists (select 1 from public.ips_bloqueadas b where b.ip = v_ip and b.hasta > now()) then
        perform public.rechazar(403, 'Acceso bloqueado temporalmente.');
    end if;
    -- Una sola sesión por cuenta: la que se registró al último gana. registrar_sesion se deja
    -- pasar siempre (es justo la llamada con que el teléfono nuevo toma la sesión).
    if v_sid is not null and auth.uid() is not null
       and coalesce(current_setting('request.path', true), '') not like '%/rpc/registrar_sesion'
       and exists (select 1 from public.sesiones_vigentes s where s.user_id = auth.uid() and s.session_id::text <> v_sid) then
        perform public.rechazar(401, 'SESION_REEMPLAZADA: iniciaste sesión en otro teléfono.');
    end if;
    -- Las lecturas corren en transacciones de solo lectura: se cuentan solo las escrituras.
    if escritura and v_ip <> 'desconocida' then
        if public.superar_limite(v_ip, 'peticiones', 120, 60) then          -- 120 escrituras por minuto por IP
            insert into public.rechazos (ip, motivo) values (v_ip, 'limite_ip');
            -- Quien insiste (más de 10 rechazos en 10 min) queda bloqueado 15 minutos.
            if (select count(*) from public.rechazos r where r.ip = v_ip and r.fecha > now() - interval '10 minutes') > 10 then
                insert into public.ips_bloqueadas (ip, hasta, motivo) values (v_ip, now() + interval '15 minutes', 'Bloqueo automático por exceso de peticiones')
                on conflict (ip) do update set hasta = excluded.hasta, motivo = excluded.motivo;
            end if;
            perform public.rechazar(429, 'Demasiadas solicitudes desde tu red. Intenta en un minuto.');
        end if;
    end if;
end $$;
grant execute on function public.antes_de_cada_peticion() to anon, authenticated;
notify pgrst, 'reload config';

-- =====================================================================
-- v4 (1.3.3) · Sede del alumno (para escalar a más regiones).
--   Solo la sede (nunca dirección ni ubicación), y solo si comparte datos anonimizados.
--   Para agregar una sede: insert into public.sedes (nombre) values ('Osorno');
--   (y súmala a Sedes.LISTA en la app).
-- =====================================================================

create table if not exists public.sedes (
    nombre text primary key check (char_length(nombre) between 2 and 60)
);
insert into public.sedes (nombre) values ('sin_dato'), ('Valdivia') on conflict do nothing;
alter table public.sedes enable row level security;
revoke all on public.sedes from anon, authenticated;
grant select on public.sedes to authenticated;
drop policy if exists sedes_select on public.sedes;
create policy sedes_select on public.sedes for select to authenticated using (true);

alter table public.usuarios add column if not exists sede text not null default 'sin_dato';
alter table public.usuarios drop constraint if exists usuarios_sede_fkey;
alter table public.usuarios add constraint usuarios_sede_fkey foreign key (sede) references public.sedes (nombre) on update cascade;

-- Personas por sede, OCULTANDO las sedes con menos de 5 (para que no se pueda identificar a nadie).
create or replace function public.resumen_por_sede()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    minimo constant int := 5;
begin
    if not public.es_admin() then
        raise exception 'Acceso solo para administradores' using errcode = '42501';
    end if;
    return jsonb_build_object(
        'minimo', minimo,
        'por_sede', (select coalesce(jsonb_object_agg(sede, n), '{}'::jsonb)
                     from (select sede, count(*) n from public.usuarios group by 1 having count(*) >= minimo) t),
        'sedes_ocultas', (select count(*) from (select sede from public.usuarios group by 1 having count(*) < minimo) t)
    );
end $$;
revoke all on function public.resumen_por_sede() from public, anon;
grant execute on function public.resumen_por_sede() to authenticated;

-- Revocar también borra la sede (como el rango de edad y el sexo).
create or replace function public.eliminar_mis_datos(motivo text default 'eliminacion')
returns void
language plpgsql volatile security definer
set search_path = public, auth
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'Se requiere sesión' using errcode = '28000';
    end if;
    if motivo = 'revocacion' then
        insert into public.eventos_privacidad (tipo) values ('revocacion');
        delete from public.registros_biometricos where usuario_id = uid;
        delete from public.resultados_scoff where usuario_id = uid;
        delete from public.consentimientos where usuario_id = uid;
        delete from public.derivaciones where usuario_id = uid and contacto_cifrado is null;
        if to_regclass('public.resultados_cuestionario') is not null then
            execute 'delete from public.resultados_cuestionario where usuario_id = $1' using uid;
        end if;
        if to_regclass('public.antecedentes_cifrados') is not null then
            execute 'delete from public.antecedentes_cifrados where usuario_id = $1' using uid;
        end if;
        update public.usuarios set rango_edad = 'sin_dato', sexo = 'sin_dato', sede = 'sin_dato' where id = uid;
    else
        insert into public.eventos_privacidad (tipo) values ('eliminacion');
        delete from public.usuarios where id = uid;  -- borra en cascada todo lo demás
        -- Borra la cuenta (anónima o con correo), nunca la de alguien del personal.
        delete from auth.users
         where id = uid
           and not exists (select 1 from public.personal where user_id = uid)
           and not exists (select 1 from public.administradores where user_id = uid);
    end if;
end $$;
revoke all on function public.eliminar_mis_datos(text) from public, anon;
grant execute on function public.eliminar_mis_datos(text) to authenticated;

-- =====================================================================
-- v4 (1.3.3) · Antecedentes de salud para quien atiende (patologías, alergias, medicamentos).
--   Se cifran EN EL TELÉFONO con la clave pública de Bienestar (igual que contacto_cifrado):
--   aquí solo hay texto ilegible; ni la base ni los administradores pueden leerlos.
--   Se suben al pedir atención y cada vez que la persona los edita. No se usan para estadísticas.
--   Se borran al eliminar la cuenta (en cascada) o al revocar (eliminar_mis_datos).
-- =====================================================================

create table if not exists public.antecedentes_cifrados (
    usuario_id     uuid primary key default auth.uid() references public.usuarios (id) on delete cascade,
    cifrado        jsonb not null check (
                       jsonb_typeof(cifrado) = 'object'
                       and cifrado ?& array['v', 'alg', 'ek', 'iv', 'ct']
                       and octet_length(cifrado::text) < 8192),
    actualizado_en timestamptz not null default now()
);
alter table public.antecedentes_cifrados enable row level security;
revoke all on public.antecedentes_cifrados from anon, authenticated;
grant select on public.antecedentes_cifrados to authenticated;
drop policy if exists antecedentes_select on public.antecedentes_cifrados;
-- La persona ve lo suyo; Bienestar (que tiene la clave privada) puede leer el sobre cifrado.
create policy antecedentes_select on public.antecedentes_cifrados for select to authenticated
    using (usuario_id = auth.uid() or public.tiene_rol('bienestar'));

create or replace function public.guardar_antecedentes(p_cifrado jsonb)
returns void language plpgsql volatile security definer set search_path = public as $$
begin
    if auth.uid() is null then perform public.rechazar(401, 'Se requiere sesión'); end if;
    if public.rol_actual() is not null then perform public.rechazar(403, 'Solo estudiantes'); end if;
    if public.superar_limite(auth.uid()::text, 'antecedentes', 30, 3600) then
        perform public.rechazar(429, 'Demasiados cambios seguidos. Intenta más tarde.');
    end if;
    insert into public.antecedentes_cifrados (usuario_id, cifrado, actualizado_en) values (auth.uid(), p_cifrado, now())
    on conflict (usuario_id) do update set cifrado = excluded.cifrado, actualizado_en = now();
end $$;

create or replace function public.borrar_antecedentes()
returns void language sql volatile security definer set search_path = public as $$
    delete from public.antecedentes_cifrados where usuario_id = auth.uid();
$$;

revoke all on function public.guardar_antecedentes(jsonb), public.borrar_antecedentes() from public, anon;
grant execute on function public.guardar_antecedentes(jsonb), public.borrar_antecedentes() to authenticated;

-- =====================================================================
-- v4 (1.3.3) · Cuestionarios de Bienestar (tarea 18f). Solo el PUNTAJE, nunca las respuestas,
--   y solo si la persona comparte datos anonimizados. Mismas reglas que resultados_scoff.
-- =====================================================================

create table if not exists public.resultados_cuestionario (
    id          uuid primary key,
    usuario_id  uuid not null default auth.uid() references public.usuarios (id) on delete cascade,
    tipo        text not null check (tipo in ('who5', 'phq2', 'phq9', 'gad2', 'gad7', 'pss10', 'auditc', 'ipaq')),
    puntaje     int  not null check (puntaje between 0 and 100),
    alerta      boolean not null,
    fecha       timestamptz not null check (fecha > '2024-01-01' and fecha < now() + interval '1 day'),
    recibido_en timestamptz not null default now()
);
create index if not exists idx_cuestionario_usuario on public.resultados_cuestionario (usuario_id);
alter table public.resultados_cuestionario enable row level security;
revoke all on public.resultados_cuestionario from anon, authenticated;
grant select, insert, update, delete on public.resultados_cuestionario to authenticated;
drop policy if exists resultados_cuestionario_select on public.resultados_cuestionario;
drop policy if exists resultados_cuestionario_insert on public.resultados_cuestionario;
drop policy if exists resultados_cuestionario_update on public.resultados_cuestionario;
drop policy if exists resultados_cuestionario_delete on public.resultados_cuestionario;
create policy resultados_cuestionario_select on public.resultados_cuestionario for select to authenticated
    using (usuario_id = auth.uid() or public.es_admin());
create policy resultados_cuestionario_insert on public.resultados_cuestionario for insert to authenticated
    with check (usuario_id = auth.uid());
create policy resultados_cuestionario_update on public.resultados_cuestionario for update to authenticated
    using (usuario_id = auth.uid()) with check (usuario_id = auth.uid());
create policy resultados_cuestionario_delete on public.resultados_cuestionario for delete to authenticated
    using (usuario_id = auth.uid());
drop trigger if exists resultados_cuestionario_limite on public.resultados_cuestionario;
create trigger resultados_cuestionario_limite before insert on public.resultados_cuestionario
    for each row execute function public.limitar_por_usuario();   -- 1000 por hora (rama por defecto)

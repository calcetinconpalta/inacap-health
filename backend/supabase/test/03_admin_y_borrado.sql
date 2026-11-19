\set ON_ERROR_STOP 1
set role authenticated;
set request.jwt.claim.sub = '22222222-2222-4222-8222-222222222222';
select 'B borra solo lo suyo: delete de A afecta 0 filas' as prueba;
delete from registros_biometricos where id = 'b0000000-0000-4000-8000-000000000001';
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
select jsonb_pretty(public.dashboard_resumen(7)) as dashboard;
-- B elimina sus datos
set request.jwt.claim.sub = '22222222-2222-4222-8222-222222222222';
select public.eliminar_mis_datos('eliminacion');
-- A revoca
set request.jwt.claim.sub = '11111111-1111-4111-8111-111111111111';
select public.eliminar_mis_datos('revocacion');
reset role;
reset request.jwt.claim.sub;
select 'tras borrar: usuarios' p, count(*) from usuarios union all
select 'registros', count(*) from registros_biometricos union all
select 'consentimientos', count(*) from consentimientos union all
select 'auth.users restantes (solo admin)', count(*) from auth.users union all
select 'eventos_privacidad', count(*) from eventos_privacidad;
select public.purgar_datos_antiguos(24);

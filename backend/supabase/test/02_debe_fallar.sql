-- Cada bloque debe FALLAR
set role authenticated;
set request.jwt.claim.sub = '22222222-2222-4222-8222-222222222222';
\echo '--- B intenta escribir a nombre de A (debe fallar):'
insert into registros_biometricos(id,usuario_id,tipo,agua_ml,fecha) values (gen_random_uuid(),'11111111-1111-4111-8111-111111111111','agua',250,now());
\echo '--- B intenta usar el dashboard (debe fallar):'
select public.dashboard_resumen(30);
\echo '--- registro incoherente (debe fallar):'
insert into registros_biometricos(id,tipo,imc,agua_ml,fecha) values (gen_random_uuid(),'imc',22,250,now());
\echo '--- modificar historial de consentimiento (debe fallar):'
set request.jwt.claim.sub = '11111111-1111-4111-8111-111111111111';
update consentimientos set aceptado = false where tipo = 'nube';
\echo '--- anon sin sesion lee (debe fallar):'
reset role; set role anon;
select count(*) from usuarios;

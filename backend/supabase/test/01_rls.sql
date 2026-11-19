\set ON_ERROR_STOP 1
insert into public.administradores(user_id, email) values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','admin@ejemplo.cl');

-- Usuario A (anónimo) escribe sus datos, como lo hace la app (upsert)
set role authenticated;
set request.jwt.claim.sub = '11111111-1111-4111-8111-111111111111';
insert into usuarios(id, rango_edad, sexo, version_app) values ('11111111-1111-4111-8111-111111111111','18-24','Femenino','1.1')
  on conflict (id) do update set rango_edad = excluded.rango_edad;
insert into usuarios(id, rango_edad, sexo, version_app) values ('11111111-1111-4111-8111-111111111111','25-29','Femenino','1.1')
  on conflict (id) do update set rango_edad = excluded.rango_edad;
insert into consentimientos(id, tipo, version, aceptado, fecha) values (gen_random_uuid(),'terminos',1,true,now()),(gen_random_uuid(),'nube',1,true,now());
insert into registros_biometricos(id,tipo,imc,categoria_imc,nivel_actividad,fecha) values ('b0000000-0000-4000-8000-000000000001','imc',22.9,'en_rango','moderado',now());
insert into registros_biometricos(id,tipo,agua_ml,fecha) values ('b0000000-0000-4000-8000-000000000002','agua',250,now()),('b0000000-0000-4000-8000-000000000003','agua',250,now());
-- reenvío idempotente (mismo id) no duplica
insert into registros_biometricos(id,tipo,agua_ml,fecha) values ('b0000000-0000-4000-8000-000000000003','agua',250,now()) on conflict (id) do update set agua_ml = excluded.agua_ml;
insert into resultados_scoff(id,puntaje,requiere_derivacion,fecha) values (gen_random_uuid(),2,true,now());
insert into derivaciones(id,canal,codigo,fecha) values (gen_random_uuid(),'cesfam','INH-ABC123',now());
select 'A ve sus registros' as prueba, count(*) from registros_biometricos;

-- Usuario B
set request.jwt.claim.sub = '22222222-2222-4222-8222-222222222222';
insert into usuarios(id) values ('22222222-2222-4222-8222-222222222222');
insert into registros_biometricos(id,tipo,imc,categoria_imc,fecha) values (gen_random_uuid(),'imc',27.1,'sobre_rango',now() - interval '2 days');
select 'B NO ve datos de A (espera 1)' as prueba, count(*) from registros_biometricos;
select 'B NO ve usuarios ajenos (espera 1)' as prueba, count(*) from usuarios;

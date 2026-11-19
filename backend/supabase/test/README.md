# Pruebas del esquema SQL (PostgreSQL local)

Estas pruebas NO usan Supabase: simulan su esquema `auth` en un PostgreSQL local.

```bash
createdb inacap_test
psql -d inacap_test -c "create extension if not exists pgcrypto"
psql -d inacap_test -v ON_ERROR_STOP=1 -f 00_simular_supabase.sql
psql -d inacap_test -v ON_ERROR_STOP=1 -f ../schema.sql
psql -d inacap_test -f 01_rls.sql            # aislamiento entre usuarios
psql -d inacap_test -f 02_debe_fallar.sql    # cada bloque debe mostrar ERROR
psql -d inacap_test -f 03_admin_y_borrado.sql  # dashboard, borrado y purga
psql -d inacap_test -v ON_ERROR_STOP=1 -f 04_roles_orientacion_derivaciones.sql  # roles, chat y derivaciones
psql -d inacap_test -v ON_ERROR_STOP=1 -f 05_v3_seguridad_y_atencion.sql          # límites, IP, limpieza, atención, equipo
```

Resultado esperado: en `01` el usuario B ve 1 registro (no los de A); en `02` los 5 intentos fallan;
en `03` el dashboard devuelve cifras; tras borrar, B desaparece por completo y A (que solo revocó) conserva su fila sin datos;
`04` termina con «TODAS LAS PRUEBAS DE ROLES, ORIENTACIÓN Y DERIVACIONES PASARON» (si algo falla se detiene con «FALLO: …»).

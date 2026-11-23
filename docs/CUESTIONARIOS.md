# Cuestionarios de Bienestar (1.3.3)

En la app: Bienestar → Cuestionarios. Una pregunta por pantalla. **No son un diagnóstico.**
Las respuestas se quedan en el teléfono. A la nube solo va el puntaje (`resultados_cuestionario`), y solo si la persona comparte datos anonimizados.
El código está en `app/src/main/java/com/inacap/health/salud/Cuestionarios.kt`.

## Activos (texto y puntos de corte verificados)

| Cuestionario | Fuente del texto en español | Puntaje y alerta |
|---|---|---|
| **WHO-5** | Traducción oficial (Psychiatric Research Unit, Centro Colaborador OMS en Salud Mental, 1998). | Suma 0–25 × 4 = 0–100. El formulario recomienda evaluar más si la suma es **< 13** o si algún ítem vale 0 o 1 → recomendar Bienestar. |
| **PHQ-2** | Primeros 2 ítems del PHQ-9 en español de Pfizer (PRIME-MD). | **≥ 3** → ofrecer el PHQ-9 y recomendar Bienestar (corte recomendado, citado en Errázuriz et al., 2022, población hispanohablante en Chile). |
| **PHQ-9** | Versión en español de Pfizer (PRIME-MD), opciones «Nunca / Varios días / Más de la mitad de los días / Casi todos los días». Solo se corrigieron erratas evidentes de la copia («Temer» → «Tener», tildes). | Bandas 0–4 mínimo, 5–9 leve, 10–14 moderado, 15–19 moderado a severo, 20–27 severo. **≥ 10** → recomendar Bienestar. |

Fuentes consultadas:
- Formulario WHO-5 en español: https://www.infocop.es/wp-content/uploads/2025/10/oms-cinco-indice-de-bienestar-oms-5.pdf
- PHQ-9 en español (Pfizer): https://niwaplibrary.wcl.american.edu/pubs/traum-temp-phq9spanish/
- Errázuriz et al. (2022), PHQ-9 y PHQ-2 en Chile: https://pmc.ncbi.nlm.nih.gov/articles/PMC9655214/
- Validación chilena del PHQ-9 (Baader et al., 2012): https://www.redalyc.org/pdf/3315/331527728002.pdf
- García-Campayo et al. (2010), GAD-7 en español (corte 10): https://pmc.ncbi.nlm.nih.gov/articles/PMC2831043/

## Pendientes (aparecen en la lista, pero NO se pueden responder todavía)

No se pudo verificar el texto exacto en su versión validada en español. Para activar cada uno:
copia el texto oficial en `Cuestionarios.kt`, pon `disponible = true` y agrega su prueba en `CuestionariosTest`.

- **GAD-2 / GAD-7** (ansiedad). El corte de 10 en el GAD-7 está verificado (García-Campayo 2010). Faltan los ítems en español. El GAD-7 completo se aplica solo si el GAD-2 da 3 o más.
- **PSS-10** (estrés percibido; versión de Remor, 2006). No tiene punto de corte clínico: mostrar el puntaje sin etiqueta de alerta, salvo que el equipo defina un criterio.
- **AUDIT-C** (alcohol; versión OMS en español). Confirmar el corte que se usará en Chile.
- **IPAQ corto** (actividad física). Necesita ingresar días y minutos (no son opciones), además de la categorización oficial baja / moderada / alta.

No se usan MBI ni ISI (requieren licencia).

## PHQ-9 · Protocolo mínimo de la pregunta 9 (ya implementado)

Si la pregunta 9 («pensamientos de que sería mejor estar muerto/a o de hacerse daño») es distinta de «Nunca», **sin importar el puntaje**, se muestra una pantalla de ayuda. La pantalla trae botones para llamar a:
- **\*4141**, prevención del suicidio (MINSAL, gratuita, 24 h)
- **Salud Responde 600 360 7777**
- **Emergencias 131**

También trae el botón «Pedir atención a Bienestar». No se puede salir sin tocar «Entiendo».

### PENDIENTE: seguimiento por parte del equipo
**Lo define el docente supervisor.** Hay que decidir quién contacta a la persona, en cuánto tiempo y por qué canal, y qué se registra en la bitácora.
Hoy la app **no avisa** al equipo cuando alguien marca la pregunta 9: el puntaje solo llega si la persona comparte datos, y sin su identidad.
Si se quiere un aviso al equipo, hay que diseñarlo con consentimiento explícito y documentarlo en los términos.

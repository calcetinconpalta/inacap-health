package com.inacap.health.legal

/**
 * Términos y Condiciones + Política de Privacidad de INACAP Health.
 *
 * IMPORTANTE: borrador elaborado para un proyecto académico. Antes de publicar
 * la app debe revisarlo un abogado o la Dirección Jurídica de INACAP.
 *
 * Si cambias algo relevante, sube [VERSION]: la app pedirá a cada usuario
 * aceptar de nuevo y quedará registrado en el historial de consentimientos.
 */
object TextosLegales {

    const val VERSION = 3
    const val FECHA_VIGENCIA = "19 de noviembre de 2026"

    /** Reemplazar por el correo real del equipo antes de publicar. */
    const val CORREO_CONTACTO = "contacto.inacaphealth@ejemplo.cl"

    data class Seccion(val titulo: String, val parrafos: List<String>)

    val terminos: List<Seccion> = listOf(
        Seccion(
            "1. Quiénes somos",
            listOf(
                "INACAP Health es un proyecto académico desarrollado por estudiantes de la carrera de Analista Programador de INACAP en la asignatura Innovación y Emprendimiento I. Es una versión de prueba y no es un servicio oficial de INACAP ni de un prestador de salud.",
                "Responsable del tratamiento de datos: equipo del proyecto INACAP Health. Contacto: $CORREO_CONTACTO."
            )
        ),
        Seccion(
            "2. Qué es y qué no es la app",
            listOf(
                "Es una herramienta de autocuidado y orientación: calculadora de IMC e hidratación, pasos diarios, pulso con la cámara o con tu reloj, cuestionario SCOFF de bienestar alimentario, recordatorios, contenido educativo, orientación entre pares, derivación y mensajes con el equipo de Bienestar Estudiantil (estudiantes de medicina en práctica, supervisados por docentes).",
                "NO entrega diagnósticos médicos ni nutricionales, NO reemplaza la consulta con un médico, nutricionista o psicólogo y NO es un servicio de urgencias. Los resultados son referencias generales (por ejemplo, los rangos de IMC de la OMS).",
                "Si sientes que necesitas ayuda urgente, acude a un servicio de urgencia o llama a Salud Responde: 600 360 7777."
            )
        ),
        Seccion(
            "3. Quién puede usarla",
            listOf(
                "Está pensada para estudiantes de educación superior. Debes tener 18 años o más. Si tienes entre 14 y 17 años, necesitas la autorización de tu madre, padre o tutor legal, porque la app trata datos de salud.",
                "Al aceptar declaras que la información que ingresas es tuya y que la usas para tu propio cuidado."
            )
        ),
        Seccion(
            "4. Uso correcto",
            listOf(
                "Te comprometes a no usar la app para dañar a otras personas, ingresar datos de terceros sin su autorización, intentar acceder a datos ajenos ni interferir con su funcionamiento o con los servidores asociados."
            )
        ),
        Seccion(
            "5. Limitación de responsabilidad",
            listOf(
                "La app se entrega \"tal cual\", como prototipo académico, y puede tener errores. Las decisiones sobre tu salud deben tomarse junto a un profesional. El equipo no se responsabiliza por decisiones tomadas solo en base a los resultados de la app.",
                "El pulso medido con la cámara es una estimación aproximada y puede fallar; no es un dispositivo médico.",
                "La orientación entre pares la entregan estudiantes de Nutrición supervisados por docentes: es educativa y no reemplaza la atención profesional.",
                "Si pides que Bienestar Estudiantil te contacte, la app envía tu solicitud cifrada; el equipo de Bienestar decide cómo y cuándo responder según su disponibilidad."
            )
        ),
        Seccion(
            "6. Cambios a estos términos",
            listOf(
                "Si cambiamos estos términos de forma relevante, la app te los mostrará de nuevo y deberás aceptarlos para seguir usándola. Cada aceptación queda registrada con su versión y fecha."
            )
        ),
        Seccion(
            "7. Ley aplicable",
            listOf(
                "Estos términos se rigen por las leyes de la República de Chile, en especial la Ley N° 19.628 sobre Protección de la Vida Privada (con las modificaciones de la Ley N° 21.719) y la Ley N° 19.496 sobre Protección de los Derechos de los Consumidores, en lo que corresponda."
            )
        )
    )

    val privacidad: List<Seccion> = listOf(
        Seccion(
            "1. Datos que tratamos",
            listOf(
                "Perfil: nombre, edad, sexo, patologías, alergias y medicamentos (todos opcionales).",
                "Registros de salud: peso, estatura, IMC, consumo de agua y resultados del cuestionario SCOFF.",
                "Actividad: pasos diarios (sensor del teléfono o, si lo vinculas, Health Connect) y frecuencia cardíaca (cámara, reloj conectado por Bluetooth o Health Connect).",
                "Preferencias: metas de agua y pasos, nivel de actividad, recordatorios y bloqueo de la app.",
                "Cámara: solo para estimar el pulso. Las imágenes se procesan en el teléfono y nunca se guardan ni se envían.",
                "Los datos de salud son datos personales sensibles. Por eso solo los tratamos con tu consentimiento expreso."
            )
        ),
        Seccion(
            "2. Dónde se guardan",
            listOf(
                "Por defecto, TODO se guarda solo en tu teléfono, en una base de datos cifrada. La app no envía nada a internet si no lo autorizas.",
                "Los datos de la app no se copian a los respaldos automáticos de Google."
            )
        ),
        Seccion(
            "3. Envío opcional de datos (anonimizado)",
            listOf(
                "Si activas \"Compartir datos anonimizados\", se envía una copia reducida a servidores del proyecto, para generar estadísticas de bienestar estudiantil y mejorar la app:",
                "• Registros: rango de edad (por ejemplo 18-24), sexo, IMC y su rango de referencia, vasos de agua, pasos diarios, pulso, puntaje SCOFF, solicitudes de derivación y tus consentimientos.",
                "• Uso de la app: pantallas visitadas, funciones usadas y errores técnicos.",
                "NUNCA se envían tu nombre, patologías, alergias, medicamentos, peso, estatura ni tus respuestas individuales del SCOFF.",
                "Se te identifica con un código aleatorio, sin correo ni teléfono, salvo que crees una cuenta con correo. Aun así, la ley considera estos datos como personales (seudonimizados), por eso te pedimos permiso.",
                "Para alojar estos datos usamos proveedores de servicios en la nube que pueden tener servidores fuera de Chile (por ejemplo, en Brasil o Estados Unidos). Cuentan con sus propias medidas de seguridad y solo procesan los datos por encargo del proyecto. Puedes pedir el detalle de los proveedores escribiendo a $CORREO_CONTACTO."
            )
        ),
        Seccion(
            "3.1 Funciones que envían datos solo cuando las usas",
            listOf(
                "• Derivación con contacto: si pides que Bienestar Estudiantil te contacte, tu nombre, medio de contacto y mensaje se cifran en tu teléfono con la clave de Bienestar (cifrado de extremo a extremo). Solo ese equipo puede leerlos; ni los servidores ni los administradores pueden.",
                "• Orientación entre pares: tus mensajes se guardan con tu código anónimo y los leen el tutor par asignado y los docentes supervisores. Puedes borrar tus conversaciones cuando quieras.",
                "• Atención de Bienestar: si alguien del equipo de Bienestar (estudiantes de medicina en práctica supervisada) responde tu solicitud, esa conversación solo la ven tú y esa persona (no los tutores ni los docentes).",
                "• Reloj por Bluetooth: la app lee el pulso que transmite tu reloj o banda directamente en el teléfono; solo se guarda si tocas \"Guardar\".",
                "• Health Connect: si lo vinculas, la app solo LEE tus pasos y frecuencia cardíaca; nunca escribe ni borra nada allí. Puedes desvincularlo en cualquier momento.",
                "• Resumen PDF: se genera en tu teléfono solo cuando lo pides y tú decides con quién compartirlo.",
                "• Cuenta con correo (opcional): si la creas, tus registros respaldados (IMC, agua, pasos, pulso y puntaje SCOFF) quedan asociados a tu correo para que puedas recuperarlos en otro teléfono. Tu contraseña se guarda cifrada y nadie del equipo puede verla. Al cerrar sesión se borran los datos de ese teléfono; al eliminar tus datos se borra también la cuenta."
            )
        ),
        Seccion(
            "4. Para qué los usamos",
            listOf(
                "Solo para: (a) mostrarte tu información y tu historial; (b) generar estadísticas agregadas en un panel de administración del proyecto (por ejemplo, cuántos tests SCOFF se realizaron o la distribución de IMC), sin identificar a personas.",
                "No vendemos, arrendamos ni cedemos tus datos. No los usamos para publicidad."
            )
        ),
        Seccion(
            "5. Quién puede verlos",
            listOf(
                "Los datos de tu teléfono: solo tú (puedes protegerlos con PIN o huella). Los datos anonimizados: los administradores del proyecto, solo como estadísticas. Las conversaciones de orientación: el tutor asignado y los docentes supervisores. Las conversaciones de atención y las derivaciones con contacto: solo Bienestar Estudiantil."
            )
        ),
        Seccion(
            "6. Cuánto tiempo los guardamos",
            listOf(
                "En tu teléfono: hasta que los elimines o desinstales la app.",
                "Datos compartidos: hasta 24 meses desde su envío o hasta que revoques tu consentimiento o elimines tus datos, lo que ocurra primero."
            )
        ),
        Seccion(
            "7. Tus derechos",
            listOf(
                "Puedes ejercer tus derechos de acceso, rectificación, supresión, oposición, portabilidad y bloqueo.",
                "Desde la app: en Perfil > Privacidad y mis datos puedes ver qué se guarda, desactivar el envío de datos (se borra la copia compartida) y eliminar todos tus datos.",
                "Para otras solicitudes escribe a $CORREO_CONTACTO.",
                "Retirar tu consentimiento no afecta el uso de la app en modo local."
            )
        ),
        Seccion(
            "8. Seguridad",
            listOf(
                "En el teléfono: base de datos cifrada con una clave protegida por el sistema del teléfono, bloqueo opcional con PIN o huella y exclusión de las copias de seguridad.",
                "En tránsito: solo conexiones cifradas. Las derivaciones con contacto, además, van cifradas de extremo a extremo.",
                "En los servidores: cada persona solo puede leer, escribir y borrar sus propios datos, el personal accede según su rol con sesiones que expiran, y hay límites de uso para evitar abusos.",
                "Ningún sistema es 100% seguro: si detectamos un incidente que afecte tus datos, lo informaremos por la app."
            )
        )
    )

    /** Texto plano completo (para exportar o compartir). */
    fun comoTexto(): String = buildString {
        appendLine("TÉRMINOS Y CONDICIONES Y POLÍTICA DE PRIVACIDAD — INACAP Health")
        appendLine("Versión $VERSION, vigente desde el $FECHA_VIGENCIA")
        appendLine()
        appendLine("TÉRMINOS Y CONDICIONES")
        terminos.forEach { s -> appendLine(); appendLine(s.titulo); s.parrafos.forEach { appendLine(it) } }
        appendLine()
        appendLine("POLÍTICA DE PRIVACIDAD")
        privacidad.forEach { s -> appendLine(); appendLine(s.titulo); s.parrafos.forEach { appendLine(it) } }
    }
}

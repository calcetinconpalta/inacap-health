package com.inacap.health.ui.screens.inicio

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inacap.health.ui.theme.Diseno
import kotlin.math.max

/** Los cuatro datos del hero, en orden. */
enum class DatoHero(val etiqueta: String, val malla: List<Diseno.Mancha>, val punto: Color) {
    AGUA("Agua", Diseno.MALLA_AGUA, Diseno.PUNTO_AGUA),
    PASOS("Pasos", Diseno.MALLA_PASOS, Diseno.PUNTO_PASOS),
    PULSO("Pulso", Diseno.MALLA_PULSO, Diseno.PUNTO_PULSO),
    IMC("IMC", Diseno.MALLA_IMC, Diseno.PUNTO_IMC)
}

/** Texto del frente de cada dato. */
data class ContenidoHero(val valor: String, val detalle: String)

/** ¿La persona pidió «quitar animaciones» en el sistema? Entonces sin parallax. */
@Composable
fun sinAnimaciones(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/**
 * Hero de Inicio dentro de la banda roja: tarjeta propia con gradient mesh y tres capas con
 * parallax (malla y círculos de fondo, ola translúcida propia de cada dato, y el dato al frente).
 */
@Composable
fun HeroInicio(pager: PagerState, banda: Color, contenido: (DatoHero) -> ContenidoHero) {
    val quieto = sinAnimaciones()
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxWidth().height(Diseno.ALTO_HERO)
            .shadow(Diseno.SOMBRA_HERO, RoundedCornerShape(Diseno.ESQUINA_HERO), clip = false)
            .clip(RoundedCornerShape(Diseno.ESQUINA_HERO))
            // Borde de luz blanco sutil arriba (se despega de la banda).
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)), RoundedCornerShape(Diseno.ESQUINA_HERO))
    ) { pagina ->
        val dato = DatoHero.entries[pagina]
        val desplazamiento = (pager.currentPage - pagina) + pager.currentPageOffsetFraction
        Box(Modifier.fillMaxSize().background(banda)) {
            // Capa 1 (la más lenta): malla y círculos blancos translúcidos.
            Canvas(Modifier.fillMaxSize().graphicsLayer { if (!quieto) translationX = desplazamiento * size.width * (1f - Diseno.PARALLAX_FONDO) }) {
                dato.malla.forEach { m ->
                    val r = max(size.width, size.height) * m.radio
                    drawRect(Brush.radialGradient(
                        listOf(m.color.copy(alpha = Diseno.INTENSIDAD_MALLA), Color.Transparent),
                        center = Offset(size.width * m.x, size.height * m.y), radius = r
                    ))
                }
                val blanco = Color.White.copy(alpha = Diseno.OPACIDAD_CAPAS)
                drawCircle(blanco, radius = size.height * 0.55f, center = Offset(size.width * 0.92f, size.height * 0.1f))
                drawCircle(blanco, radius = size.height * 0.25f, center = Offset(size.width * 0.12f, size.height * 0.95f))
            }
            // Capa 2: ola blanca translúcida propia de cada dato.
            Canvas(Modifier.fillMaxSize().graphicsLayer { if (!quieto) translationX = desplazamiento * size.width * (1f - Diseno.PARALLAX_OLA) }) {
                val alto = size.height * (0.62f + 0.06f * pagina)
                val ola = Path().apply {
                    moveTo(0f, alto)
                    cubicTo(size.width * 0.3f, alto - 40f - 10f * pagina, size.width * 0.6f, alto + 30f, size.width, alto - 20f)
                    lineTo(size.width, size.height); lineTo(0f, size.height); close()
                }
                drawPath(ola, Color.White.copy(alpha = Diseno.OPACIDAD_CAPAS))
            }
            // Capa 3 (la más rápida): el dato grande en blanco.
            val c = contenido(dato)
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(dato.punto, CircleShape).border(1.dp, Color.White, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(dato.etiqueta, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
                }
                Text(c.valor, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(c.detalle, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Fila de íconos Agua · Pasos · Pulso · IMC bajo la banda. El elegido queda hundido y en rojo. */
@Composable
fun IconosHero(seleccionado: Int, iconos: List<ImageVector>, onElegir: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        DatoHero.entries.forEachIndexed { i, dato ->
            val elegido = i == seleccionado
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onElegir(i) }.semantics { contentDescription = dato.etiqueta; selected = elegido }) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(52.dp)
                        .shadow(if (elegido) 0.dp else 3.dp, RoundedCornerShape(Diseno.ESQUINA_TARJETA))
                        .background(if (elegido) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, RoundedCornerShape(Diseno.ESQUINA_TARJETA))
                ) {
                    Icon(iconos[i], contentDescription = null, tint = if (elegido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(dato.punto, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(dato.etiqueta, style = MaterialTheme.typography.labelMedium,
                        color = if (elegido) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Botón principal rojo sólido con un borde inferior más oscuro (leve relieve). */
@Composable
fun BotonPrincipal(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth().height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Diseno.BandaRojaOscura)
            .padding(bottom = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Diseno.BandaRoja)
            .clickable(onClick = onClick)
    ) { Text(texto, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

/** Para que el ícono elegido y la página del hero vayan juntos. */
fun PagerState.paginaVisible(): Int = (currentPage + currentPageOffsetFraction).let { kotlin.math.round(it).toInt() }.coerceIn(0, pageCount - 1)


package com.inacap.health.util

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.util.Calendar

/** Emite [leer] cada vez que cambia el archivo de preferencias. */
fun <T> SharedPreferences.observar(leer: (SharedPreferences) -> T): Flow<T> = callbackFlow {
    val escucha = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        trySend(leer(prefs))
    }
    trySend(leer(this@observar))
    registerOnSharedPreferenceChangeListener(escucha)
    awaitClose { unregisterOnSharedPreferenceChangeListener(escucha) }
}

fun inicioDelDia(ahora: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = ahora
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * Inicio del día actual, que se actualiza solo al pasar la medianoche.
 * Antes se calculaba una sola vez y el contador de agua no volvía a 0
 * si la app seguía abierta al cambiar de día.
 */
fun flujoInicioDelDia(diasAtras: Int = 0): Flow<Long> = flow {
    while (true) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = inicioDelDia()
            add(Calendar.DAY_OF_YEAR, -diasAtras)
        }
        emit(cal.timeInMillis)
        delay(60_000)
    }
}.distinctUntilChanged()

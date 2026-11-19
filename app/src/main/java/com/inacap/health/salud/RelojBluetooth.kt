package com.inacap.health.salud

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Perfil Bluetooth estándar de frecuencia cardíaca (Heart Rate Profile).
 * Lo transmiten bandas de pecho y muchos relojes con la opción "transmitir frecuencia cardíaca".
 */
object PerfilFrecuenciaCardiaca {
    val SERVICIO: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val MEDICION: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    val SERVICIO_BATERIA: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val NIVEL_BATERIA: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** @param contacto null = el sensor no informa contacto; false = no está en contacto con la piel. */
    data class Lectura(val bpm: Int, val contacto: Boolean?, val rrMs: List<Int>)

    /** Decodifica la característica 0x2A37 (Bluetooth SIG, Heart Rate Measurement). */
    fun decodificar(b: ByteArray): Lectura? {
        if (b.isEmpty()) return null
        val flags = b[0].toInt() and 0xFF
        val bpm16 = flags and 0x01 != 0
        var i = 1
        val bpm = if (bpm16) {
            if (b.size < 3) return null
            ((b[1].toInt() and 0xFF) or ((b[2].toInt() and 0xFF) shl 8)).also { i = 3 }
        } else {
            if (b.size < 2) return null
            (b[1].toInt() and 0xFF).also { i = 2 }
        }
        val contacto = when ((flags shr 1) and 0x03) { 2 -> false; 3 -> true; else -> null }
        if (flags and 0x08 != 0) i += 2                       // energía gastada (se ignora)
        val rr = mutableListOf<Int>()
        if (flags and 0x10 != 0) {
            while (i + 1 < b.size) {
                val v = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
                rr += (v * 1000) / 1024                        // unidades de 1/1024 s → ms
                i += 2
            }
        }
        return Lectura(bpm, contacto, rr)
    }

    /** Promedio de las lecturas válidas (descarta 0 y valores imposibles). */
    fun promedio(bpms: List<Int>): Int? = bpms.filter { it in 30..220 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
}

data class RelojEncontrado(val nombre: String, val direccion: String, val senal: Int)

sealed interface EstadoReloj {
    object Inactivo : EstadoReloj
    data class Buscando(val encontrados: List<RelojEncontrado>) : EstadoReloj
    data class Conectando(val nombre: String) : EstadoReloj
    data class Conectado(val nombre: String, val bpm: Int?, val contacto: Boolean?, val bateria: Int?) : EstadoReloj
    data class Problema(val mensaje: String) : EstadoReloj
}

/** Conexión BLE directa con un reloj o banda que transmita la frecuencia cardíaca. */
@SuppressLint("MissingPermission") // se revisan los permisos antes de cada llamada (tienePermisos)
class RelojBluetooth(private val context: Context) {
    private val adaptador = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val principal = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private val encontrados = linkedMapOf<String, RelojEncontrado>()
    private val ultimas = ArrayDeque<Int>()

    private val _estado = MutableStateFlow<EstadoReloj>(EstadoReloj.Inactivo)
    val estado: StateFlow<EstadoReloj> = _estado.asStateFlow()

    val disponible: Boolean get() = adaptador != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val encendido: Boolean get() = adaptador?.isEnabled == true

    val permisosNecesarios: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun tienePermisos() = permisosNecesarios.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Promedio de los últimos ~10 latidos recibidos (para guardar). */
    fun promedioReciente(): Int? = PerfilFrecuenciaCardiaca.promedio(ultimas.toList())

    private val escaneo = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) {
            val nombre = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull() ?: "Dispositivo sin nombre"
            encontrados[r.device.address] = RelojEncontrado(nombre, r.device.address, r.rssi)
            _estado.value = EstadoReloj.Buscando(encontrados.values.sortedByDescending { it.senal })
        }
        override fun onScanFailed(errorCode: Int) { _estado.value = EstadoReloj.Problema("No se pudo buscar dispositivos (código $errorCode).") }
    }

    fun buscar() {
        if (!disponible) { _estado.value = EstadoReloj.Problema("Este teléfono no tiene Bluetooth de bajo consumo."); return }
        if (!tienePermisos()) { _estado.value = EstadoReloj.Problema("Falta el permiso de Bluetooth."); return }
        if (!encendido) { _estado.value = EstadoReloj.Problema("Enciende el Bluetooth del teléfono."); return }
        detenerBusqueda()
        encontrados.clear()
        _estado.value = EstadoReloj.Buscando(emptyList())
        val filtro = ScanFilter.Builder().setServiceUuid(ParcelUuid(PerfilFrecuenciaCardiaca.SERVICIO)).build()
        val ajustes = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        adaptador?.bluetoothLeScanner?.startScan(listOf(filtro), ajustes, escaneo)
        principal.postDelayed({ detenerBusqueda() }, 15_000)
    }

    fun detenerBusqueda() {
        if (tienePermisos()) runCatching { adaptador?.bluetoothLeScanner?.stopScan(escaneo) }
    }

    fun conectar(direccion: String, nombre: String) {
        if (!tienePermisos()) return
        detenerBusqueda()
        desconectar()
        val equipo: BluetoothDevice = adaptador?.getRemoteDevice(direccion) ?: return
        _estado.value = EstadoReloj.Conectando(nombre)
        ultimas.clear()
        gatt = equipo.connectGatt(context, false, Llamadas(nombre), BluetoothDevice.TRANSPORT_LE)
        context.getSharedPreferences("reloj_prefs", Context.MODE_PRIVATE).edit()
            .putString("direccion", direccion).putString("nombre", nombre).apply()
    }

    fun ultimoReloj(): Pair<String, String>? {
        val p = context.getSharedPreferences("reloj_prefs", Context.MODE_PRIVATE)
        val d = p.getString("direccion", null) ?: return null
        return d to (p.getString("nombre", null) ?: "Mi reloj")
    }

    fun desconectar() {
        if (tienePermisos()) runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null
        if (_estado.value !is EstadoReloj.Problema) _estado.value = EstadoReloj.Inactivo
    }

    private inner class Llamadas(private val nombre: String) : BluetoothGattCallback() {
        private var bateria: Int? = null

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, nuevo: Int) {
            if (nuevo == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else if (nuevo == BluetoothProfile.STATE_DISCONNECTED) {
                runCatching { g.close() }
                _estado.value = if (status == BluetoothGatt.GATT_SUCCESS) EstadoReloj.Inactivo
                else EstadoReloj.Problema("Se perdió la conexión con $nombre. Acércalo al teléfono y vuelve a conectar.")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val c = g.getService(PerfilFrecuenciaCardiaca.SERVICIO)?.getCharacteristic(PerfilFrecuenciaCardiaca.MEDICION)
            if (c == null) {
                _estado.value = EstadoReloj.Problema("$nombre no está transmitiendo la frecuencia cardíaca. Activa la transmisión en el reloj.")
                return
            }
            g.setCharacteristicNotification(c, true)
            val d = c.getDescriptor(PerfilFrecuenciaCardiaca.CCCD)
            if (d != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else @Suppress("DEPRECATION") { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
            }
            _estado.value = EstadoReloj.Conectado(nombre, null, null, null)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            // Después de activar el pulso, se lee la batería (si el reloj la informa).
            g.getService(PerfilFrecuenciaCardiaca.SERVICIO_BATERIA)?.getCharacteristic(PerfilFrecuenciaCardiaca.NIVEL_BATERIA)?.let { g.readCharacteristic(it) }
        }

        @Deprecated("Android < 13")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION") c.value?.let { leer(c, it) }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) = leer(c, value)

        @Deprecated("Android < 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") c.value?.let { leer(c, it) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = leer(c, value)

        private fun leer(c: BluetoothGattCharacteristic, valor: ByteArray) {
            when (c.uuid) {
                PerfilFrecuenciaCardiaca.NIVEL_BATERIA -> bateria = valor.firstOrNull()?.toInt()?.and(0xFF)
                PerfilFrecuenciaCardiaca.MEDICION -> PerfilFrecuenciaCardiaca.decodificar(valor)?.let { l ->
                    if (l.bpm in 30..220) { ultimas.addLast(l.bpm); while (ultimas.size > 10) ultimas.removeFirst() }
                    _estado.value = EstadoReloj.Conectado(nombre, l.bpm.takeIf { it > 0 }, l.contacto, bateria)
                }
            }
        }
    }
}

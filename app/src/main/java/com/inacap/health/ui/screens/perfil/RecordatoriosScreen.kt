package com.inacap.health.ui.screens.perfil

import androidx.core.content.edit
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.inacap.health.HealthApp
import com.inacap.health.reminders.ReminderScheduler
import com.inacap.health.ui.theme.BorderGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordatoriosScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
    }

    var aguaActivo by remember {
        mutableStateOf(sharedPrefs.getBoolean("recordatorio_agua_activo", false))
    }
    var aguaHoras by remember {
        mutableStateOf(sharedPrefs.getInt("recordatorio_agua_horas", 2))
    }
    var movActivo by remember {
        mutableStateOf(sharedPrefs.getBoolean("recordatorio_mov_activo", false))
    }
    var movMin by remember {
        mutableStateOf(sharedPrefs.getInt("recordatorio_mov_min", 60))
    }

    val sync = remember { (context.applicationContext as HealthApp).sync }
    fun registrar(tipo: String, activo: Boolean, intervalo: Int) {
        sync.registrarEvento(
            "recordatorio_cambiado",
            mapOf("tipo" to tipo, "activo" to activo, "intervalo" to intervalo)
        )
    }

    var permisoDenegado by remember { mutableStateOf(false) }
    var pendingTipo by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val tipo = pendingTipo
        pendingTipo = null
        if (isGranted) {
            permisoDenegado = false
            if (tipo == ReminderScheduler.TIPO_AGUA) {
                aguaActivo = true
                sharedPrefs.edit { putBoolean("recordatorio_agua_activo", true) }
                ReminderScheduler.programar(context, ReminderScheduler.TIPO_AGUA)
                registrar(ReminderScheduler.TIPO_AGUA, true, aguaHoras * 60)
            } else if (tipo == ReminderScheduler.TIPO_MOV) {
                movActivo = true
                sharedPrefs.edit { putBoolean("recordatorio_mov_activo", true) }
                ReminderScheduler.programar(context, ReminderScheduler.TIPO_MOV)
                registrar(ReminderScheduler.TIPO_MOV, true, movMin)
            }
        } else {
            permisoDenegado = true
            if (tipo == ReminderScheduler.TIPO_AGUA) {
                aguaActivo = false
                sharedPrefs.edit { putBoolean("recordatorio_agua_activo", false) }
                ReminderScheduler.cancelar(context, ReminderScheduler.TIPO_AGUA)
            } else if (tipo == ReminderScheduler.TIPO_MOV) {
                movActivo = false
                sharedPrefs.edit { putBoolean("recordatorio_mov_activo", false) }
                ReminderScheduler.cancelar(context, ReminderScheduler.TIPO_MOV)
            }
        }
    }

    fun onToggleSwitch(tipo: String, newState: Boolean) {
        if (!newState) {
            registrar(tipo, false, 0)
            if (tipo == ReminderScheduler.TIPO_AGUA) {
                aguaActivo = false
                sharedPrefs.edit { putBoolean("recordatorio_agua_activo", false) }
                ReminderScheduler.cancelar(context, ReminderScheduler.TIPO_AGUA)
            } else {
                movActivo = false
                sharedPrefs.edit { putBoolean("recordatorio_mov_activo", false) }
                ReminderScheduler.cancelar(context, ReminderScheduler.TIPO_MOV)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                pendingTipo = tipo
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                permisoDenegado = false
                registrar(tipo, true, if (tipo == ReminderScheduler.TIPO_AGUA) aguaHoras * 60 else movMin)
                if (tipo == ReminderScheduler.TIPO_AGUA) {
                    aguaActivo = true
                    sharedPrefs.edit { putBoolean("recordatorio_agua_activo", true) }
                    ReminderScheduler.programar(context, ReminderScheduler.TIPO_AGUA)
                } else {
                    movActivo = true
                    sharedPrefs.edit { putBoolean("recordatorio_mov_activo", true) }
                    ReminderScheduler.programar(context, ReminderScheduler.TIPO_MOV)
                }
            }
        }
    }

    val opcionesAgua = listOf(1 to "1 h", 2 to "2 h", 3 to "3 h")
    val opcionesMov = listOf(45 to "45 min", 60 to "1 h", 120 to "2 h")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Recordatorios",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Tarjeta 1: Agua
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, BorderGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recordatorio de agua",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = aguaActivo,
                            onCheckedChange = { checked ->
                                onToggleSwitch(ReminderScheduler.TIPO_AGUA, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    if (aguaActivo) {
                        Text(
                            text = "Intervalo de aviso",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            opcionesAgua.forEach { (horas, label) ->
                                FilterChip(
                                    selected = aguaHoras == horas,
                                    onClick = {
                                        aguaHoras = horas
                                        sharedPrefs.edit { putInt("recordatorio_agua_horas", horas) }
                                        ReminderScheduler.programar(context, ReminderScheduler.TIPO_AGUA)
                                        registrar(ReminderScheduler.TIPO_AGUA, true, horas * 60)
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Tarjeta 2: Movimiento
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, BorderGray)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pausa de movimiento",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = movActivo,
                            onCheckedChange = { checked ->
                                onToggleSwitch(ReminderScheduler.TIPO_MOV, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    if (movActivo) {
                        Text(
                            text = "Intervalo de aviso",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            opcionesMov.forEach { (min, label) ->
                                FilterChip(
                                    selected = movMin == min,
                                    onClick = {
                                        movMin = min
                                        sharedPrefs.edit { putInt("recordatorio_mov_min", min) }
                                        ReminderScheduler.programar(context, ReminderScheduler.TIPO_MOV)
                                        registrar(ReminderScheduler.TIPO_MOV, true, min)
                                    },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Solo recibirás avisos entre las 08:00 y las 22:00.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (permisoDenegado) {
                Text(
                    text = "Sin permiso de notificaciones no podemos enviarte recordatorios.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Los recordatorios son opcionales y se pueden desactivar cuando quieras.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

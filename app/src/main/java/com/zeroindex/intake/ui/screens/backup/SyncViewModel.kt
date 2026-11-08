package com.zeroindex.intake.ui.screens.backup

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.dao.SyncConflictDao
import com.zeroindex.intake.data.db.entity.SyncConflictEntity
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.settings.SyncSettings
import com.zeroindex.intake.data.sync.PairingCode
import com.zeroindex.intake.data.sync.SyncDevice
import com.zeroindex.intake.data.sync.SyncEngine
import com.zeroindex.intake.data.sync.SyncException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** One field that differs between the kept and the other version of a row. */
data class FieldChange(val field: String, val kept: String, val other: String)

@HiltViewModel
class SyncViewModel @Inject constructor(
    settings: SettingsRepository,
    conflictDao: SyncConflictDao,
    private val engine: SyncEngine,
) : ViewModel() {

    val sync: StateFlow<SyncSettings> = settings.settings.map { it.sync }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSettings())
    val conflicts: StateFlow<List<SyncConflictEntity>> = conflictDao.observeOpen()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serverUrl = MutableStateFlow("")
    val code = MutableStateFlow("")
    val deviceName = MutableStateFlow(listOfNotNull(Build.MANUFACTURER?.replaceFirstChar(Char::uppercase), Build.MODEL).joinToString(" ").ifBlank { "Android device" })
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val devices = MutableStateFlow<List<SyncDevice>>(emptyList())
    val newCode = MutableStateFlow<PairingCode?>(null)

    private fun run(block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        error.value = null
        try {
            block()
        } catch (e: SyncException) {
            error.value = e.message
        } finally {
            busy.value = false
        }
    }

    fun pair() = run {
        engine.pair(serverUrl.value, code.value, deviceName.value)
        code.value = ""
        engine.sync()
        devices.value = engine.devices()
    }

    fun syncNow() = run {
        engine.sync()
        devices.value = engine.devices()
    }

    fun loadDevices() = run { devices.value = engine.devices() }
    fun addDevice() = run { newCode.value = engine.newPairingCode() }
    fun remove(device: SyncDevice) = run {
        engine.revoke(device.id)
        devices.value = engine.devices()
    }
    fun unpair() = run {
        engine.unpair()
        devices.value = emptyList()
    }

    fun restore(conflict: SyncConflictEntity) = viewModelScope.launch { engine.restore(conflict.id) }
    fun keepCurrent(conflict: SyncConflictEntity) = viewModelScope.launch { engine.dismiss(conflict.id) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val bookkeeping = setOf("id", "createdAt", "updatedAt")

        /** The fields that actually differ, so a conflict reads as "lunch: rice → rice and beans". */
        fun changes(conflict: SyncConflictEntity): List<FieldChange> {
            val kept = runCatching { json.parseToJsonElement(conflict.keptJson).jsonObject }.getOrDefault(JsonObject(emptyMap()))
            val other = runCatching { json.parseToJsonElement(conflict.otherJson).jsonObject }.getOrDefault(JsonObject(emptyMap()))
            return (kept.keys + other.keys).filter { it !in bookkeeping && kept[it] != other[it] }.map {
                FieldChange(it, kept[it]?.toString()?.trim('"') ?: "—", other[it]?.toString()?.trim('"') ?: "—")
            }
        }
    }
}

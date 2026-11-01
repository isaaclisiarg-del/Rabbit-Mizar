package com.aura.caradeverga

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.open(application)
    val repository = Repository(db)
    val ledger = MutableStateFlow(Ledger())
    val loaded = MutableStateFlow(false)
    val fatal = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val pendingImport = MutableStateFlow<Ledger?>(null)
    val today = MutableStateFlow(LocalDate.now())
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    init {
        viewModelScope.launch {
            try {
                repository.initialize()
                repository.ledgers.collect { ledger.value = it; loaded.value = true }
            } catch (e: Exception) { fatal.value = "No se pudo abrir la base de datos. Cerrá y volvé a abrir la app. Tus datos no se eliminaron." }
        }
        viewModelScope.launch { while (isActive) { today.value = LocalDate.now(); delay(30_000) } }
    }
    fun refreshDate() { today.value = LocalDate.now() }
    fun action(success: String? = null, after: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
                ledger.value = repository.snapshot()
                after()
                if (success != null) messages.send(success)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                messages.send(e.message?.take(250) ?: "No se pudo completar la operación.")
            } finally { busy.value = false }
        }
    }
    fun export(uri: Uri) = action("Copia de seguridad exportada.") {
        val text = Backup.encode(repository.snapshot())
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            (resolver.openOutputStream(uri, "wt") ?: error("No se pudo escribir el archivo.")).use {
                it.write(text.toByteArray(Charsets.UTF_8)); it.flush()
            }
        }
    }
    fun readImport(uri: Uri) = action {
        val text = withContext(Dispatchers.IO) {
            (getApplication<Application>().contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo.")).use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer); if (n == -1) break
                    require(out.size() + n <= Backup.MAX_BYTES) { "La copia supera los 20 MB." }
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
        }
        pendingImport.value = withContext(Dispatchers.Default) { Backup.decode(text, today.value) }
    }
    override fun onCleared() { super.onCleared(); db.close() }
}

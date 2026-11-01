package com.aura.caradeverga

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class BackupFileTest {
    @Test fun viewModelWritesAndReadsActualBackupFile() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.deleteDatabase("caradeverga.db")
        val store = ViewModelStore()
        val vm = AppViewModel(app); store.put("vm", vm)
        val file = File(app.cacheDir, "roundtrip.json")
        try {
            val original = runBlocking { vm.repository.initialize(); vm.repository.loadDemo(LocalDate.now()); vm.repository.snapshot() }
            vm.export(Uri.fromFile(file))
            awaitAction(vm)
            assertTrue("Backup file must exist", file.isFile)
            assertEquals(original, Backup.decode(file.readText()))
            vm.readImport(Uri.fromFile(file)); awaitAction(vm)
            assertEquals(original, vm.pendingImport.value)
            // Reading a valid import is only a preview; it does not modify the database.
            assertEquals(original, runBlocking { vm.repository.snapshot() })
            file.writeText("{bad json")
            vm.pendingImport.value = null
            vm.readImport(Uri.fromFile(file)); awaitAction(vm)
            assertNull(vm.pendingImport.value)
            assertEquals(original, runBlocking { vm.repository.snapshot() })
        } finally { store.clear(); app.deleteDatabase("caradeverga.db"); file.delete() }
    }
    private fun awaitAction(vm: AppViewModel) {
        val deadline = System.nanoTime() + 15_000_000_000L
        while (vm.busy.value && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertFalse("Operation did not complete", vm.busy.value)
    }
}

package com.aura.caradeverga

import android.app.Application
import android.app.DatePickerDialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR-w412dp-h915dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private lateinit var app: Application
    private fun awaitState(condition: () -> Boolean) {
        compose.waitUntil(10_000) { shadowOf(Looper.getMainLooper()).idle(); condition() }
    }
    @After fun cleanup() { store.clear(); if (::app.isInitialized) app.deleteDatabase("caradeverga.db") }
    private fun render(filename: String, fontScale: Float = 1f) {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("caradeverga.db")
        val vm = AppViewModel(app); store.put("app", vm)
        runBlocking { vm.repository.initialize(); vm.repository.loadDemo(LocalDate.now()) }
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) { CaraTheme { CaraApp(vm) } } }
        awaitState { vm.loaded.value }
        compose.onNodeWithText("Mati Créditos").assertExists()
        compose.onNodeWithText("Hoy").assertExists()
        capture(filename)
    }
    private fun capture(filename: String) {
        val output = File("build/screenshots/$filename.png"); output.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun lightHome() { render("inicio-claro") }
    @Test @Config(qualifiers = "es-rAR-w412dp-h915dp-night-xhdpi") fun darkHome() { render("inicio-oscuro") }
    @Test @Config(qualifiers = "es-rAR-w360dp-h800dp-xhdpi") fun compactPhone() { render("inicio-compacto", 1.3f) }
    @Test fun completeAppFlowCreatesClientTwoLoansAndPartialPayment() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("caradeverga.db")
        val vm = AppViewModel(app); store.put("app", vm)
        compose.setContent { CaraTheme { CaraApp(vm) } }
        awaitState { vm.loaded.value }
        compose.onNodeWithText("Nuevo préstamo", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Crear cliente y continuar").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Cliente QA")
        compose.onNodeWithText("Guardar").performClick()
        awaitState { vm.ledger.value.customers.size == 1 && !vm.busy.value }
        compose.onNodeWithText("Capital prestado").performTextInput("200000")
        compose.onNodeWithText("Crear préstamo").performScrollTo().assertIsEnabled().performClick()
        awaitState { vm.ledger.value.loans.size == 1 && !vm.busy.value }
        compose.onNodeWithText("Cuota 1 de 2").performScrollTo().performClick()
        compose.onNodeWithText("Monto recibido").performTextReplacement("80000")
        compose.onNodeWithText("Registrar pago").performClick()
        awaitState { vm.ledger.value.payments.size == 1 && !vm.busy.value }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Nuevo préstamo", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Capital prestado").performTextInput("100000")
        compose.onNodeWithText("4").performClick()
        compose.onNodeWithText("Crear préstamo").performScrollTo().assertIsEnabled().performClick()
        try { awaitState { vm.ledger.value.loans.size == 2 && !vm.busy.value } }
        catch (e: Throwable) {
            println("After second loan: loans=${vm.ledger.value.loans.size}, busy=${vm.busy.value}, fatal=${vm.fatal.value}")
            println(compose.onRoot(useUnmergedTree = true).printToString())
            throw e
        }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("$ 340.000").assertExists()
        val s = vm.ledger.value
        Assert.assertEquals(2, s.active(s.customers.single()))
        Assert.assertEquals(80_000L, s.payments.single().amount)
        compose.onNodeWithText("Archivar").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar").performClick()
        awaitState { vm.ledger.value.customers.single().archived }
        Assert.assertEquals(340_000L, vm.ledger.value.pending)
    }

    private fun pickDate(label: String, value: LocalDate) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog() as DatePickerDialog
            dialog.updateDate(value.year, value.monthValue - 1, value.dayOfMonth)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
    }

    @Test fun startedLoanAppFlowKeepsOtherLoansAndDisplaysOnlyRemainingDebt() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("caradeverga.db")
        val vm = AppViewModel(app); store.put("app", vm)
        val today = LocalDate.now()
        val firstDue = today.minusMonths(1)
        val customerId = runBlocking {
            vm.repository.initialize()
            val id = vm.repository.saveCustomer(Customer(name = "Isaac"))
            vm.repository.newLoan(id, 100_000, 2, today, today.plusMonths(1), Frequency.MONTHLY, today)
            vm.repository.updatePlans(listOf(Plan(1, 1500), Plan(2, 3000), Plan(3, 5500), Plan(4, 6000)))
            id
        }
        compose.setContent { CaraTheme { CaraApp(vm) } }
        awaitState { vm.loaded.value && vm.ledger.value.loans.size == 1 }
        compose.onNodeWithText("Nuevo préstamo", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Isaac").performClick()
        compose.onNodeWithText("Ya iniciado").performClick()
        compose.onNodeWithText("Capital prestado").performTextInput("200000")
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithText("Interés total pactado").performScrollTo().performTextReplacement("45")
        pickDate("Fecha original del préstamo", firstDue.minusDays(5))
        pickDate("Vencimiento de la primera cuota original", firstDue)
        compose.onNodeWithText("Ya iniciado").performScrollTo()
        capture("iniciado-datos")
        compose.onNodeWithText("Agregar pago a cuota 1").performScrollTo().performClick()
        compose.onNodeWithText("Agregar pago anterior").performClick()
        compose.onNodeWithText("Agregar pago a cuota 2").performScrollTo().performClick()
        compose.onNodeWithText("Monto recibido").performTextReplacement("20000")
        compose.onNodeWithText("Agregar pago anterior").performClick()
        compose.onNodeWithText("Pagos anteriores").performScrollTo()
        capture("iniciado-pagos")
        compose.onNodeWithText("Guardar préstamo iniciado").performScrollTo().performClick()
        awaitState { vm.ledger.value.loans.size == 2 && !vm.busy.value }
        val s = vm.ledger.value
        val started = s.loans.single { it.installmentCount == 3 }
        Assert.assertEquals(4500, started.interestRateBps)
        Assert.assertEquals(173_334L, s.balance(started))
        Assert.assertEquals(2, s.active(s.customerById.getValue(customerId)))
        Assert.assertEquals(2, s.payments.size)
        compose.onNodeWithText("$ 173.334").assertExists()
        capture("iniciado-guardado")
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("$ 303.334").assertExists()
    }
}

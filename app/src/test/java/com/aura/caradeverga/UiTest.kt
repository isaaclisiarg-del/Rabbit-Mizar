package com.aura.caradeverga

import android.content.Context
import android.content.DialogInterface
import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR-w412dp-h915dp-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 9, 8)
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = androidx.room.Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = Repository(db); repo.initialize(); repo.loadDemo(today); Unit
    }
    @After fun cleanup() { db.close() }

    @Test fun homeShowsWhoOwesTodayAndOpeningACardWorks() {
        val s = runBlocking { repo.snapshot() }
        var clicked = 0L
        compose.setContent { CaraTheme { HomeScreen(s, today) { clicked = it.id } } }
        compose.onNodeWithText("Hoy").assertExists()
        compose.onNodeWithText("Isaac").performClick()
        Assert.assertTrue(clicked > 0)
        compose.onNodeWithText("Vencidos").performScrollTo().assertExists()
        compose.onNodeWithText("Pedro").performScrollTo().assertExists()
        Unit
    }

    @Test fun paymentFormSendsPartialAmountAndKeepsAmountVisible() {
        val s = runBlocking { repo.snapshot() }
        val i = s.installments.first { s.status(it, today) == InstallmentStatus.PARTIAL }
        var received = 0L
        compose.setContent { CaraTheme { Box(Modifier.fillMaxSize()) { PaymentDialog(s, i, today, false, {}, { amount, _ -> received = amount }, {}) } } }
        compose.onNodeWithText("Monto recibido").performTextReplacement("10000")
        compose.onNodeWithText("Registrar pago").performClick()
        Assert.assertEquals(10_000L, received)
    }

    @Test fun newLoanUsesSelectedPlanAndCreatesRealRepositoryLoan() {
        val s = runBlocking { repo.snapshot() }; val customer = s.customers.first { it.name == "Isaac" }
        var created = 0L
        compose.setContent { CaraTheme { NewLoanScreen(customer, s.plans, today, false) { input ->
            runBlocking { created = repo.newLoan(customer.id, input, today) }
        } } }
        compose.onNodeWithText("Capital prestado").performTextInput("200000")
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithText("Crear préstamo").performScrollTo().performClick()
        Assert.assertTrue(created > 0)
        val saved = runBlocking { repo.snapshot() }.loanById.getValue(created)
        Assert.assertEquals(290_000L, saved.totalAmount)
        Assert.assertEquals(3, saved.installmentCount)
    }

    @Test fun zeroInterestChipCreatesAnIndividualInterestFreeLoan() {
        val s = runBlocking { repo.snapshot() }
        val customer = s.customers.first { it.name == "Isaac" }
        var created = 0L
        compose.setContent { CaraTheme { NewLoanScreen(customer, s.plans, today, false) { input ->
            runBlocking { created = repo.newLoan(customer.id, input, today) }
        } } }
        compose.onNodeWithText("Capital prestado").performTextInput("10000")
        compose.onNodeWithText("Sin intereses (0%)").performScrollTo().performClick()
        compose.onNodeWithText("$ 10.000").performScrollTo().assertExists()
        compose.onNodeWithText("Crear préstamo").performScrollTo().performClick()
        val saved = runBlocking { repo.snapshot() }
        Assert.assertTrue(created > 0)
        Assert.assertEquals(0, saved.loanById.getValue(created).interestRateBps)
        Assert.assertEquals(10_000L, saved.loanById.getValue(created).totalAmount)
        Assert.assertEquals(s.plans, saved.plans)
    }

    @Test fun loanCardHighlightsOriginalCapitalNotAmountWithInterest() {
        val s = runBlocking { repo.snapshot() }
        val loan = s.loans.first { it.principal == 200_000L }
        var opened = false
        compose.setContent { CaraTheme { LoanCard(s, loan, today) { opened = true } } }
        compose.onNodeWithText("Capital prestado").assertExists()
        compose.onNodeWithText(Money.format(loan.principal)).assertExists()
        compose.onNodeWithText("Ganancia pactada").assertExists()
        compose.onNodeWithText("+${Money.format(loan.expectedProfit)}").assertExists()
        compose.onNodeWithText("Interés: ${Money.rate(loan.interestRateBps)}").assertExists()
        compose.onNodeWithText("${loan.installmentCount} ${if (loan.installmentCount == 1) "cuota" else "cuotas"}").assertExists()
        // El desglose y los saldos están en el detalle, no en esta tarjeta.
        compose.onNodeWithText("Total pactado:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Por cuota:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Pendiente actual:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Préstamo del", substring = true).assertDoesNotExist()
        compose.onNode(hasClickAction()).performClick()
        Assert.assertTrue("La tarjeta debe seguir abriendo el detalle", opened)
    }

    @Test fun loanCardDisplaysInterestFreeContractsWithoutChangingFinancialData() {
        val s = runBlocking { repo.snapshot() }
        val original = s.loans.first()
        val free = original.copy(
            interestRateBps = 0,
            expectedProfit = 0L,
            totalAmount = original.principal
        )
        compose.setContent { CaraTheme { LoanCard(s, free, today) {} } }
        compose.onNodeWithText(Money.format(free.principal)).assertExists()
        compose.onNodeWithText("Ganancia pactada").assertExists()
        compose.onNodeWithText("+${Money.format(0)}").assertExists()
        compose.onNodeWithText("Interés: 0 %").assertExists()
        Assert.assertEquals(original, s.loans.first())
    }

        @Test fun appearanceSelectorCanSwitchIndependentlyOfFinancialPlans() {
        val s = runBlocking { repo.snapshot() }
        var chosen = AppearanceMode.SYSTEM
        compose.setContent { CaraTheme { SettingsScreen(s, false, {}, {}, {}, {},
            appearanceMode = chosen, onAppearanceChange = { chosen = it }) } }
        compose.onNodeWithText("Claro").performScrollTo().performClick()
        Assert.assertEquals(AppearanceMode.LIGHT, chosen)
        Assert.assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStored("unrecognized"))
        Assert.assertEquals(AppearanceMode.DARK, AppearanceMode.fromStored("DARK"))
    }

    @Test fun settingsButtonsHaveActionsAndAllFourPlansSave() {
        val s = runBlocking { repo.snapshot() }
        var exported = false; var imported = false; var saved: List<Plan>? = null
        compose.setContent { MaterialTheme { SettingsScreen(s, false, { saved = it }, { exported = true }, { imported = true }, {}) } }
        compose.onNodeWithText("Guardar planes").performScrollTo().performClick()
        Assert.assertEquals(s.plans, saved)
        compose.onNodeWithText("Exportar copia de seguridad").performScrollTo().performClick()
        compose.onNodeWithText("Importar copia de seguridad").performScrollTo().performClick()
        Assert.assertTrue(exported && imported)
    }

    private fun pickDate(label: String, value: LocalDate) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog() as DatePickerDialog
            dialog.updateDate(value.year, value.monthValue - 1, value.dayOfMonth)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        }
    }

    @Test fun startedLoanFormKeepsDraftAndSavesOriginalDatesAndMultiplePayments() {
        val s = runBlocking { repo.snapshot() }
        val customer = s.customers.first { it.name == "Isaac" }
        var created = 0L
        val restoration = StateRestorationTester(compose)
        restoration.setContent { CaraTheme { NewLoanScreen(customer, s.plans, today, false) { input ->
            runBlocking { created = repo.newLoan(customer.id, input, today) }
        } } }
        compose.onNodeWithText("Ya iniciado").performClick()
        compose.onNodeWithText("Capital prestado").performTextInput("200000")
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithText("Interés total pactado").performScrollTo().performTextReplacement("45")
        pickDate("Fecha original del préstamo", LocalDate.of(2026, 7, 10))
        pickDate("Vencimiento de la primera cuota original", LocalDate.of(2026, 8, 5))
        compose.onNodeWithText("Agregar pago a cuota 1").performScrollTo().performClick()
        compose.onNodeWithText("Agregar pago anterior").performClick()
        compose.onNodeWithText("Agregar pago a cuota 2").performScrollTo().performClick()
        compose.onNodeWithText("Monto recibido").performTextReplacement("8000")
        pickDate("Fecha del pago", LocalDate.of(2026, 9, 7))
        compose.onNodeWithText("Agregar pago anterior").performClick()
        compose.onNodeWithText("Agregar pago a cuota 2").performScrollTo().performClick()
        compose.onNodeWithText("Monto recibido").performTextReplacement("200000")
        compose.onNodeWithText("Agregar pago anterior").performClick()
        compose.onNodeWithText("El pago no puede superar $ 88.666.").assertExists()
        compose.onNodeWithText("Monto recibido").performTextReplacement("12000")
        pickDate("Fecha del pago", today)
        compose.onNodeWithText("Agregar pago anterior").performClick()
        Assert.assertEquals(s, runBlocking { repo.snapshot() }) // Drafts do not write partial records.
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("$ 173.334").performScrollTo().assertExists()
        compose.onNodeWithText("Guardar préstamo iniciado").performScrollTo().performClick()
        Assert.assertTrue(created > 0L)
        val saved = runBlocking { repo.snapshot() }
        val loan = saved.loanById.getValue(created)
        val parts = saved.installmentsByLoan.getValue(created).sortedBy { it.number }
        Assert.assertEquals("2026-07-10", loan.loanDate)
        Assert.assertEquals("2026-08-05", loan.firstDueDate)
        Assert.assertEquals(4500, loan.interestRateBps)
        Assert.assertEquals(173_334L, saved.balance(loan))
        Assert.assertEquals(3, saved.active(customer))
        Assert.assertEquals(listOf(InstallmentStatus.PAID, InstallmentStatus.OVERDUE, InstallmentStatus.PENDING), parts.map { saved.status(it, today) })
        Assert.assertEquals(listOf("2026-09-07", "2026-09-08"), saved.paymentsByInstallment.getValue(parts[1].id).map { it.paymentDate })
        Assert.assertEquals(s.loans, saved.loans.filter { it.id != created })
    }
}

package com.aura.caradeverga

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class LedgerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private lateinit var name: String
    private val today = LocalDate.of(2026, 9, 8)
    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        name = "test-${System.nanoTime()}.db"
        db = AppDatabase.open(context, name); repo = Repository(db); repo.initialize()
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private suspend fun isaac() = repo.saveCustomer(Customer(name = "Isaac"))
    private suspend fun loan(customer: Long, capital: Long = 200_000, count: Int = 2,
        due: LocalDate = today, date: LocalDate = today.minusMonths(1)) =
        repo.newLoan(customer, capital, count, date, due, Frequency.MONTHLY, today)

    @Test fun exactPlansAndRemainder() {
        assertEquals(listOf(230_000L, 260_000L, 290_000L, 320_000L), (1..4).map { LoanMath.quote(200_000, it * 1500, it).total })
        assertEquals(listOf(96_666L, 96_666L, 96_668L), LoanMath.quote(200_000, 4500, 3).amounts)
        for (capital in listOf(1L, 99L, 101L, 200_000L, Money.MAX_PRINCIPAL)) for (n in 1..4) {
            val q = runCatching { LoanMath.quote(capital, n * 1500, n) }.getOrNull() ?: continue
            assertEquals(q.total, q.amounts.sum()); assertTrue(q.amounts.all { it > 0 })
        }
        assertEquals(102L, LoanMath.quote(101, 1, 1).total)
        assertEquals(200_000L, Money.parse("$ 200.000"))
        assertThrows(IllegalArgumentException::class.java) { Money.parse("100.50") }
        assertThrows(IllegalArgumentException::class.java) { Money.parse("-5") }
        assertEquals(3525, Money.parseRate("35,25"))
    }

    @Test fun datesStayAnchoredAndOverdueIsStrict() {
        val first = LocalDate.of(2026, 1, 31)
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), LoanMath.dates(first, 4, Frequency.MONTHLY).map { it.toString() })
        assertEquals(LocalDate.of(2026, 2, 15), LoanMath.dates(first, 2, Frequency.BIWEEKLY)[1])
        assertEquals(LocalDate.of(2026, 2, 7), LoanMath.dates(first, 2, Frequency.WEEKLY)[1])
    }

    @Test fun simultaneousLoansAndPermanentRates() = runBlocking {
        val c = isaac()
        val a = loan(c)
        val b = loan(c, 100_000, 4)
        var s = repo.snapshot()
        assertEquals(2, s.active(s.customerById.getValue(c)))
        assertEquals(420_000L, s.debt(s.customerById.getValue(c)))
        assertEquals(300_000L, s.principalPending)
        repo.updatePlans(listOf(Plan(1, 1500), Plan(2, 3500), Plan(3, 4500), Plan(4, 6000)))
        val third = loan(c)
        s = repo.snapshot()
        assertEquals(3000, s.loanById.getValue(a).interestRateBps)
        assertEquals(260_000L, s.loanById.getValue(a).totalAmount)
        assertEquals(160_000L, s.loanById.getValue(b).totalAmount)
        assertEquals(270_000L, s.loanById.getValue(third).totalAmount)
        assertEquals(3500, s.loanById.getValue(third).interestRateBps)
    }

    @Test fun zeroInterestIsPerLoanAndNeverChangesGlobalPlans() = runBlocking {
        val customerId = isaac()
        val ordinaryId = loan(customerId, 200_000, 2)
        val beforePlans = repo.snapshot().plans
        val noInterestId = repo.newLoan(customerId,
            LoanInput(10_000, 2, today.minusDays(1), today, Frequency.MONTHLY, agreedRateBps = 0), today)
        var s = repo.snapshot()
        val free = s.loanById.getValue(noInterestId)
        val ordinary = s.loanById.getValue(ordinaryId)
        assertEquals(0, free.interestRateBps)
        assertEquals(10_000L, free.principal)
        assertEquals(10_000L, free.totalAmount)
        assertEquals(0L, free.expectedProfit)
        assertEquals(listOf(5_000L, 5_000L), s.installmentsByLoan.getValue(free.id).sortedBy { it.number }.map { it.originalAmount })
        assertEquals(beforePlans, s.plans)
        assertEquals(260_000L, ordinary.totalAmount)
        assertEquals(3000, ordinary.interestRateBps)
        val amounts = s.activeLoanAmounts(s.customerById.getValue(customerId))
        assertEquals(210_000L, amounts.originalCapital)
        assertEquals(60_000L, amounts.agreedInterest)
        assertEquals(270_000L, amounts.agreedTotal)
        assertEquals(270_000L, amounts.pending)
        val firstFree = s.installmentsByLoan.getValue(free.id).first { it.number == 1 }
        repo.registerPayment(firstFree.id, 2_000L, today, today)
        s = repo.snapshot()
        assertEquals(8_000L, s.balance(free))
        assertEquals(268_000L, s.debt(s.customerById.getValue(customerId)))
        assertEquals(210_000L, s.activeLoanAmounts(s.customerById.getValue(customerId)).originalCapital)
        assertEquals(0L, s.allocations.filter { it.payment.installmentId == firstFree.id }.sumOf { it.interest })
        assertEquals(s, Backup.decode(Backup.encode(s), today))
    }

    @Test fun zeroInterestOnHistoricalLoanSupportsPreviousPayments() = runBlocking {
        val customerId = isaac()
        val input = LoanInput(10_000, 2, today.minusMonths(2), today.minusMonths(1),
            Frequency.MONTHLY, agreedRateBps = 0,
            previousPayments = listOf(PreviousPayment(1, 5_000, today.minusDays(10))))
        val id = repo.newLoan(customerId, input, today)
        val s = repo.snapshot()
        assertEquals(0L, s.loanById.getValue(id).expectedProfit)
        assertEquals(5_000L, s.balance(s.loanById.getValue(id)))
        assertEquals(5_000L, s.allocations.sumOf { it.principal })
        assertEquals(0L, s.allocations.sumOf { it.interest })
    }

    @Test fun partialPaymentsHistoryDeletionAndClosure() = runBlocking {
        val l = loan(isaac())
        var s = repo.snapshot(); val i = s.installmentsByLoan.getValue(l).first()
        val p = repo.registerPayment(i.id, 80_000, today, today)
        s = repo.snapshot()
        assertEquals(80_000L, s.paid(i)); assertEquals(50_000L, s.remaining(i))
        assertEquals(InstallmentStatus.PARTIAL, s.status(i, today))
        repo.registerPayment(i.id, 50_000, today, today)
        s = repo.snapshot()
        assertEquals(InstallmentStatus.PAID, s.status(i, today))
        assertEquals(2, s.paymentsByInstallment.getValue(i.id).size)
        assertEquals(today.toString(), s.completionDate(i))
        repo.deletePayment(p)
        s = repo.snapshot(); assertEquals(80_000L, s.remaining(i)); assertEquals(InstallmentStatus.PARTIAL, s.status(i, today))
        repo.registerPayment(i.id, 80_000, today, today)
        val last = s.installmentsByLoan.getValue(l).last()
        repo.registerPayment(last.id, last.originalAmount, today, today)
        s = repo.snapshot()
        assertEquals(0L, s.balance(s.loanById.getValue(l)))
        assertEquals(200_000L, s.allocations.sumOf { it.principal }); assertEquals(60_000L, s.allocations.sumOf { it.interest })
        assertEquals(0L, s.principalPending); assertEquals(0L, s.interestPending)
        assertEquals(1, s.loans.size); assertEquals(3, s.payments.size)
    }

    @Test fun overduePartialAndPaidTransitions() = runBlocking {
        val l = loan(isaac())
        var s = repo.snapshot(); val i = s.installmentsByLoan.getValue(l).first()
        assertEquals(InstallmentStatus.PENDING, s.status(i, today))
        assertEquals(InstallmentStatus.OVERDUE, s.status(i, today.plusDays(1)))
        repo.registerPayment(i.id, 1, today, today); s = repo.snapshot()
        assertEquals(InstallmentStatus.PARTIAL, s.status(i, today))
        assertEquals(InstallmentStatus.OVERDUE, s.status(i, today.plusDays(1)))
        repo.registerPayment(i.id, 129_999, today, today); s = repo.snapshot()
        assertEquals(InstallmentStatus.PAID, s.status(i, today.plusYears(2)))
    }

    @Test fun persistenceSurvivesDatabaseCloseAndReopen() = runBlocking {
        repo.loadDemo(today)
        val before = repo.snapshot()
        db.close()
        db = AppDatabase.open(context, name); repo = Repository(db)
        assertEquals(before, repo.snapshot())
        val states = repo.snapshot().installments.map { repo.snapshot().status(it, today) }.toSet()
        assertEquals(InstallmentStatus.entries.toSet(), states)
    }

    @Test fun completeBackupRoundTripAndSafeRejection() = runBlocking {
        repo.loadDemo(today)
        val original = repo.snapshot()
        val encoded = Backup.encode(original)
        val decoded = Backup.decode(encoded, today)
        assertEquals(original, decoded)
        val extra = repo.saveCustomer(Customer(name = "Temporal"))
        repo.restore(decoded, today)
        assertEquals(original, repo.snapshot()); assertFalse(repo.snapshot().customerById.containsKey(extra))
        val invalid = original.copy(payments = original.payments + Payment(id = 999, installmentId = original.installments.first().id, amount = Long.MAX_VALUE, paymentDate = today.toString()))
        assertTrue(runCatching { repo.restore(invalid, today) }.isFailure)
        assertEquals(original, repo.snapshot())
        assertTrue(runCatching { Backup.decode(encoded.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"), today) }.isFailure)
        val duplicate = original.copy(customers = original.customers + original.customers.first())
        assertTrue(runCatching { repo.restore(duplicate, today) }.isFailure)
        assertEquals(original, repo.snapshot())
    }

    @Test fun monthlyCashUsesPaymentDatesAndExpectedUsesDueDates() = runBlocking {
        val c = isaac()
        val l = loan(c, due = LocalDate.of(2026, 9, 10))
        var s = repo.snapshot()
        val i = s.installmentsByLoan.getValue(l).first()
        repo.registerPayment(i.id, 80_000, LocalDate.of(2026, 9, 1), today)
        repo.registerPayment(i.id, 50_000, LocalDate.of(2026, 9, 8), today)
        s = repo.snapshot()
        val september = s.monthly(YearMonth.of(2026, 9), today)
        assertEquals(0L, september.lent)
        assertEquals(130_000L, september.scheduled); assertEquals(30_000L, september.expectedInterest)
        assertEquals(130_000L, september.collected); assertEquals(100_000L, september.recovered); assertEquals(30_000L, september.interestCollected)
        val october = s.monthly(YearMonth.of(2026, 10), today)
        assertEquals(130_000L, october.pending); assertEquals(30_000L, october.expectedInterest)
        assertEquals(0L, october.interestCollected); assertEquals(0L, october.overdue)
    }

    @Test fun invalidAndConcurrentPaymentsCannotOverpay() = runBlocking {
        val l = loan(isaac(), count = 1)
        val i = repo.snapshot().installmentsByLoan.getValue(l).first()
        for (bad in listOf(0L, -1L, i.originalAmount + 1)) assertTrue(runCatching { repo.registerPayment(i.id, bad, today, today) }.isFailure)
        assertTrue(runCatching { repo.registerPayment(i.id, 1, today.plusDays(1), today) }.isFailure)
        val outcomes = coroutineScope { listOf(1, 2).map { async(Dispatchers.IO) { runCatching { repo.registerPayment(i.id, i.originalAmount, today, today) }.isSuccess } }.awaitAll() }
        assertEquals(1, outcomes.count { it }); assertEquals(i.originalAmount, repo.snapshot().paid(i))
    }

    @Test fun archiveKeepsDebtAndDeletionCascadesOnlySelectedLoan() = runBlocking {
        val c = isaac(); val a = loan(c); val b = loan(c, 100_000, 4)
        repo.archiveCustomer(c, true)
        var s = repo.snapshot()
        assertTrue(s.customerById.getValue(c).archived); assertEquals(420_000L, s.pending)
        assertTrue(runCatching { repo.deleteCustomer(c) }.isFailure)
        assertTrue(runCatching { loan(c) }.isFailure)
        val i = s.installmentsByLoan.getValue(a).first(); repo.registerPayment(i.id, 100, today, today)
        repo.deleteLoan(a)
        s = repo.snapshot()
        assertEquals(listOf(b), s.loans.map { it.id }); assertTrue(s.payments.isEmpty()); assertEquals(160_000L, s.pending)
        repo.deleteLoan(b); repo.deleteCustomer(c)
        assertTrue(repo.snapshot().customers.isEmpty())
    }

    @Test fun startedLoanKeepsOriginalRateDatesCashAndHistoryAcrossReopenAndBackup() = runBlocking {
        val c = isaac()
        val existing = loan(c, 100_000, 2, date = today)
        val originalLoan = repo.snapshot().loanById.getValue(existing)
        repo.updatePlans(listOf(Plan(1, 1500), Plan(2, 3000), Plan(3, 5500), Plan(4, 6000)))
        val input = LoanInput(200_000, 3, LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 20), Frequency.MONTHLY, 4500,
            listOf(PreviousPayment(1, 80_000, LocalDate.of(2026, 8, 20)),
                PreviousPayment(1, 16_666, LocalDate.of(2026, 8, 21)),
                PreviousPayment(2, 20_000, LocalDate.of(2026, 9, 5))))
        val id = repo.newLoan(c, input, today)
        var s = repo.snapshot()
        val saved = s.loanById.getValue(id)
        val parts = s.installmentsByLoan.getValue(id).sortedBy { it.number }
        assertEquals(2, s.active(s.customerById.getValue(c)))
        assertEquals(originalLoan, s.loanById.getValue(existing))
        assertEquals(4500, saved.interestRateBps)
        assertEquals(290_000L, saved.totalAmount)
        assertEquals(90_000L, saved.expectedProfit)
        assertEquals(listOf(96_666L, 96_666L, 96_668L), parts.map { it.originalAmount })
        assertEquals(listOf("2026-08-20", "2026-09-20", "2026-10-20"), parts.map { it.dueDate })
        assertEquals(listOf(InstallmentStatus.PAID, InstallmentStatus.PARTIAL, InstallmentStatus.PENDING), parts.map { s.status(it, today) })
        assertEquals(173_334L, s.balance(saved))
        assertEquals(303_334L, s.debt(s.customerById.getValue(c)))
        val august = s.monthly(YearMonth.of(2026, 8), today)
        val september = s.monthly(YearMonth.of(2026, 9), today)
        assertEquals(200_000L, august.lent)
        assertEquals(96_666L, august.collected)
        assertEquals(29_999L, august.interestCollected)
        assertEquals(100_000L, september.lent)
        assertEquals(20_000L, september.collected)
        assertEquals(6_207L, september.interestCollected)
        db.close(); db = AppDatabase.open(context, name); repo = Repository(db)
        assertEquals(s, repo.snapshot())
        val copy = Backup.decode(Backup.encode(s), today)
        repo.restore(copy, today)
        assertEquals(s, repo.snapshot())
        repo.registerPayment(parts[1].id, 76_666, today, today)
        s = repo.snapshot()
        assertEquals(InstallmentStatus.PAID, s.status(parts[1], today))
        assertEquals(96_668L, s.balance(saved))
        assertEquals(4, s.payments.size)
        assertEquals(5500, s.plans.single { it.count == 3 }.rateBps)
    }

    @Test fun startedLoansMayBeUnpaidOverdueOrCompletelyPaid() = runBlocking {
        val c = isaac()
        val input = LoanInput(200_000, 3, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 5), Frequency.MONTHLY, 4500)
        val unpaid = repo.newLoan(c, input, today)
        val paid = repo.newLoan(c, input.copy(previousPayments = listOf(
            PreviousPayment(1, 96_666, LocalDate.of(2026, 6, 5)),
            PreviousPayment(2, 96_666, LocalDate.of(2026, 7, 5)),
            PreviousPayment(3, 96_668, LocalDate.of(2026, 8, 5)))), today)
        val s = repo.snapshot()
        assertEquals(1, s.active(s.customerById.getValue(c)))
        assertEquals(290_000L, s.balance(s.loanById.getValue(unpaid)))
        assertEquals(290_000L, s.overdue(s.customerById.getValue(c), today))
        assertEquals(0L, s.balance(s.loanById.getValue(paid)))
        assertTrue(s.installmentsByLoan.getValue(paid).all { s.status(it, today) == InstallmentStatus.PAID })
        assertEquals(200_000L, s.allocations.sumOf { it.principal })
        assertEquals(90_000L, s.allocations.sumOf { it.interest })
    }

    @Test fun invalidPreviousPaymentsNeverLeaveHalfRegisteredLoans() = runBlocking {
        val c = isaac(); loan(c)
        val before = repo.snapshot()
        val base = LoanInput(200_000, 3, today.minusMonths(2), today.minusMonths(1), Frequency.MONTHLY, 4500)
        val valid = PreviousPayment(1, 96_000, today.minusDays(2))
        val badPayments = listOf(
            PreviousPayment(1, 667, today), PreviousPayment(2, 96_667, today),
            PreviousPayment(2, Long.MAX_VALUE, today), PreviousPayment(2, 0, today),
            PreviousPayment(2, -1, today), PreviousPayment(4, 1, today),
            PreviousPayment(2, 1, base.loanDate.minusDays(1)), PreviousPayment(2, 1, today.plusDays(1)))
        badPayments.forEach { bad ->
            assertTrue(runCatching { repo.newLoan(c, base.copy(previousPayments = listOf(valid, bad)), today) }.isFailure)
            assertEquals(before, repo.snapshot())
        }
        assertTrue(runCatching { repo.newLoan(c, base.copy(agreedRateBps = -1), today) }.isFailure)
        assertEquals(before, repo.snapshot())
    }
}

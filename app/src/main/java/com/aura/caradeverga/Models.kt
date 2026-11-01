package com.aura.caradeverga

import androidx.room.*
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Entity(tableName = "customers")
data class Customer(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val nickname: String = "", val phone: String = "", val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(), val archived: Boolean = false) {
    val displayName: String get() = listOf(name, nickname).filter { it.isNotBlank() }.joinToString(" · ")
}

@Entity(tableName = "loans", foreignKeys = [ForeignKey(entity = Customer::class,
    parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("customerId")])
data class Loan(@PrimaryKey(autoGenerate = true) val id: Long = 0, val customerId: Long,
    val principal: Long, val interestRateBps: Int, val installmentCount: Int,
    val totalAmount: Long, val expectedProfit: Long, val loanDate: String,
    val firstDueDate: String, val frequency: String,
    val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "installments", foreignKeys = [ForeignKey(entity = Loan::class,
    parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["loanId", "number"], unique = true), Index("dueDate")])
data class Installment(@PrimaryKey(autoGenerate = true) val id: Long = 0, val loanId: Long,
    val number: Int, val originalAmount: Long, val dueDate: String)

@Entity(tableName = "payments", foreignKeys = [ForeignKey(entity = Installment::class,
    parentColumns = ["id"], childColumns = ["installmentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("installmentId"), Index("paymentDate")])
data class Payment(@PrimaryKey(autoGenerate = true) val id: Long = 0, val installmentId: Long,
    val amount: Long, val paymentDate: String, val createdAt: Long = System.currentTimeMillis())

@Entity(tableName = "plans")
data class Plan(@PrimaryKey val count: Int, val rateBps: Int)

// Form input only. Historical payments become ordinary payment movements on save.
data class PreviousPayment(val installmentNumber: Int, val amount: Long, val date: LocalDate)
data class LoanInput(val principal: Long, val count: Int, val loanDate: LocalDate,
    val firstDue: LocalDate, val frequency: Frequency, val agreedRateBps: Int? = null,
    val previousPayments: List<PreviousPayment> = emptyList()) {
    fun validate(rateBps: Int, today: LocalDate): LoanMath.Quote {
        require(loanDate <= today) { "La fecha del préstamo no puede ser futura." }
        require(firstDue >= loanDate) { "La primera cuota no puede vencer antes del préstamo." }
        require(loanDate.year in 1900..2200 && firstDue.year in 1900..2200) { "Revisá las fechas." }
        val quote = LoanMath.quote(principal, rateBps, count)
        val paid = LongArray(count)
        previousPayments.forEach { p ->
            require(p.installmentNumber in 1..count) { "Un pago anterior pertenece a una cuota que ya no está en el plan." }
            val index = p.installmentNumber - 1
            require(p.amount > 0 && p.amount <= quote.amounts[index] - paid[index]) {
                "Los pagos anteriores de la cuota ${p.installmentNumber} superan su monto o no son válidos."
            }
            require(p.date in loanDate..today) { "La fecha de cada pago debe estar entre el préstamo y hoy." }
            paid[index] += p.amount
        }
        return quote
    }
}

enum class Frequency(val label: String) { WEEKLY("Semanal"), BIWEEKLY("Quincenal · 15 días"), MONTHLY("Mensual") }
enum class InstallmentStatus(val label: String) { PENDING("Pendiente"), PAID("Pagada"), PARTIAL("Parcial"), OVERDUE("Vencida") }

object Money {
    const val MAX_PRINCIPAL = 1_000_000_000_000L
    fun format(amount: Long): String = "$ " + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-AR")).format(amount)
    fun parse(text: String): Long {
        val value = text.trim().removePrefix("$").trim().replace(" ", "")
        require(value.matches(Regex("(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)"))) { "Ingresá pesos enteros, por ejemplo 100000 o 100.000." }
        return value.replace(".", "").toLongOrNull()?.also { require(it > 0) { "El monto debe ser mayor que cero." } }
            ?: throw IllegalArgumentException("El monto es demasiado grande.")
    }
    fun rate(value: Int): String = BigDecimal(value).movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',') + " %"
    fun parseRate(text: String): Int {
        val s = text.trim().replace(',', '.')
        require(s.matches(Regex("[0-9]{1,4}(?:\\.[0-9]{1,2})?"))) { "Usá un porcentaje con hasta dos decimales." }
        return BigDecimal(s).movePointRight(2).intValueExact().also { require(it in 0..100_000) { "El porcentaje debe estar entre 0 y 1.000 %." } }
    }
    fun fraction(amount: Long, numerator: Long, denominator: Long): Long {
        val value = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(numerator)).divide(BigInteger.valueOf(denominator))
        if (value.bitLength() > 63) throw ArithmeticException("El importe excede el límite admitido.")
        return value.toLong() // Exact after the range check; also available on Android 8–11.
    }
}

object LoanMath {
    data class Quote(val total: Long, val profit: Long, val amounts: List<Long>)
    fun quote(principal: Long, rateBps: Int, count: Int): Quote {
        require(principal in 1..Money.MAX_PRINCIPAL) { "El capital debe estar entre $ 1 y ${Money.format(Money.MAX_PRINCIPAL)}." }
        require(rateBps in 0..100_000 && count in 1..4) { "Plan de cuotas inválido." }
        // Any fraction of a peso rounds up once, before splitting installments.
        val interest = BigDecimal(principal).multiply(BigDecimal(rateBps)).divide(BigDecimal(10_000), 0, RoundingMode.CEILING).longValueExact()
        val total = Math.addExact(principal, interest)
        require(total >= count) { "El total debe permitir al menos un peso por cuota." }
        val base = total / count
        return Quote(total, interest, List(count) { if (it == count - 1) total - base * (count - 1) else base })
    }
    fun dates(first: LocalDate, count: Int, frequency: Frequency): List<LocalDate> = List(count) {
        when (frequency) {
            Frequency.WEEKLY -> first.plusWeeks(it.toLong())
            Frequency.BIWEEKLY -> first.plusDays(15L * it)
            Frequency.MONTHLY -> first.plusMonths(it.toLong()) // Always anchor to the original date.
        }
    }
}

data class Allocation(val payment: Payment, val principal: Long, val interest: Long)
data class MonthlySummary(val lent: Long, val scheduled: Long, val expectedInterest: Long,
    val collected: Long, val recovered: Long, val interestCollected: Long, val pending: Long, val overdue: Long)

data class Ledger(val customers: List<Customer> = emptyList(), val loans: List<Loan> = emptyList(),
    val installments: List<Installment> = emptyList(), val payments: List<Payment> = emptyList(),
    val plans: List<Plan> = (1..4).map { Plan(it, it * 1500) }) {
    val customerById by lazy { customers.associateBy { it.id } }
    val loanById by lazy { loans.associateBy { it.id } }
    val installmentById by lazy { installments.associateBy { it.id } }
    val paymentsByInstallment by lazy { payments.groupBy { it.installmentId } }
    val installmentsByLoan by lazy { installments.groupBy { it.loanId } }
    val loansByCustomer by lazy { loans.groupBy { it.customerId } }
    fun paid(i: Installment): Long = paymentsByInstallment[i.id].orEmpty().sumOf { it.amount }
    fun remaining(i: Installment): Long = i.originalAmount - paid(i)
    fun balance(l: Loan): Long = installmentsByLoan[l.id].orEmpty().sumOf { remaining(it) }
    fun debt(c: Customer): Long = loansByCustomer[c.id].orEmpty().sumOf { balance(it) }
    fun active(c: Customer): Int = loansByCustomer[c.id].orEmpty().count { balance(it) > 0 }
    fun customerInstallments(c: Customer): List<Installment> = loansByCustomer[c.id].orEmpty().flatMap { installmentsByLoan[it.id].orEmpty() }
    fun next(c: Customer): Installment? = customerInstallments(c).filter { remaining(it) > 0 }.minWithOrNull(compareBy({ it.dueDate }, { it.id }))
    fun overdue(i: Installment, today: LocalDate): Boolean = remaining(i) > 0 && LocalDate.parse(i.dueDate) < today
    fun overdue(c: Customer, today: LocalDate): Long = customerInstallments(c).filter { overdue(it, today) }.sumOf { remaining(it) }
    fun status(i: Installment, today: LocalDate): InstallmentStatus = when {
        remaining(i) == 0L -> InstallmentStatus.PAID
        overdue(i, today) -> InstallmentStatus.OVERDUE
        paid(i) > 0L -> InstallmentStatus.PARTIAL
        else -> InstallmentStatus.PENDING
    }
    fun completionDate(i: Installment): String? = if (remaining(i) == 0L) paymentsByInstallment[i.id].orEmpty().maxOfOrNull { it.paymentDate } else null
    val allocations: List<Allocation> by lazy {
        val cumulative = mutableMapOf<Long, Long>()
        payments.sortedWith(compareBy({ it.paymentDate }, { it.createdAt }, { it.id })).map { p ->
            val loan = loanById.getValue(installmentById.getValue(p.installmentId).loanId)
            val before = cumulative[loan.id] ?: 0L
            val after = Math.addExact(before, p.amount)
            val interest = Money.fraction(after, loan.expectedProfit, loan.totalAmount) - Money.fraction(before, loan.expectedProfit, loan.totalAmount)
            cumulative[loan.id] = after
            Allocation(p, p.amount - interest, interest)
        }
    }
    val pending: Long get() = installments.sumOf { remaining(it) }
    val interestPending: Long get() = loans.sumOf { it.expectedProfit } - allocations.sumOf { it.interest }
    val principalPending: Long get() = loans.sumOf { it.principal } - allocations.sumOf { it.principal }
    fun monthly(month: YearMonth, today: LocalDate): MonthlySummary {
        val selected = installments.filter { YearMonth.from(LocalDate.parse(it.dueDate)) == month }
        val collected = allocations.filter { YearMonth.from(LocalDate.parse(it.payment.paymentDate)) == month }
        val expectedById = mutableMapOf<Long, Long>()
        loans.forEach { l ->
            var sum = 0L
            installmentsByLoan[l.id].orEmpty().sortedBy { it.number }.forEach { i ->
                val next = sum + i.originalAmount
                expectedById[i.id] = Money.fraction(next, l.expectedProfit, l.totalAmount) - Money.fraction(sum, l.expectedProfit, l.totalAmount)
                sum = next
            }
        }
        return MonthlySummary(loans.filter { YearMonth.from(LocalDate.parse(it.loanDate)) == month }.sumOf { it.principal },
            selected.sumOf { it.originalAmount }, selected.sumOf { expectedById.getValue(it.id) },
            collected.sumOf { it.payment.amount }, collected.sumOf { it.principal }, collected.sumOf { it.interest },
            selected.sumOf { remaining(it) }, selected.filter { overdue(it, today) }.sumOf { remaining(it) })
    }
}

fun displayDate(iso: String): String = LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

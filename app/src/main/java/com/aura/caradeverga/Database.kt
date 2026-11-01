package com.aura.caradeverga

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@Dao
interface LedgerDao {
    @Query("SELECT (SELECT COUNT(*) FROM customers) + (SELECT COUNT(*) FROM loans) + (SELECT COUNT(*) FROM installments) + (SELECT COUNT(*) FROM payments) + (SELECT COUNT(*) FROM plans)")
    fun changes(): Flow<Long>
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE, id") suspend fun customers(): List<Customer>
    @Query("SELECT * FROM loans ORDER BY loanDate DESC, id DESC") suspend fun loans(): List<Loan>
    @Query("SELECT * FROM installments ORDER BY dueDate, loanId, number") suspend fun installments(): List<Installment>
    @Query("SELECT * FROM payments ORDER BY paymentDate, createdAt, id") suspend fun payments(): List<Payment>
    @Query("SELECT * FROM plans ORDER BY count") suspend fun plans(): List<Plan>
    @Insert suspend fun addCustomer(c: Customer): Long
    @Insert suspend fun addLoan(l: Loan): Long
    @Insert suspend fun addInstallments(items: List<Installment>): List<Long>
    @Insert suspend fun addPayment(p: Payment): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePlans(items: List<Plan>)
    @Update suspend fun editCustomer(c: Customer)
    @Query("DELETE FROM customers WHERE id = :id") suspend fun deleteCustomer(id: Long)
    @Query("DELETE FROM loans WHERE id = :id") suspend fun deleteLoan(id: Long)
    @Query("DELETE FROM payments WHERE id = :id") suspend fun deletePayment(id: Long)
    @Query("DELETE FROM payments") suspend fun clearPayments()
    @Query("DELETE FROM installments") suspend fun clearInstallments()
    @Query("DELETE FROM loans") suspend fun clearLoans()
    @Query("DELETE FROM customers") suspend fun clearCustomers()
    @Query("DELETE FROM plans") suspend fun clearPlans()
}

@Database(entities = [Customer::class, Loan::class, Installment::class, Payment::class, Plan::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
    companion object {
        fun open(context: Context, name: String = "caradeverga.db"): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name).build()
    }
}

class Repository(val db: AppDatabase) {
    private val dao = db.dao()
    val ledgers: Flow<Ledger> = dao.changes().map { snapshot() }
    suspend fun initialize() = db.withTransaction {
        if (dao.plans().isEmpty()) dao.savePlans((1..4).map { Plan(it, it * 1500) })
    }
    suspend fun snapshot(): Ledger = db.withTransaction {
        Ledger(dao.customers(), dao.loans(), dao.installments(), dao.payments(), dao.plans())
    }
    suspend fun saveCustomer(customer: Customer): Long = db.withTransaction {
        val c = customer.copy(name = customer.name.trim(), nickname = customer.nickname.trim(), phone = customer.phone.trim(), notes = customer.notes.trim())
        require(c.name.isNotBlank() && c.name.length <= 120) { "Ingresá un nombre de hasta 120 caracteres." }
        require(c.nickname.length <= 120 && c.phone.length <= 60 && c.notes.length <= 4000) { "El texto es demasiado largo." }
        if (c.id == 0L) dao.addCustomer(c) else {
            require(dao.customers().any { it.id == c.id }) { "El cliente ya no existe." }
            dao.editCustomer(c); c.id
        }
    }
    suspend fun archiveCustomer(id: Long, archived: Boolean) = db.withTransaction {
        val c = dao.customers().firstOrNull { it.id == id } ?: error("El cliente ya no existe.")
        dao.editCustomer(c.copy(archived = archived))
    }
    suspend fun deleteCustomer(id: Long) = db.withTransaction {
        require(dao.loans().none { it.customerId == id }) { "Este cliente tiene historial. Podés archivarlo para conservarlo." }
        dao.deleteCustomer(id)
    }
    suspend fun newLoan(customerId: Long, principal: Long, count: Int, loanDate: LocalDate,
        firstDue: LocalDate, frequency: Frequency, today: LocalDate = LocalDate.now()): Long =
        newLoan(customerId, LoanInput(principal, count, loanDate, firstDue, frequency), today)

    suspend fun newLoan(customerId: Long, input: LoanInput, today: LocalDate = LocalDate.now()): Long = db.withTransaction {
        val c = dao.customers().firstOrNull { it.id == customerId } ?: error("Seleccioná un cliente.")
        require(!c.archived) { "Restaurá al cliente antes de crear un préstamo." }
        val rate = input.agreedRateBps ?: dao.plans().firstOrNull { it.count == input.count }?.rateBps ?: error("Plan no disponible.")
        val quote = input.validate(rate, today)
        val id = dao.addLoan(Loan(customerId = customerId, principal = input.principal, interestRateBps = rate,
            installmentCount = input.count, totalAmount = quote.total, expectedProfit = quote.profit,
            loanDate = input.loanDate.toString(), firstDueDate = input.firstDue.toString(), frequency = input.frequency.name))
        val installmentIds = dao.addInstallments(LoanMath.dates(input.firstDue, input.count, input.frequency).mapIndexed { n, date ->
            Installment(loanId = id, number = n + 1, originalAmount = quote.amounts[n], dueDate = date.toString())
        })
        input.previousPayments.forEach { p ->
            dao.addPayment(Payment(installmentId = installmentIds[p.installmentNumber - 1], amount = p.amount, paymentDate = p.date.toString()))
        }
        id
    }
    suspend fun registerPayment(installmentId: Long, amount: Long, date: LocalDate,
        today: LocalDate = LocalDate.now()): Long = db.withTransaction {
        val s = snapshot()
        val i = s.installmentById[installmentId] ?: error("La cuota ya no existe.")
        val loan = s.loanById.getValue(i.loanId)
        require(amount > 0 && amount <= s.remaining(i)) { "El pago debe ser mayor que cero y no superar ${Money.format(s.remaining(i))}." }
        require(!date.isAfter(today) && !date.isBefore(LocalDate.parse(loan.loanDate))) { "La fecha debe estar entre el préstamo y hoy." }
        dao.addPayment(Payment(installmentId = i.id, amount = amount, paymentDate = date.toString()))
    }
    suspend fun deletePayment(id: Long) = dao.deletePayment(id)
    suspend fun deleteLoan(id: Long) = dao.deleteLoan(id)
    suspend fun updatePlans(plans: List<Plan>) = db.withTransaction {
        require(plans.map { it.count }.sorted() == listOf(1, 2, 3, 4) && plans.all { it.rateBps in 0..100_000 }) { "Planes inválidos." }
        dao.savePlans(plans)
    }
    suspend fun restore(ledger: Ledger, today: LocalDate = LocalDate.now()) {
        Backup.validate(ledger, today)
        db.withTransaction {
            dao.clearPayments(); dao.clearInstallments(); dao.clearLoans(); dao.clearCustomers(); dao.clearPlans()
            ledger.customers.forEach { dao.addCustomer(it) }
            ledger.loans.forEach { dao.addLoan(it) }
            dao.addInstallments(ledger.installments)
            ledger.payments.forEach { dao.addPayment(it) }
            dao.savePlans(ledger.plans)
        }
    }
    suspend fun loadDemo(today: LocalDate = LocalDate.now()) = db.withTransaction {
        require(dao.customers().isEmpty() && dao.loans().isEmpty()) { "Los ejemplos solo se pueden cargar en una base vacía." }
        initialize()
        val isaac = saveCustomer(Customer(name = "Isaac", notes = "Cliente de ejemplo: dos préstamos independientes."))
        val first = newLoan(isaac, 200_000, 2, today.minusMonths(1), today.minusDays(20), Frequency.MONTHLY, today)
        val second = newLoan(isaac, 100_000, 4, today.minusDays(10), today, Frequency.MONTHLY, today)
        val a = snapshot().installmentsByLoan.getValue(first).sortedBy { it.number }
        registerPayment(a[0].id, a[0].originalAmount, today.minusDays(20), today)
        val b = snapshot().installmentsByLoan.getValue(second).sortedBy { it.number }
        registerPayment(b[0].id, 15_000, today, today)
        val pedro = saveCustomer(Customer(name = "Pedro", notes = "Ejemplo de una cuota vencida."))
        newLoan(pedro, 50_000, 1, today.minusDays(15), today.minusDays(3), Frequency.MONTHLY, today)
    }
}

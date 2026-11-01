package com.aura.caradeverga

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object Backup {
    const val MAX_BYTES = 20 * 1024 * 1024
    private const val MAX_RECORDS = 150_000
    private const val ALLOCATION = "PROPORTIONAL_CUMULATIVE_V1"
    private fun obj(vararg pairs: Pair<String, Any>): JSONObject = JSONObject().apply { pairs.forEach { put(it.first, it.second) } }
    private fun <T> arr(items: List<T>, mapper: (T) -> JSONObject) = JSONArray().apply { items.forEach { put(mapper(it)) } }
    fun encode(s: Ledger): String = obj("app" to "CaraDeVerga", "schemaVersion" to 1,
        "interestAllocation" to ALLOCATION, "exportedAt" to System.currentTimeMillis(),
        "customers" to arr(s.customers) { obj("id" to it.id, "name" to it.name, "nickname" to it.nickname, "phone" to it.phone, "notes" to it.notes, "createdAt" to it.createdAt, "archived" to it.archived) },
        "loans" to arr(s.loans) { obj("id" to it.id, "customerId" to it.customerId, "principal" to it.principal, "interestRateBps" to it.interestRateBps, "installmentCount" to it.installmentCount, "totalAmount" to it.totalAmount, "expectedProfit" to it.expectedProfit, "loanDate" to it.loanDate, "firstDueDate" to it.firstDueDate, "frequency" to it.frequency, "createdAt" to it.createdAt) },
        "installments" to arr(s.installments) { obj("id" to it.id, "loanId" to it.loanId, "number" to it.number, "originalAmount" to it.originalAmount, "dueDate" to it.dueDate) },
        "payments" to arr(s.payments) { obj("id" to it.id, "installmentId" to it.installmentId, "amount" to it.amount, "paymentDate" to it.paymentDate, "createdAt" to it.createdAt) },
        "plans" to arr(s.plans) { obj("count" to it.count, "rateBps" to it.rateBps) }).toString(2)

    private fun JSONObject.num(key: String): Long {
        val value = get(key)
        require(value is Number && value.toString().matches(Regex("-?[0-9]+"))) { "Valor entero inválido: $key." }
        return value.toString().toLongOrNull() ?: error("Número fuera de rango: $key.")
    }
    private fun JSONObject.int(key: String): Int = Math.toIntExact(num(key))
    private fun JSONObject.str(key: String): String = get(key).also { require(it is String) { "Texto inválido: $key." } } as String
    private fun JSONObject.bool(key: String): Boolean = get(key).also { require(it is Boolean) { "Valor inválido: $key." } } as Boolean
    private fun <T> JSONObject.list(key: String, mapper: (JSONObject) -> T): List<T> {
        val a = getJSONArray(key)
        require(a.length() <= MAX_RECORDS) { "La copia contiene demasiados registros." }
        return (0 until a.length()).map { mapper(a.getJSONObject(it)) }
    }
    fun decode(text: String, today: LocalDate = LocalDate.now()): Ledger {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "La copia supera los 20 MB." }
        try {
            val j = JSONObject(text)
            require(j.str("app") == "CaraDeVerga" && j.int("schemaVersion") == 1 && j.str("interestAllocation") == ALLOCATION) { "Esta copia no es compatible con CaraDeVerga 1.0." }
            val s = Ledger(
                j.list("customers") { Customer(it.num("id"), it.str("name"), it.str("nickname"), it.str("phone"), it.str("notes"), it.num("createdAt"), it.bool("archived")) },
                j.list("loans") { Loan(it.num("id"), it.num("customerId"), it.num("principal"), it.int("interestRateBps"), it.int("installmentCount"), it.num("totalAmount"), it.num("expectedProfit"), it.str("loanDate"), it.str("firstDueDate"), it.str("frequency"), it.num("createdAt")) },
                j.list("installments") { Installment(it.num("id"), it.num("loanId"), it.int("number"), it.num("originalAmount"), it.str("dueDate")) },
                j.list("payments") { Payment(it.num("id"), it.num("installmentId"), it.num("amount"), it.str("paymentDate"), it.num("createdAt")) },
                j.list("plans") { Plan(it.int("count"), it.int("rateBps")) })
            validate(s, today)
            return s
        } catch (e: IllegalArgumentException) { throw e }
        catch (e: Exception) { throw IllegalArgumentException("Copia inválida o incompleta. No se modificaron tus datos.", e) }
    }
    private fun ids(values: List<Long>) {
        require(values.size <= MAX_RECORDS && values.all { it in 1 until Long.MAX_VALUE } && values.distinct().size == values.size) { "La copia tiene identificadores inválidos o repetidos." }
    }
    private fun date(value: String): LocalDate = LocalDate.parse(value).also {
        require(it.year in 1900..2201 && it.toString() == value) { "La copia tiene fechas inválidas." }
    }
    fun validate(s: Ledger, today: LocalDate = LocalDate.now()) {
        ids(s.customers.map { it.id }); ids(s.loans.map { it.id }); ids(s.installments.map { it.id }); ids(s.payments.map { it.id })
        require(s.plans.map { it.count }.sorted() == listOf(1, 2, 3, 4) && s.plans.all { it.rateBps in 0..100_000 }) { "La copia tiene planes inválidos." }
        s.customers.forEach {
            require(it.name.isNotBlank() && it.name.length <= 120 && it.nickname.length <= 120 && it.phone.length <= 60 && it.notes.length <= 4000 && it.createdAt >= 0) { "La copia tiene clientes inválidos." }
        }
        s.loans.forEach { l ->
            require(l.customerId in s.customerById && l.createdAt >= 0) { "Préstamo sin cliente válido." }
            val q = LoanMath.quote(l.principal, l.interestRateBps, l.installmentCount)
            require(q.total == l.totalAmount && q.profit == l.expectedProfit) { "Los importes de un préstamo no coinciden." }
            val loanDate = date(l.loanDate); val firstDue = date(l.firstDueDate)
            require(loanDate <= today && firstDue >= loanDate) { "Las fechas de un préstamo no coinciden." }
            val dates = LoanMath.dates(firstDue, l.installmentCount, Frequency.valueOf(l.frequency))
            val items = s.installmentsByLoan[l.id].orEmpty().sortedBy { it.number }
            require(items.size == l.installmentCount) { "Faltan cuotas en un préstamo." }
            items.forEachIndexed { n, i ->
                require(i.number == n + 1 && i.originalAmount == q.amounts[n] && date(i.dueDate) == dates[n]) { "Las cuotas de un préstamo no coinciden." }
            }
        }
        s.installments.forEach { i ->
            require(i.loanId in s.loanById && i.originalAmount > 0) { "Cuota sin préstamo válido." }
            val sum = s.paymentsByInstallment[i.id].orEmpty().fold(0L) { a, p -> Math.addExact(a, p.amount) }
            require(sum in 0..i.originalAmount) { "Una cuota tiene pagos superiores a su importe." }
        }
        s.payments.forEach { p ->
            val i = s.installmentById[p.installmentId] ?: error("Pago sin cuota válida.")
            val l = s.loanById.getValue(i.loanId)
            require(p.amount > 0 && p.createdAt >= 0 && date(p.paymentDate) in date(l.loanDate)..today) { "La copia contiene un pago inválido." }
        }
        // Fail before restore if aggregate monetary values cannot be represented.
        s.loans.fold(0L) { a, l -> Math.addExact(a, l.totalAmount) }
    }
}

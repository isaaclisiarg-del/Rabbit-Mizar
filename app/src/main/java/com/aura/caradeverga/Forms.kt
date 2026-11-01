package com.aura.caradeverga

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

@Composable
fun DateField(label: String, value: LocalDate, enabled: Boolean = true, onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(enabled = enabled, onClick = {
        DatePickerDialog(context, { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d)) }, value.year, value.monthValue - 1, value.dayOfMonth).show()
    }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(displayDate(value.toString()), style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Default.DateRange, null)
        }
    }
}

@Composable
fun FormError(error: String?) { if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

@Composable
fun CustomerForm(customer: Customer?, busy: Boolean, onDismiss: () -> Unit, onSave: (Customer) -> Unit) {
    var name by rememberSaveable(customer?.id) { mutableStateOf(customer?.name ?: "") }
    var nickname by rememberSaveable(customer?.id) { mutableStateOf(customer?.nickname ?: "") }
    var phone by rememberSaveable(customer?.id) { mutableStateOf(customer?.phone ?: "") }
    var notes by rememberSaveable(customer?.id) { mutableStateOf(customer?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false), title = { Text(if (customer == null) "Nuevo cliente" else "Editar cliente") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { if (it.length <= 120) name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), enabled = !busy)
            OutlinedTextField(nickname, { if (it.length <= 120) nickname = it }, Modifier.fillMaxWidth(), label = { Text("Apellido o apodo · opcional") }, singleLine = true, enabled = !busy)
            OutlinedTextField(phone, { if (it.length <= 60) phone = it }, Modifier.fillMaxWidth(), label = { Text("Teléfono · opcional") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), enabled = !busy)
            OutlinedTextField(notes, { if (it.length <= 4000) notes = it }, Modifier.fillMaxWidth(), label = { Text("Notas") }, minLines = 2, maxLines = 4, enabled = !busy)
            FormError(error)
        } }, confirmButton = { TextButton(enabled = !busy, onClick = {
            if (name.isBlank()) error = "Ingresá el nombre del cliente."
            else onSave((customer ?: Customer(name = name)).copy(name = name, nickname = nickname, phone = phone, notes = notes))
        }) { Text("Guardar") } }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun NewLoanScreen(customer: Customer, plans: List<Plan>, today: LocalDate, busy: Boolean,
    onSave: (LoanInput) -> Unit) {
    var principal by rememberSaveable(customer.id) { mutableStateOf("") }
    var count by rememberSaveable(customer.id) { mutableIntStateOf(2) }
    var loanDate by rememberSaveable(customer.id) { mutableStateOf(today.toString()) }
    var firstDue by rememberSaveable(customer.id) { mutableStateOf(today.plusMonths(1).toString()) }
    var frequency by rememberSaveable(customer.id) { mutableStateOf(Frequency.MONTHLY.name) }
    var started by rememberSaveable(customer.id) { mutableStateOf(false) }
    var agreedRate by rememberSaveable(customer.id) { mutableStateOf<String?>(null) }
    var withoutInterest by rememberSaveable(customer.id) { mutableStateOf(false) }
    var previous by rememberSaveable(customer.id, stateSaver = PreviousPaymentsSaver) { mutableStateOf(emptyList<PreviousPayment>()) }
    var paymentNumber by rememberSaveable(customer.id) { mutableStateOf<Int?>(null) }
    var confirmNew by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val planRate = plans.firstOrNull { it.count == count }?.rateBps ?: count * 1500
    val rateText = agreedRate ?: Money.rate(planRate).removeSuffix(" %")
    val rate = when {
        withoutInterest -> 0
        started -> runCatching { Money.parseRate(rateText) }.getOrNull()
        else -> planRate
    }
    val quote = runCatching { LoanMath.quote(Money.parse(principal), requireNotNull(rate), count) }.getOrNull()
    val date = LocalDate.parse(loanDate)
    val due = LocalDate.parse(firstDue)
    val dates = LoanMath.dates(due, count, Frequency.valueOf(frequency))
    val received = if (started) previous.sumOf { it.amount } else 0L
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(customer.displayName, style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !started, enabled = !busy, onClick = {
                if (previous.isNotEmpty()) confirmNew = true else { started = false; error = null }
            }, label = { Text("Nuevo") }, modifier = Modifier.weight(1f))
            FilterChip(selected = started, enabled = !busy, onClick = { started = true; error = null },
                label = { Text("Ya iniciado") }, modifier = Modifier.weight(1f))
        }
        if (started) Text("Cargá el préstamo original y los pagos que ya recibiste. Solo quedará pendiente lo que falta cobrar.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(principal, { if (it.length <= 20) principal = it }, Modifier.fillMaxWidth(), label = { Text("Capital prestado") }, prefix = { Text("$ ") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Pesos enteros, sin centavos") }, enabled = !busy)
        Text(if (started) "Cuotas originales, incluidas las pagadas" else "Cantidad de cuotas", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..4).forEach { n -> FilterChip(selected = count == n, enabled = !busy, onClick = {
                if (previous.any { it.installmentNumber > n }) error = "Quitá primero los pagos de las cuotas que querés sacar."
                else { count = n; error = null }
            }, label = { Text(n.toString()) }, modifier = Modifier.weight(1f)) }
        }
        Section("Intereses de este préstamo")
        FilterChip(selected = withoutInterest, enabled = !busy, onClick = { withoutInterest = !withoutInterest; error = null },
            label = { Text("Sin intereses (0%)") })
        if (withoutInterest) Text("Solo se devolverá el capital. Los planes generales no se modifican.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (started) OutlinedTextField(rateText, { if (it.length <= 8) agreedRate = it }, Modifier.fillMaxWidth(),
            label = { Text("Interés total pactado") }, suffix = { Text("%") }, singleLine = true,
            supportingText = { Text("Usá el porcentaje original de este préstamo.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy)
        else Text("Interés total del plan: ${Money.rate(planRate)}", style = MaterialTheme.typography.bodyMedium)
        DateField(if (started) "Fecha original del préstamo" else "Fecha del préstamo", date, !busy) { loanDate = it.toString() }
        DateField(if (started) "Vencimiento de la primera cuota original" else "Fecha de primera cuota", due, !busy) { firstDue = it.toString() }
        Box {
            OutlinedButton(enabled = !busy, onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Frecuencia: ${Frequency.valueOf(frequency).label}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Frequency.entries.forEach { f -> DropdownMenuItem(text = { Text(f.label) }, onClick = { frequency = f.name; menu = false }) }
            }
        }
        if (quote != null) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp)) {
                AmountRow("Total a cobrar", quote.total, true)
                AmountRow("Ganancia prevista", quote.profit)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (started) {
                    AmountRow("Ya cobrado", received)
                    if (received <= quote.total) AmountRow("Saldo pendiente", quote.total - received, true)
                } else quote.amounts.forEachIndexed { n, amount -> AmountRow("Cuota ${n + 1} · ${displayDate(dates[n].toString())}", amount) }
                if (Money.parse(principal) * requireNotNull(rate) % 10_000 != 0L) Text("El interés se redondea al peso superior.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (started && quote != null) {
            Section("Pagos anteriores", "Agregá cada cobro con su fecha real. Si aún no te pagaron, dejá las cuotas sin pagos.")
            quote.amounts.forEachIndexed { index, amount ->
                val number = index + 1
                val paid = previous.filter { it.installmentNumber == number }.sumOf { it.amount }
                val remaining = amount - paid
                val status = when {
                    remaining == 0L -> "Pagada"
                    remaining < 0L -> "Revisar pagos"
                    dates[index] < today -> "Vencida"
                    paid > 0 -> "Parcial"
                    else -> "Pendiente"
                }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Cuota $number · ${displayDate(dates[index].toString())}", style = MaterialTheme.typography.titleSmall)
                        Badge(status, remaining < 0 || (remaining > 0 && dates[index] < today))
                        AmountRow("Monto original", amount)
                        if (paid > 0) AmountRow("Pagado", paid)
                        if (remaining >= 0) AmountRow("Pendiente", remaining, true)
                        else FormError("Los pagos superan el monto de esta cuota.")
                        previous.forEachIndexed { paymentIndex, p -> if (p.installmentNumber == number) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Money.format(p.amount), style = MaterialTheme.typography.bodyMedium)
                                    Text(displayDate(p.date.toString()), style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(enabled = !busy, onClick = {
                                    previous = previous.filterIndexed { n, _ -> n != paymentIndex }; error = null
                                }) { Icon(Icons.Default.Delete, "Quitar pago sin guardar de cuota $number, ${displayDate(p.date.toString())}, ${Money.format(p.amount)}") }
                            }
                        } }
                        if (remaining > 0) TextButton(enabled = !busy && date <= today && due >= date, onClick = { paymentNumber = number }, modifier = Modifier.fillMaxWidth()) {
                            Text("Agregar pago a cuota $number")
                        }
                    }
                }
            }
            Text("Los cobros se guardarán en su fecha original y se incluirán en el Resumen de ese mes.", style = MaterialTheme.typography.bodySmall)
        }
        FormError(error)
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = {
            runCatching {
                val originalRate = when {
                    withoutInterest -> 0
                    started -> Money.parseRate(rateText)
                    else -> null
                }
                val input = LoanInput(Money.parse(principal), count, date, due, Frequency.valueOf(frequency),
                    originalRate, if (started) previous else emptyList())
                input.validate(originalRate ?: planRate, today)
                onSave(input)
            }.onFailure { error = it.message }
        }) { Text(if (busy) "Guardando…" else if (started) "Guardar préstamo iniciado" else "Crear préstamo") }
        Spacer(Modifier.height(16.dp))
    }
    paymentNumber?.let { number -> quote?.let { q ->
        val remaining = q.amounts[number - 1] - previous.filter { it.installmentNumber == number }.sumOf { it.amount }
        PreviousPaymentDialog(number, remaining, dates[number - 1].coerceIn(date, today), date, today, busy,
            onDismiss = { paymentNumber = null }, onAdd = { amount, paymentDate ->
                previous = previous + PreviousPayment(number, amount, paymentDate); paymentNumber = null; error = null
            })
    } }
    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false), title = { Text("¿Cambiar a préstamo nuevo?") },
        text = { Text("Se quitarán los pagos anteriores de este formulario. Todavía no se guardaron.") },
        confirmButton = { TextButton(onClick = { previous = emptyList(); started = false; confirmNew = false; error = null }) { Text("Quitar pagos y cambiar") } },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Cancelar") } })
}

private val PreviousPaymentsSaver = listSaver<List<PreviousPayment>, String>(
    save = { items -> items.map { "${it.installmentNumber}|${it.amount}|${it.date}" } },
    restore = { items -> items.map { value ->
        val fields = value.split('|')
        PreviousPayment(fields[0].toInt(), fields[1].toLong(), LocalDate.parse(fields[2]))
    } }
)

@Composable
private fun PreviousPaymentDialog(number: Int, remaining: Long, initialDate: LocalDate, loanDate: LocalDate,
    today: LocalDate, busy: Boolean, onDismiss: () -> Unit, onAdd: (Long, LocalDate) -> Unit) {
    var amount by rememberSaveable(number) { mutableStateOf(remaining.toString()) }
    var date by rememberSaveable(number) { mutableStateOf(initialDate.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false), title = { Text("Pago anterior · Cuota $number") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Para una cuota pagada, ingresá el saldo completo. Para un pago parcial, solo lo recibido.")
            AmountRow("Saldo de la cuota", remaining)
            OutlinedTextField(amount, { if (it.length <= 20) amount = it }, Modifier.fillMaxWidth(),
                label = { Text("Monto recibido") }, prefix = { Text("$ ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
            DateField("Fecha del pago", LocalDate.parse(date), !busy) { date = it.toString() }
            FormError(error)
        } }, confirmButton = { TextButton(enabled = !busy, onClick = {
            runCatching {
                val received = Money.parse(amount)
                require(received <= remaining) { "El pago no puede superar ${Money.format(remaining)}." }
                val paymentDate = LocalDate.parse(date)
                require(paymentDate in loanDate..today) { "La fecha debe estar entre el préstamo y hoy." }
                onAdd(received, paymentDate)
            }.onFailure { error = it.message }
        }) { Text("Agregar pago anterior") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun PaymentDialog(s: Ledger, i: Installment, today: LocalDate, busy: Boolean, onDismiss: () -> Unit,
    onPayment: (Long, LocalDate) -> Unit, onDelete: (Payment) -> Unit) {
    val l = s.loanById.getValue(i.loanId)
    val remaining = s.remaining(i)
    var amount by rememberSaveable(i.id, remaining) { mutableStateOf(if (remaining > 0) remaining.toString() else "") }
    var date by rememberSaveable(i.id) { mutableStateOf(today.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Cuota ${i.number} · Préstamo #${l.id}") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.customerById.getValue(l.customerId).displayName, style = MaterialTheme.typography.titleMedium)
            Text("Vencimiento: ${displayDate(i.dueDate)}")
            Badge(s.status(i, today).label, s.overdue(i, today))
            AmountRow("Monto original", i.originalAmount)
            AmountRow("Pagado", s.paid(i))
            AmountRow("Pendiente", remaining, true)
            if (remaining > 0) {
                OutlinedTextField(amount, { if (it.length <= 20) amount = it }, Modifier.fillMaxWidth(), label = { Text("Monto recibido") }, prefix = { Text("$ ") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
                DateField("Fecha del pago", LocalDate.parse(date), !busy) { date = it.toString() }
            }
            FormError(error)
            Section("Movimientos de pago")
            val payments = s.paymentsByInstallment[i.id].orEmpty().sortedWith(compareByDescending<Payment> { it.paymentDate }.thenByDescending { it.id })
            if (payments.isEmpty()) Text("Todavía no hay pagos.")
            payments.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Money.format(p.amount), style = MaterialTheme.typography.titleSmall)
                        Text(displayDate(p.paymentDate), style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(enabled = !busy, onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, "Eliminar pago de ${Money.format(p.amount)}") }
                }
            }
        } }, confirmButton = {
            if (remaining > 0) TextButton(enabled = !busy, onClick = {
                runCatching {
                    val received = Money.parse(amount)
                    require(received <= remaining) { "El pago no puede superar ${Money.format(remaining)}." }
                    val paymentDate = LocalDate.parse(date)
                    require(paymentDate <= today && paymentDate >= LocalDate.parse(l.loanDate)) { "La fecha debe estar entre el préstamo y hoy." }
                    onPayment(received, paymentDate)
                }.onFailure { error = it.message }
            }) { Text("Registrar pago") }
            else TextButton(onClick = onDismiss) { Text("Cerrar") }
        }, dismissButton = { if (remaining > 0) TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun SettingsScreen(s: Ledger, busy: Boolean, onPlans: (List<Plan>) -> Unit, onExport: () -> Unit, onImport: () -> Unit, onDemo: () -> Unit,
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM, onAppearanceChange: (AppearanceMode) -> Unit = {}) {
    var rates by remember(s.plans) { mutableStateOf(s.plans.sortedBy { it.count }.map { java.math.BigDecimal(it.rateBps).movePointLeft(2).stripTrailingZeros().toPlainString().replace('.', ',') }) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("Apariencia · Rabbit: Mizar", "Elegí cómo querés ver la app. No afecta los préstamos ni los datos.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppearanceMode.entries.forEach { mode ->
                FilterChip(selected = appearanceMode == mode, enabled = !busy, onClick = { onAppearanceChange(mode) },
                    label = { Text(mode.label) }, modifier = Modifier.weight(1f))
            }
        }
        Section("Planes de cuotas", "Los cambios se aplican solamente a préstamos nuevos.")
        rates.forEachIndexed { n, rate ->
            OutlinedTextField(rate, { value -> if (value.length <= 8) rates = rates.toMutableList().also { it[n] = value } }, Modifier.fillMaxWidth(), label = { Text("${n + 1} ${if (n == 0) "cuota" else "cuotas"}") }, suffix = { Text("%") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy)
        }
        FormError(error)
        Button(enabled = !busy && rates.size == 4, onClick = {
            runCatching { rates.mapIndexed { n, value -> Plan(n + 1, Money.parseRate(value)) } }
                .onSuccess { error = null; onPlans(it) }.onFailure { error = it.message }
        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar planes") }
        Section("Copia de seguridad", "Conserva clientes, préstamos, cuotas, pagos y planes.")
        OutlinedButton(enabled = !busy, onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("Exportar copia de seguridad") }
        OutlinedButton(enabled = !busy, onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Importar copia de seguridad") }
        Text("La copia es un archivo JSON con tus datos. Guardala en un lugar privado. Al desinstalar la app se borra la base local.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.customers.isEmpty() && s.loans.isEmpty()) {
            Section("Probar la app")
            OutlinedButton(enabled = !busy, onClick = onDemo, modifier = Modifier.fillMaxWidth()) { Text("Cargar datos de ejemplo") }
        }
        Section("Rabbit: Mizar · 1.2.0")
        Text("Sin cuentas ni conexión a Internet. Los datos se guardan en este teléfono.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
    }
}

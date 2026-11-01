package com.aura.caradeverga

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Section(title: String, detail: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Metric(label: String, amount: Long, modifier: Modifier = Modifier, danger: Boolean = false) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(Money.format(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AmountRow(label: String, amount: Long, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(Money.format(amount), style = MaterialTheme.typography.titleMedium, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun Badge(label: String, danger: Boolean = false) {
    Surface(color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun InstallmentCard(s: Ledger, i: Installment, today: LocalDate, showCustomer: Boolean, onClick: () -> Unit) {
    val l = s.loanById.getValue(i.loanId)
    val status = s.status(i, today)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (showCustomer) s.customerById.getValue(l.customerId).displayName else "Cuota ${i.number} de ${l.installmentCount}",
                    Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.titleMedium)
                Badge(status.label, status == InstallmentStatus.OVERDUE)
            }
            Text(Money.format(if (status == InstallmentStatus.PAID) i.originalAmount else s.remaining(i)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("${if (showCustomer) "Préstamo #${l.id} · Cuota ${i.number} · " else "Vence "}${displayDate(i.dueDate)}", style = MaterialTheme.typography.bodySmall)
            if (s.overdue(i, today)) Text("${ChronoUnit.DAYS.between(LocalDate.parse(i.dueDate), today)} días de atraso", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            if (s.paid(i) > 0 && s.remaining(i) > 0) Text("Pago parcial: ${Money.format(s.paid(i))} de ${Money.format(i.originalAmount)}", style = MaterialTheme.typography.bodySmall)
            s.completionDate(i)?.let { Text("Pagada el ${displayDate(it)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun HomeScreen(s: Ledger, today: LocalDate, onInstallment: (Installment) -> Unit) {
    val pending = s.installments.filter { s.remaining(it) > 0 }.sortedWith(compareBy({ it.dueDate }, { it.id }))
    val todayList = pending.filter { it.dueDate == today.toString() }
    val overdue = pending.filter { s.overdue(it, today) }
    val tomorrow = pending.filter { it.dueDate == today.plusDays(1).toString() }
    val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val week = pending.filter { LocalDate.parse(it.dueDate) > today && LocalDate.parse(it.dueDate) <= sunday }
    var expanded by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"))).replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Capital actualmente prestado", s.principalPending, Modifier.weight(1f))
            Metric("Pendiente de cobro", s.pending, Modifier.weight(1f))
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Intereses pendientes", s.interestPending, Modifier.weight(1f))
            Metric("Monto vencido", overdue.sumOf { s.remaining(it) }, Modifier.weight(1f), overdue.isNotEmpty())
        } }
        item { Section("Hoy", "${todayList.size} cuotas · ${Money.format(todayList.sumOf { s.remaining(it) })}") }
        if (todayList.isEmpty()) item { Text("No hay cuotas pendientes que venzan hoy.", style = MaterialTheme.typography.bodyMedium) }
        items(todayList, key = { "today${it.id}" }) { i -> InstallmentCard(s, i, today, true) { onInstallment(i) } }
        item { Section("Vencidos", if (overdue.isEmpty()) null else "${overdue.size} cuotas pendientes") }
        if (overdue.isEmpty()) item { Text("Sin cuotas atrasadas.", style = MaterialTheme.typography.bodyMedium) }
        items(overdue, key = { "overdue${it.id}" }) { i -> InstallmentCard(s, i, today, true) { onInstallment(i) } }
        item { Section("Próximos") }
        item { UpcomingRow("Mañana", tomorrow, s, expanded == "tomorrow") { expanded = if (expanded == "tomorrow") "" else "tomorrow" } }
        if (expanded == "tomorrow") items(tomorrow, key = { "tomorrow${it.id}" }) { i -> InstallmentCard(s, i, today, true) { onInstallment(i) } }
        item { UpcomingRow("Esta semana · hasta el domingo", week, s, expanded == "week") { expanded = if (expanded == "week") "" else "week" } }
        if (expanded == "week") items(week, key = { "week${it.id}" }) { i -> InstallmentCard(s, i, today, true) { onInstallment(i) } }
        if (s.customers.isEmpty()) item { EmptyState("Empezá con un préstamo", "Tocá Nuevo préstamo para crear o seleccionar un cliente. Los ejemplos están disponibles en Ajustes.") }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun UpcomingRow(label: String, items: List<Installment>, s: Ledger, expanded: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text("${items.size} cuotas · ${Money.format(items.sumOf { s.remaining(it) })}", style = MaterialTheme.typography.bodyMedium)
            }
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "Ocultar cuotas" else "Ver cuotas")
        }
    }
}

private fun searchKey(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
private fun matches(c: Customer, query: String): Boolean = searchKey("${c.name} ${c.nickname} ${c.phone}").contains(searchKey(query.trim()))

@Composable
fun CustomersScreen(s: Ledger, today: LocalDate, onCustomer: (Customer) -> Unit, onCreate: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    val list = s.customers.filter { it.archived == archived && matches(it, query) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { OutlinedTextField(query, onValueChange = { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Buscar nombre, apodo o teléfono") }, leadingIcon = { Icon(Icons.Default.Search, null) }) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilterChip(archived, onClick = { archived = !archived }, label = { Text("Archivados") })
            TextButton(onClick = onCreate) { Icon(Icons.Default.Add, null); Text("Crear cliente") }
        } }
        if (list.isEmpty()) item { EmptyState("Sin clientes", if (query.isBlank()) "Creá un cliente para empezar." else "No hay coincidencias con esa búsqueda.") }
        items(list, key = { it.id }) { c -> Card(onClick = { onCustomer(c) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(c.displayName, style = MaterialTheme.typography.titleMedium)
                val amounts = s.activeLoanAmounts(c)
                Text("Capital prestado · préstamos activos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(amounts.originalCapital), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("+ Intereses pactados: ${Money.format(amounts.agreedInterest)}", style = MaterialTheme.typography.bodySmall)
                Text("Total pactado: ${Money.format(amounts.agreedTotal)}", style = MaterialTheme.typography.bodySmall)
                Text("Pendiente actual: ${Money.format(amounts.pending)}", style = MaterialTheme.typography.bodySmall)
                Text("${s.active(c)} préstamos activos", style = MaterialTheme.typography.bodySmall)
                s.next(c)?.let { Text("Próxima cuota: ${displayDate(it.dueDate)} · ${Money.format(s.remaining(it))}", style = MaterialTheme.typography.bodySmall) }
                if (s.overdue(c, today) > 0L) Badge("Vencido: ${Money.format(s.overdue(c, today))}", true)
            }
        } }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
fun CustomerPicker(s: Ledger, onSelect: (Customer) -> Unit, onCreate: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val list = s.customers.filter { !it.archived && matches(it, query) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Crear cliente y continuar") } }
        item { OutlinedTextField(query, onValueChange = { query = it }, Modifier.fillMaxWidth(), label = { Text("Buscar cliente existente") }, singleLine = true) }
        items(list, key = { it.id }) { c -> Card(onClick = { onSelect(c) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(c.displayName, style = MaterialTheme.typography.titleMedium)
                Text("Deuda actual: ${Money.format(s.debt(c))}", style = MaterialTheme.typography.bodySmall)
            }
        } }
        if (list.isEmpty()) item { EmptyState("Sin coincidencias", "Podés crear un cliente nuevo para este préstamo.") }
    }
}

@Composable
fun CustomerScreen(s: Ledger, c: Customer, today: LocalDate, busy: Boolean, onLoan: (Loan) -> Unit,
    onEdit: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit) {
    val loans = s.loansByCustomer[c.id].orEmpty()
    val active = loans.filter { s.balance(it) > 0L }
    val paid = loans.filter { s.balance(it) == 0L }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(c.displayName, style = MaterialTheme.typography.headlineMedium)
            if (c.archived) Badge("Archivado")
        }
        item { Metric("Capital prestado · préstamos activos", s.activeLoanAmounts(c).originalCapital, Modifier.fillMaxWidth()) }
        item { Card { Column(Modifier.padding(14.dp)) {
            val amounts = s.activeLoanAmounts(c)
            AmountRow("+ Intereses pactados", amounts.agreedInterest)
            AmountRow("Total pactado", amounts.agreedTotal)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            AmountRow("Deuda pendiente actual", amounts.pending, true)
        } } }
        item { Text("${active.size} préstamos activos", style = MaterialTheme.typography.titleSmall) }
        if (s.overdue(c, today) > 0) item { Badge("Monto vencido: ${Money.format(s.overdue(c, today))}", true) }
        s.next(c)?.let { i -> item { Text("Próxima cuota: ${displayDate(i.dueDate)} · ${Money.format(s.remaining(i))}", style = MaterialTheme.typography.bodyMedium) } }
        if (c.phone.isNotBlank()) item { Text(c.phone, style = MaterialTheme.typography.bodyMedium) }
        if (c.notes.isNotBlank()) item { Text(c.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = onEdit) { Text("Editar") }
            TextButton(enabled = !busy, onClick = onArchive) { Text(if (c.archived) "Restaurar" else "Archivar") }
        } }
        item { Section("Préstamos activos") }
        if (active.isEmpty()) item { Text("No tiene préstamos activos.") }
        items(active, key = { it.id }) { LoanCard(s, it, today) { onLoan(it) } }
        item { Section("Historial", "Préstamos terminados; conservan cuotas y pagos.") }
        if (paid.isEmpty()) item { Text("Todavía no hay préstamos terminados.") }
        items(paid, key = { it.id }) { LoanCard(s, it, today) { onLoan(it) } }
        item { Text("Cliente desde ${displayDate(java.time.Instant.ofEpochMilli(c.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())}", style = MaterialTheme.typography.bodySmall) }
        if (loans.isEmpty()) item { TextButton(enabled = !busy, onClick = onDelete) { Text("Eliminar cliente", color = MaterialTheme.colorScheme.error) } }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
fun LoanCard(s: Ledger, l: Loan, today: LocalDate, onClick: () -> Unit) {
    val paid = s.balance(l) == 0L
    val overdue = s.installmentsByLoan[l.id].orEmpty().any { s.overdue(it, today) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Préstamo #${l.id}", style = MaterialTheme.typography.titleMedium)
                Badge(if (paid) "Pagado" else "Activo")
            }
            Text("Capital prestado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(l.principal), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("+ Intereses (${Money.rate(l.interestRateBps)}): ${Money.format(l.expectedProfit)}", style = MaterialTheme.typography.bodySmall)
            Text("Total pactado: ${Money.format(l.totalAmount)}", style = MaterialTheme.typography.bodySmall)
            Text("${l.installmentCount} cuotas · ${Frequency.valueOf(l.frequency).label}", style = MaterialTheme.typography.bodySmall)
            Text("Por cuota: ${s.installmentsByLoan[l.id].orEmpty().sortedBy { it.number }.joinToString(" · ") { "${Money.format(it.originalAmount)} (${displayDate(it.dueDate)})" }}", style = MaterialTheme.typography.bodySmall)
            Text("Préstamo del ${displayDate(l.loanDate)}", style = MaterialTheme.typography.bodySmall)
            Text(if (paid) "Préstamo pagado" else "Pendiente actual: ${Money.format(s.balance(l))}", style = MaterialTheme.typography.bodyMedium)
            if (overdue) Badge("Tiene cuotas vencidas", true)
        }
    }
}

@Composable
fun LoanScreen(s: Ledger, l: Loan, today: LocalDate, busy: Boolean, onInstallment: (Installment) -> Unit, onDelete: () -> Unit) {
    val installments = s.installmentsByLoan[l.id].orEmpty().sortedBy { it.number }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(s.customerById.getValue(l.customerId).displayName, style = MaterialTheme.typography.headlineSmall)
            Badge(if (s.balance(l) == 0L) "Pagado" else "Activo")
        }
        item { Metric("Capital prestado · original", l.principal, Modifier.fillMaxWidth()) }
        item { Card { Column(Modifier.padding(16.dp)) {
            Text("Interés pactado: ${Money.rate(l.interestRateBps)}", style = MaterialTheme.typography.bodyMedium)
            AmountRow("+ Intereses", l.expectedProfit)
            AmountRow("Total pactado", l.totalAmount)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            AmountRow("Cobrado", l.totalAmount - s.balance(l))
            AmountRow("Pendiente actual", s.balance(l), true)
            Text("${displayDate(l.loanDate)} · ${l.installmentCount} cuotas · ${Frequency.valueOf(l.frequency).label}", style = MaterialTheme.typography.bodySmall)
        } } }
        item { Section("Cuotas", "Tocá una cuota para cobrar o consultar sus pagos.") }
        items(installments, key = { it.id }) { i -> InstallmentCard(s, i, today, false) { onInstallment(i) } }
        item { TextButton(enabled = !busy, onClick = onDelete) { Icon(Icons.Default.Delete, null); Text("Eliminar préstamo", color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
fun SummaryScreen(s: Ledger, today: LocalDate) {
    var selected by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(selected)
    val summary = s.monthly(month, today)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(enabled = month.year > 1900, onClick = { selected = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-AR"))).replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.titleLarge)
            IconButton(enabled = month.year < 2200, onClick = { selected = month.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente") }
        } }
        item { TextButton(onClick = { selected = YearMonth.from(today).toString() }) { Text("Mes actual") } }
        item { Metric("Intereses efectivamente cobrados", summary.interestCollected, Modifier.fillMaxWidth()) }
        item { Section("Dinero que se movió en el mes") }
        item { Card { Column(Modifier.padding(16.dp)) {
            AmountRow("Capital prestado", summary.lent)
            AmountRow("Dinero efectivamente cobrado", summary.collected, true)
            AmountRow("Capital recuperado", summary.recovered)
            AmountRow("Ganancia cobrada", summary.interestCollected, true)
        } } }
        item { Section("Cuotas con vencimiento en el mes") }
        item { Card { Column(Modifier.padding(16.dp)) {
            AmountRow("Total previsto a cobrar", summary.scheduled)
            AmountRow("Ganancia prevista", summary.expectedInterest)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            AmountRow("Pendiente", summary.pending)
            AmountRow("Vencido", summary.overdue)
        } } }
        item { Text("Pendiente y vencido muestran el saldo actual de las cuotas de este mes, al ${displayDate(today.toString())}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("Cada pago se reparte proporcionalmente entre capital e intereses. La ganancia cobrada incluye únicamente pagos registrados; los ajustes de pesos se acumulan hasta cerrar el préstamo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

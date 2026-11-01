package com.aura.caradeverga

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private lateinit var model: AppViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[AppViewModel::class.java]
        setContent { CaraTheme { CaraApp(model) } }
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.refreshDate() }
}

@Composable
fun CaraTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFA1D5BD), onPrimary = Color(0xFF063829), primaryContainer = Color(0xFF244E3E),
        onPrimaryContainer = Color(0xFFBCEED6), secondary = Color(0xFFB8CCBF),
        background = Color(0xFF101512), surface = Color(0xFF101512), surfaceContainer = Color(0xFF1C211E),
        surfaceVariant = Color(0xFF3E4942), onSurfaceVariant = Color(0xFFC0C9C1)
    ) else lightColorScheme(
        primary = Color(0xFF28634B), onPrimary = Color.White, primaryContainer = Color(0xFFC5EBD5),
        onPrimaryContainer = Color(0xFF073823), secondary = Color(0xFF526657),
        background = Color(0xFFF7F9F5), surface = Color(0xFFF7F9F5), surfaceContainer = Color(0xFFEDF1EB),
        surfaceVariant = Color(0xFFDCE5DA), onSurfaceVariant = Color(0xFF414A42)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

data class Confirmation(val title: String, val text: String, val action: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaraApp(vm: AppViewModel) {
    val s by vm.ledger.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val fatal by vm.fatal.collectAsStateWithLifecycle()
    val pendingImport by vm.pendingImport.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf("main") }
    var customerId by rememberSaveable { mutableLongStateOf(0L) }
    var loanId by rememberSaveable { mutableLongStateOf(0L) }
    var installmentId by rememberSaveable { mutableLongStateOf(0L) }
    var editCustomer by rememberSaveable { mutableStateOf<Long?>(null) }
    var continueAfterCustomer by rememberSaveable { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    val snack = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { if (it != null) vm.export(it) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) vm.readImport(it) }
    LaunchedEffect(Unit) { vm.events.collectLatest { snack.showSnackbar(it) } }
    // A receipt from the previous screen must not cover the next form's actions.
    LaunchedEffect(route, tab) { snack.currentSnackbarData?.dismiss() }
    val goCustomer: (Long) -> Unit = { customerId = it; route = "customer" }
    val goLoan: (Loan) -> Unit = { customerId = it.customerId; loanId = it.id; route = "loan" }
    val newLoan: (Long) -> Unit = { customerId = it; route = if (it == 0L) "select" else "newLoan" }
    val goBack: () -> Unit = {
        route = when (route) { "loan" -> "customer"; "newLoan" -> "customer"; else -> "main" }
    }
    BackHandler(enabled = route != "main" && editCustomer == null && installmentId == 0L) { goBack() }
    val title = when (route) {
        "customer" -> "Cliente"
        "loan" -> "Préstamo #$loanId"
        "select" -> "Elegir cliente"
        "newLoan" -> "Nuevo préstamo"
        else -> listOf("CaraDeVerga", "Clientes", "Resumen", "Ajustes")[tab]
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = {
            if (route != "main") IconButton(onClick = goBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
        }) },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (route == "main") NavigationBar {
                val icons = listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.DateRange, Icons.Default.Settings)
                listOf("Inicio", "Clientes", "Resumen", "Ajustes").forEachIndexed { n, label ->
                    NavigationBarItem(selected = tab == n, onClick = { tab = n }, icon = { Icon(icons[n], null) }, label = { Text(label) })
                }
            }
        },
        floatingActionButton = {
            if (loaded && !busy && ((route == "main" && tab in 0..1) || (route == "customer" && s.customerById[customerId]?.archived == false))) {
                ExtendedFloatingActionButton(onClick = { newLoan(if (route == "customer") customerId else 0L) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nuevo préstamo") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when {
                fatal != null -> EmptyState("No se pudo abrir la app", fatal!!)
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                route == "select" -> CustomerPicker(s, onSelect = { newLoan(it.id) }, onCreate = { continueAfterCustomer = true; editCustomer = 0L })
                route == "newLoan" -> s.customerById[customerId]?.let { c -> NewLoanScreen(c, s.plans, today, busy) { input ->
                    vm.action("Préstamo guardado.", after = { route = "loan" }) {
                        loanId = vm.repository.newLoan(c.id, input, today)
                    }
                } } ?: EmptyState("Cliente no disponible", "Volvé y seleccioná un cliente.")
                route == "customer" -> s.customerById[customerId]?.let { c -> CustomerScreen(s, c, today, busy,
                    onLoan = goLoan, onEdit = { continueAfterCustomer = false; editCustomer = c.id },
                    onArchive = { confirmation = Confirmation(if (c.archived) "¿Restaurar cliente?" else "¿Archivar cliente?", if (c.archived) "Volverá a la lista de clientes." else "Se conserva todo el historial. Las deudas seguirán apareciendo en Inicio y Resumen.") {
                        vm.action(if (c.archived) "Cliente restaurado." else "Cliente archivado.") { vm.repository.archiveCustomer(c.id, !c.archived) }
                    } }, onDelete = { confirmation = Confirmation("¿Eliminar cliente?", "Se eliminará a ${c.displayName}. Esta acción no se puede deshacer.") {
                        vm.action("Cliente eliminado.", after = { route = "main" }) { vm.repository.deleteCustomer(c.id) }
                    } }) } ?: EmptyState("Cliente no disponible", "El cliente se eliminó o la copia de seguridad lo reemplazó.")
                route == "loan" -> s.loanById[loanId]?.let { l -> LoanScreen(s, l, today, busy, onInstallment = { installmentId = it.id }, onDelete = {
                    val ids = s.installmentsByLoan[l.id].orEmpty().map { it.id }.toSet()
                    val count = s.payments.count { it.installmentId in ids }
                    confirmation = Confirmation("¿Eliminar préstamo #${l.id}?", "Se borrarán este préstamo, sus ${l.installmentCount} cuotas y $count movimientos de pago. Sus importes dejarán de aparecer en caja. No se puede deshacer.") {
                        vm.action("Préstamo eliminado.", after = { route = "customer" }) { vm.repository.deleteLoan(l.id) }
                    }
                }) } ?: EmptyState("Préstamo no disponible", "Volvé al historial del cliente.")
                else -> when (tab) {
                    0 -> HomeScreen(s, today) { installmentId = it.id }
                    1 -> CustomersScreen(s, today, onCustomer = { goCustomer(it.id) }, onCreate = { continueAfterCustomer = false; editCustomer = 0L })
                    2 -> SummaryScreen(s, today)
                    3 -> SettingsScreen(s, busy,
                        onPlans = { plans -> vm.action("Planes guardados. Los préstamos anteriores conservan su tasa.") { vm.repository.updatePlans(plans) } },
                        onExport = { export.launch("CaraDeVerga-${today}.json") },
                        onImport = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        onDemo = { confirmation = Confirmation("¿Cargar ejemplos?", "Se crearán clientes ficticios y cuotas de prueba. Para trabajar con datos reales, podés borrar los préstamos de ejemplo y luego sus clientes.") {
                            vm.action("Ejemplos cargados.", after = { tab = 0 }) { vm.repository.loadDemo(today) }
                        } })
                }
            }
        }
    }
    editCustomer?.let { id -> CustomerForm(if (id == 0L) null else s.customerById[id], busy,
        onDismiss = { if (!busy) editCustomer = null }, onSave = { customer ->
            vm.action("Cliente guardado.", after = { editCustomer = null; route = if (continueAfterCustomer) "newLoan" else "customer" }) {
                customerId = vm.repository.saveCustomer(customer)
            }
        }) }
    s.installmentById[installmentId]?.let { i -> PaymentDialog(s, i, today, busy,
        onDismiss = { if (!busy) installmentId = 0L }, onPayment = { amount, date ->
            vm.action("Pago registrado.", after = { installmentId = 0L }) { vm.repository.registerPayment(i.id, amount, date, today) }
        }, onDelete = { p -> confirmation = Confirmation("¿Eliminar pago?", "Se eliminará el cobro de ${Money.format(p.amount)} del ${displayDate(p.paymentDate)}. La cuota volverá a mostrar ese saldo pendiente.") {
            vm.action("Pago eliminado.") { vm.repository.deletePayment(p.id) }
        } }) }
    confirmation?.let { c -> AlertDialog(onDismissRequest = { confirmation = null }, modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(c.title) }, text = { Text(c.text) },
        confirmButton = { TextButton(enabled = !busy, onClick = { confirmation = null; c.action() }) { Text("Confirmar") } },
        dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } }) }
    pendingImport?.let { imported -> AlertDialog(onDismissRequest = { if (!busy) vm.pendingImport.value = null }, modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("¿Restaurar esta copia?") }, text = {
            Text("${imported.customers.size} clientes · ${imported.loans.size} préstamos · ${imported.payments.size} pagos\n\nReemplazará TODOS los datos y planes actuales. Exportá primero una copia si querés conservarlos.")
        }, confirmButton = { TextButton(enabled = !busy, onClick = {
            vm.action("Copia restaurada.", after = { vm.pendingImport.value = null; route = "main"; tab = 0; installmentId = 0L }) { vm.repository.restore(imported, today) }
        }) { Text("Reemplazar y restaurar") } }, dismissButton = { TextButton(enabled = !busy, onClick = { vm.pendingImport.value = null }) { Text("Cancelar") } }) }
}

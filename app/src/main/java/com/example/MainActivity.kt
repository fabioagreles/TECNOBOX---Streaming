package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.AppRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DateHelper
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ViewModelFactory
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { AppRepository(database.appDao()) }
    private val viewModel: MainViewModel by viewModels { ViewModelFactory(repository) }

    // SAF Launchers for backup creation and restoration
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val outputStream = contentResolver.openOutputStream(it)
                viewModel.runExport(this, outputStream)
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao exportar backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = contentResolver.openInputStream(it)
                viewModel.runImport(this, inputStream)
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao carregar backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(
                    viewModel = viewModel,
                    onBackupExport = { createDocumentLauncher.launch("backup_mensalidades.json") },
                    onBackupImport = { openDocumentLauncher.launch(arrayOf("application/json")) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onBackupExport: () -> Unit,
    onBackupImport: () -> Unit
) {
    val context = LocalContext.current
    val clients by viewModel.clients.collectAsState()
    val services by viewModel.services.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val whatsAppConfig by viewModel.whatsAppConfig.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var currentTab by remember { mutableStateOf("clientes") }

    // Forms / Dialog State
    var showAddClientDialog by remember { mutableStateOf(false) }
    var showEditClientDialog by remember { mutableStateOf(false) }
    var showRenewClientDialog by remember { mutableStateOf(false) }
    var selectedClientForRenewal by remember { mutableStateOf<Client?>(null) }
    var selectedClientForEdit by remember { mutableStateOf<Client?>(null) }

    // Toast updates
    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Gestão de Mensalidades",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding()
            ) {
                NavigationBarItem(
                    selected = currentTab == "clientes",
                    onClick = { currentTab = "clientes" },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Clientes") },
                    label = { Text("Clientes", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == "servicos",
                    onClick = { currentTab = "servicos" },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Serviços") },
                    label = { Text("Serviços", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == "mensagens",
                    onClick = { currentTab = "mensagens" },
                    icon = { Icon(Icons.Default.Send, contentDescription = "Mensagens") },
                    label = { Text("Cobranças", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == "financeiro",
                    onClick = { currentTab = "financeiro" },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Financeiro") },
                    label = { Text("Financeiro", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = currentTab == "backup",
                    onClick = { currentTab = "backup" },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Backup") },
                    label = { Text("Backup", fontSize = 11.sp) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "clientes" -> ClientsTab(
                    viewModel = viewModel,
                    clients = clients,
                    services = services,
                    onAddClick = {
                        if (services.isEmpty()) {
                            Toast.makeText(context, "Cadastre um serviço primeiro!", Toast.LENGTH_LONG).show()
                        } else {
                            showAddClientDialog = true
                        }
                    },
                    onEditClick = { client ->
                        selectedClientForEdit = client
                        showEditClientDialog = true
                    },
                    onRenewClick = { client ->
                        selectedClientForRenewal = client
                        showRenewClientDialog = true
                    }
                )
                "servicos" -> ServicesTab(
                    viewModel = viewModel,
                    services = services
                )
                "mensagens" -> MessagesTab(
                    viewModel = viewModel,
                    templates = templates,
                    config = whatsAppConfig
                )
                "financeiro" -> FinanceTab(
                    viewModel = viewModel,
                    transactions = transactions
                )
                "backup" -> BackupTab(
                    onExport = onBackupExport,
                    onImport = onBackupImport
                )
            }
        }
    }

    // Modal forms definitions
    if (showAddClientDialog) {
        ClientFormDialog(
            isEdit = false,
            client = null,
            services = services,
            onDismiss = { showAddClientDialog = false },
            onSave = { newClient ->
                viewModel.saveClient(newClient)
                showAddClientDialog = false
            }
        )
    }

    if (showEditClientDialog && selectedClientForEdit != null) {
        ClientFormDialog(
            isEdit = true,
            client = selectedClientForEdit,
            services = services,
            onDismiss = {
                showEditClientDialog = false
                selectedClientForEdit = null
            },
            onSave = { updatedClient ->
                viewModel.saveClient(updatedClient)
                showEditClientDialog = false
                selectedClientForEdit = null
            }
        )
    }

    if (showRenewClientDialog && selectedClientForRenewal != null) {
        RenewalDialog(
            client = selectedClientForRenewal!!,
            onDismiss = {
                showRenewClientDialog = false
                selectedClientForRenewal = null
            },
            onConfirm = { date, months ->
                viewModel.renewClient(selectedClientForRenewal!!, date, months)
                showRenewClientDialog = false
                selectedClientForRenewal = null
            }
        )
    }
}

// ---------------------- CLIENTS TAB ----------------------
@Composable
fun ClientsTab(
    viewModel: MainViewModel,
    clients: List<Client>,
    services: List<Service>,
    onAddClick: () -> Unit,
    onEditClick: (Client) -> Unit,
    onRenewClick: (Client) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("TODOS") } // "TODOS", "ATIVOS", "VENCIDOS"

    val context = LocalContext.current

    // Apply filters
    val filteredClients = clients.filter { client ->
        val matchesSearch = client.nome.contains(searchQuery, ignoreCase = true) || 
                            client.usuario.contains(searchQuery, ignoreCase = true)
        val isOverdue = DateHelper.isOverdue(client.dataVencimento)
        val matchesFilter = when (filterStatus) {
            "ATIVOS" -> !isOverdue
            "VENCIDOS" -> isOverdue
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Search & Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar Cliente (Nome ou Usuário)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("client_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterStatus == "TODOS",
                    onClick = { filterStatus = "TODOS" },
                    label = { Text("Todos (${clients.size})") }
                )
                FilterChip(
                    selected = filterStatus == "ATIVOS",
                    onClick = { filterStatus = "ATIVOS" },
                    label = { Text("Ativos (${clients.count { !DateHelper.isOverdue(it.dataVencimento) }})") }
                )
                FilterChip(
                    selected = filterStatus == "VENCIDOS",
                    onClick = { filterStatus = "VENCIDOS" },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    label = { Text("Vencidos (${clients.count { DateHelper.isOverdue(it.dataVencimento) }})") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredClients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Sem clientes",
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Nenhum cliente cadastrado ainda." else "Nenhum cliente atende aos filtros.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredClients) { client ->
                        val service = services.find { it.id == client.servicoId }
                        val serviceName = service?.nome ?: "Serviço Desconhecido"
                        ClientCard(
                            client = client,
                            serviceName = serviceName,
                            onEdit = { onEditClick(client) },
                            onRenew = { onRenewClick(client) },
                            onDelete = { viewModel.deleteClient(client) },
                            onSendMessage = { type ->
                                viewModel.sendWhatsAppMessage(context, client, type)
                            }
                        )
                    }
                }
            }
        }

        // Floating action button
        FloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_client_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Client")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Cliente", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ClientCard(
    client: Client,
    serviceName: String,
    onEdit: () -> Unit,
    onRenew: () -> Unit,
    onDelete: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    val isOverdue = DateHelper.isOverdue(client.dataVencimento)
    val remainingDays = DateHelper.daysRemaining(client.dataVencimento)
    val formattedVencimento = DateHelper.isoToDisplay(client.dataVencimento)
    val formattedAdesao = DateHelper.isoToDisplay(client.dataAdesao)

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteMockDialog by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isOverdue) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Name and Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.nome,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "WhatsApp: +${client.whatsapp}",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                // Expiration Badge
                if (isOverdue) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Vencido",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2E7D32))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (remainingDays == 0) "Vence Hoje" else "Vence em $remainingDays d",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            // Client Login Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Dados de Acesso", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Usuário: ${client.usuario}", fontSize = 13.sp)
                    Text("Senha: ${client.senha}", fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Serviço & Mensalidade", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Text(serviceName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Valor: R$ ${DecimalFormat("#,##0.00").format(client.valor)}", fontSize = 13.sp)
                }
            }

            if (client.observacao.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Obs: ${client.observacao}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Adesão: $formattedAdesao", fontSize = 11.sp, color = Color.Gray)
                Text("Vencimento: $formattedVencimento", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WhatsApp Messages Quick Send Menu
                Box {
                    Button(
                        onClick = { showMenu = !showMenu },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF25D366),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mensagem de Vencimento") },
                            onClick = {
                                onSendMessage("vencimento")
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Confirmação de Renovação") },
                            onClick = {
                                onSendMessage("confirmacao_renovacao")
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Enviar Credenciais de Acesso") },
                            onClick = {
                                onSendMessage("dados_cliente")
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onRenew,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Renovar", fontSize = 12.sp)
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(
                        onClick = { showDeleteMockDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Apagar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDeleteMockDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMockDialog = false },
            title = { Text("Excluir Cliente?") },
            text = { Text("Tem certeza que deseja excluir o cliente ${client.nome}? Esta ação é irreversível.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteMockDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMockDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// ---------------------- CLIENT FORM DIALOG ----------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFormDialog(
    isEdit: Boolean,
    client: Client?,
    services: List<Service>,
    onDismiss: () -> Unit,
    onSave: (Client) -> Unit
) {
    val context = LocalContext.current

    var nome by remember { mutableStateOf(client?.nome ?: "") }
    var usuario by remember { mutableStateOf(client?.usuario ?: "") }
    var senha by remember { mutableStateOf(client?.senha ?: "") }
    var whatsapp by remember { mutableStateOf(client?.whatsapp ?: "") }
    var dataAdesao by remember { mutableStateOf(client?.dataAdesao ?: DateHelper.getTodayIso()) }
    var dataVencimento by remember { mutableStateOf(client?.dataVencimento ?: DateHelper.addDaysToIso(DateHelper.getTodayIso(), 31)) }
    var selectedServiceIndex by remember {
        val initialIndex = if (client != null) services.indexOfFirst { it.id == client.servicoId } else 0
        mutableStateOf(if (initialIndex >= 0) initialIndex else 0)
    }
    var valor by remember {
        val calculatedVal = client?.valor ?: (if (services.isNotEmpty()) services[0].precoVenda else 0.0)
        mutableStateOf(calculatedVal.toString())
    }
    var observacao by remember { mutableStateOf(client?.observacao ?: "") }

    var dropdownExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isEdit) "Editar Cliente" else "Novo Cliente",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome Completo") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = usuario,
                        onValueChange = { usuario = it },
                        label = { Text("Usuário") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = senha,
                        onValueChange = { senha = it },
                        label = { Text("Senha") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it },
                    label = { Text("WhatsApp (Ex: 5581999998888)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Service Dropdown
                if (services.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = services[selectedServiceIndex].nome,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Serviço Pré-cadastrado") },
                            trailingIcon = {
                                IconButton(onClick = { dropdownExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            services.forEachIndexed { idx, s ->
                                DropdownMenuItem(
                                    text = { Text("${s.nome} - Venda: R$ ${DecimalFormat("#,##0.00").format(s.precoVenda)}") },
                                    onClick = {
                                        selectedServiceIndex = idx
                                        // Default the customized client price to the service's sale price automatically
                                        valor = s.precoVenda.toString()
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text("Nenhum serviço cadastrado! Acesse a aba de Serviços.", color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = valor,
                    onValueChange = { valor = it },
                    label = { Text("Valor cobrado mensal (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Date Picker Fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Adesão Picker
                    OutlinedTextField(
                        value = DateHelper.isoToDisplay(dataAdesao),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Adesão") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showDatePicker(context, dataAdesao) { selectedDate ->
                                    dataAdesao = selectedDate
                                    // Automatic recalculation of +31 days on new registration if chosen
                                    if (!isEdit) {
                                        dataVencimento = DateHelper.addDaysToIso(selectedDate, 31)
                                    }
                                }
                            },
                        enabled = false, // Allows click on parent wrapper
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Vencimento Picker
                    OutlinedTextField(
                        value = DateHelper.isoToDisplay(dataVencimento),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Até (Vencimento)") },
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                showDatePicker(context, dataVencimento) { selectedDate ->
                                    dataVencimento = selectedDate
                                }
                            },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = observacao,
                    onValueChange = { observacao = it },
                    label = { Text("Observação") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nome.isBlank() || usuario.isBlank() || senha.isBlank() || whatsapp.isBlank()) {
                                Toast.makeText(context, "Por favor, preencha todos os campos !", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (services.isEmpty()) {
                                Toast.makeText(context, "Favor cadastrar ao menos 1 serviço antes!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val numValor = valor.toDoubleOrNull() ?: 0.0
                            val targetService = services[selectedServiceIndex]

                            val item = Client(
                                id = client?.id ?: 0,
                                nome = nome,
                                usuario = usuario,
                                senha = senha,
                                whatsapp = whatsapp,
                                dataAdesao = dataAdesao,
                                dataVencimento = dataVencimento,
                                servicoId = targetService.id,
                                valor = numValor,
                                observacao = observacao
                            )
                            onSave(item)
                        }
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

// ---------------------- RENEWAL DIALOG ----------------------
@Composable
fun RenewalDialog(
    client: Client,
    onDismiss: () -> Unit,
    onConfirm: (nextDate: String, months: Int) -> Unit
) {
    val context = LocalContext.current
    var renewalOption by remember { mutableStateOf("31") } // "30", "31", "custom"
    var totalMonths by remember { mutableStateOf("1") }

    // Calculates prospective target dates based on selection
    val baseFromDate = if (DateHelper.isOverdue(client.dataVencimento)) DateHelper.getTodayIso() else client.dataVencimento
    var customDatePicked by remember { mutableStateOf(DateHelper.addDaysToIso(baseFromDate, 30)) }

    val calculatedTargetDate = when (renewalOption) {
        "30" -> DateHelper.addDaysToIso(baseFromDate, 30)
        "31" -> DateHelper.addDaysToIso(baseFromDate, 31)
        else -> customDatePicked
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Renovar Cliente: ${client.nome}", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "A data atual de validade é ${DateHelper.isoToDisplay(client.dataVencimento)}.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text("Selecione o prazo de Renovação:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { renewalOption = "30" }
                    ) {
                        RadioButton(selected = renewalOption == "30", onClick = { renewalOption = "30" })
                        Text("Adicionar 30 Dias")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { renewalOption = "31" }
                    ) {
                        RadioButton(selected = renewalOption == "31", onClick = { renewalOption = "31" })
                        Text("Adicionar 31 Dias (Auto)")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { renewalOption = "custom" }
                    ) {
                        RadioButton(selected = renewalOption == "custom", onClick = { renewalOption = "custom" })
                        Text("Escolher Data Personalizada")
                    }
                }

                // Custom Form displays when "custom" is activated
                if (renewalOption == "custom") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Configurar prazo personalizado:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = DateHelper.isoToDisplay(customDatePicked),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Nova Data de Validade") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDatePicker(context, customDatePicked) { picked ->
                                    customDatePicked = picked
                                }
                            },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = totalMonths,
                        onValueChange = { totalMonths = it },
                        label = { Text("Vincular Financeiro: Quantos meses cobrar?") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    val monthsCount = if (renewalOption == "custom") (totalMonths.toIntOrNull() ?: 1) else 1
                    val calculatedCost = client.valor * monthsCount
                    Column {
                        Text(
                            text = "Resumo:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Novo vencimento: ${DateHelper.isoToDisplay(calculatedTargetDate)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Valor a registrar: R$ ${DecimalFormat("#,##0.00").format(calculatedCost)} ($monthsCount mês/meses cobrado(s))",
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val months = if (renewalOption == "custom") (totalMonths.toIntOrNull() ?: 1) else 1
                    if (months <= 0) {
                        Toast.makeText(context, "Digite um número de meses válido!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onConfirm(calculatedTargetDate, months)
                }
            ) {
                Text("Confirmar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// Date selection logic launcher
fun showDatePicker(context: Context, initialDateIso: String, onSelected: (String) -> Unit) {
    val cal = Calendar.getInstance()
    try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(initialDateIso)
        if (d != null) cal.time = d
    } catch (e: Exception) {}

    DatePickerDialog(
        context,
        { _, y, m, d ->
            val sel = Calendar.getInstance().apply {
                set(Calendar.YEAR, y)
                set(Calendar.MONTH, m)
                set(Calendar.DAY_OF_MONTH, d)
            }
            onSelected(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(sel.time))
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}


// ---------------------- SERVICES TAB ----------------------
@Composable
fun ServicesTab(
    viewModel: MainViewModel,
    services: List<Service>
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var buyPriceText by remember { mutableStateOf("") }
    var sellPriceText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Cadastrar Novo Serviço",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Serviço (Ex: Mensalidade Netflix, Painel IPTV)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = buyPriceText,
                        onValueChange = { buyPriceText = it },
                        label = { Text("Preço Compra (Custo)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = sellPriceText,
                        onValueChange = { sellPriceText = it },
                        label = { Text("Preço Venda (Preço final)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (name.isBlank() || buyPriceText.isBlank() || sellPriceText.isBlank()) {
                            Toast.makeText(context, "Favor preencher todos os campos!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val bp = buyPriceText.toDoubleOrNull() ?: 0.0
                        val sp = sellPriceText.toDoubleOrNull() ?: 0.0

                        viewModel.saveService(
                            Service(nome = name, precoCompra = bp, precoVenda = sp)
                        )

                        // Clear inputs
                        name = ""
                        buyPriceText = ""
                        sellPriceText = ""
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adicionar Serviço")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Serviços Cadastrados (${services.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (services.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum serviço disponível ainda.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(services) { s ->
                    val profitMargin = s.precoVenda - s.precoCompra
                    var showConfirmDelete by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.nome, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text("Compra (Custo): R$ ${DecimalFormat("#,##0.00").format(s.precoCompra)}", fontSize = 12.sp, color = Color.Gray)
                                    Text("Venda: R$ ${DecimalFormat("#,##0.00").format(s.precoVenda)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                                Text("Lucro Base: R$ ${DecimalFormat("#,##0.00").format(profitMargin)} por assinatura", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            }

                            IconButton(onClick = { showConfirmDelete = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Apagar Serviço", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    if (showConfirmDelete) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDelete = false },
                            title = { Text("Excluir Serviço?") },
                            text = { Text("Excluir o serviço '${s.nome}' deletará a referência para novos mensalistas associados. Deseja prosseguir?") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteService(s)
                                        showConfirmDelete = false
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Excluir")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirmDelete = false }) {
                                    Text("Cancelar")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


// ---------------------- MESSAGES TAB ----------------------
@Composable
fun MessagesTab(
    viewModel: MainViewModel,
    templates: List<MessageTemplate>,
    config: WhatsAppConfig
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Match database items to UI streams
    val vencimentoTpl = templates.find { it.tipo == "vencimento" }?.template ?: ""
    val renovacaoTpl = templates.find { it.tipo == "confirmacao_renovacao" }?.template ?: ""
    val dadosTpl = templates.find { it.tipo == "dados_cliente" }?.template ?: ""

    var tempVencimento by remember { mutableStateOf("") }
    var tempRenovacao by remember { mutableStateOf("") }
    var tempDados by remember { mutableStateOf("") }

    // WhatsApp configuration form inputs
    var apiType by remember { mutableStateOf(config.apiType) }
    var apiUrl by remember { mutableStateOf(config.apiUrl) }
    var apiMethod by remember { mutableStateOf(config.apiMethod) }
    var payloadJson by remember { mutableStateOf(config.payloadJson) }
    var headersJson by remember { mutableStateOf(config.headersJson) }

    // Prepopulate inputs once database loads templates
    LaunchedEffect(templates) {
        if (tempVencimento.isEmpty() && vencimentoTpl.isNotEmpty()) tempVencimento = vencimentoTpl
        if (tempRenovacao.isEmpty() && renovacaoTpl.isNotEmpty()) tempRenovacao = renovacaoTpl
        if (tempDados.isEmpty() && dadosTpl.isNotEmpty()) tempDados = dadosTpl
    }

    LaunchedEffect(config) {
        apiType = config.apiType
        apiUrl = config.apiUrl
        apiMethod = config.apiMethod
        payloadJson = config.payloadJson
        headersJson = config.headersJson
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Modelos de Mensagens de Cobrança",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Personalize as mensagens automáticas enviadas aos clientes. Use as seguintes variáveis para substituição dinâmica em tempo de envio:\n{nome}, {usuario}, {senha}, {servico}, {valor}, {data_validade}",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Template Forms
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("1. Aviso de Vencimento de Cobrança", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = tempVencimento,
                    onValueChange = { tempVencimento = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.saveTemplate(MessageTemplate("vencimento", tempVencimento)) },
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Salvar Mensagem Vencida", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("2. Confirmação de Renovação / Pagamento", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = tempRenovacao,
                    onValueChange = { tempRenovacao = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.saveTemplate(MessageTemplate("confirmacao_renovacao", tempRenovacao)) },
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Salvar Confirmação", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("3. Credenciais de Acesso (Dados do Cliente)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = tempDados,
                    onValueChange = { tempDados = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.saveTemplate(MessageTemplate("dados_cliente", tempDados)) },
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Salvar Dados Cliente", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // WhatsApp Integration Form
        Text(
            text = "Configuração do Gateway do WhatsApp",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Configure se as mensagens devem abrir no aplicativo WhatsApp local ou se quer automatizar com API externa de disparo de mensagens.",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Método de Envio", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { apiType = "intent" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = apiType == "intent", onClick = { apiType = "intent" })
                        Text("App WhatsApp (Grátis)", fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { apiType = "api" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = apiType == "api", onClick = { apiType = "api" })
                        Text("API Automática (Webhook)", fontSize = 13.sp)
                    }
                }

                if (apiType == "api") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Parâmetros do Servidor de Envio (API)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = apiUrl,
                        onValueChange = { apiUrl = it },
                        label = { Text("URL do Disparo (Ex: http://api.z-api.io/message)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = apiMethod == "POST", onClick = { apiMethod = "POST" })
                            Text("POST")
                        }
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = apiMethod == "GET", onClick = { apiMethod = "GET" })
                            Text("GET")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = headersJson,
                        onValueChange = { headersJson = it },
                        label = { Text("Headers HTTP (JSON)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = payloadJson,
                        onValueChange = { payloadJson = it },
                        label = { Text("Payload JSON (Vincule {number} e {message})") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        // Easy validation of input json blocks
                        if (apiType == "api") {
                            try {
                                JSONObject(headersJson)
                                JSONObject(payloadJson)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Erro no formato JSON de headers ou payload!", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                        }

                        viewModel.saveWhatsAppConfig(
                            WhatsAppConfig(
                                id = 1,
                                apiType = apiType,
                                apiUrl = apiUrl,
                                apiMethod = apiMethod,
                                payloadJson = payloadJson,
                                headersJson = headersJson
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salvar Configurações do WhatsApp")
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}


// ---------------------- FINANCE TAB ----------------------
@Composable
fun FinanceTab(
    viewModel: MainViewModel,
    transactions: List<FinanceTransaction>
) {
    val totalRevenue = transactions.sumOf { it.valorRecebido }
    val totalCost = transactions.sumOf { it.custoFaturado }
    val netProfit = transactions.sumOf { it.lucroLiquido }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Resumo Financeiro de Caixa",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Metrics Grid Box
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Total Revenue Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Faturamento", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "R$ ${DecimalFormat("#,##0.00").format(totalRevenue)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }

                // Cost of Goods Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Custo Serviços", fontSize = 11.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "R$ ${DecimalFormat("#,##0.00").format(totalCost)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFB71C1C)
                        )
                    }
                }
            }

            // Net Profit Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Lucro Líquido Real", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "R$ ${DecimalFormat("#,##0.00").format(netProfit)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Histórico de Lançamentos (${transactions.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            if (transactions.isNotEmpty()) {
                var showClearDialog by remember { mutableStateOf(false) }

                TextButton(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Limpar Histórico")
                }

                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text("Zerar Histórico?") },
                        text = { Text("Isto irá apagar todos os registros de pagamentos do banco de dados local. Os prazos dos clientes não serão afetados. Deseja prosseguir?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.clearTransactions()
                                    showClearDialog = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Apagar Tudo")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDialog = false }) {
                                Text("Voltar")
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhuma transação financeira efetuada.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { tx ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.clienteNome,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${tx.servicoNome} | ${tx.meses} mês(es)",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Data Pagamento: ${DateHelper.isoToDisplay(tx.dataTransacao)}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+ R$ ${DecimalFormat("#,##0.00").format(tx.valorRecebido)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = "Lucro: R$ ${DecimalFormat("#,##0.00").format(tx.lucroLiquido)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { viewModel.deleteTransaction(tx) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Limpar registro",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ---------------------- BACKUP TAB ----------------------
@Composable
fun BackupTab(
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Segurança e Backup",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Backup e Restauração",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Proteja os dados do seu aplicativo exportando uma cópia de segurança em arquivo. Se trocar de aparelho ou reinstalar o aplicativo, poderá restaurar todos os clientes, serviços e lançamentos financeiros imediatamente.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Create export block
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExport() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Fazer Backup Geral (Exportar)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Salvar arquivo de banco de dados em formato JSON no seu celular, Google Drive ou enviar via WhatsApp.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Create import block
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onImport() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Restaurar Backup (Importar)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Escolha um arquivo backup de extensão JSON gerado anteriormente por este aplicativo para importar e substituir os dados locais.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

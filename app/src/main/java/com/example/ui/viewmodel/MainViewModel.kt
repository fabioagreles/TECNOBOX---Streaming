package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(private val repository: AppRepository) : ViewModel() {

    // Lists from Database
    val services: StateFlow<List<Service>> = repository.allServices.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val clients: StateFlow<List<Client>> = repository.allClients.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val templates: StateFlow<List<MessageTemplate>> = repository.allTemplates.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val transactions: StateFlow<List<FinanceTransaction>> = repository.allTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // WhatsApp configuration live state
    private val _whatsAppConfig = MutableStateFlow(WhatsAppConfig())
    val whatsAppConfig: StateFlow<WhatsAppConfig> = _whatsAppConfig.asStateFlow()

    // Status / Toast Messages
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val httpClient = OkHttpClient()

    init {
        viewModelScope.launch {
            repository.prepopulateDefaults()
            loadWhatsAppConfig()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private suspend fun loadWhatsAppConfig() {
        _whatsAppConfig.value = repository.getWhatsAppConfig()
    }

    // CRUD Service
    fun saveService(service: Service) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertService(service)
            _statusMessage.value = "Serviço salvo com sucesso!"
        }
    }

    fun deleteService(service: Service) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteService(service)
            _statusMessage.value = "Serviço removido com sucesso!"
        }
    }

    // CRUD Client
    fun saveClient(client: Client) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertClient(client)
            _statusMessage.value = "Cliente salvo com sucesso!"
        }
    }

    fun deleteClient(client: Client) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteClient(client)
            _statusMessage.value = "Cliente removido com sucesso!"
        }
    }

    // Update Message Templates
    fun saveTemplate(template: MessageTemplate) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTemplate(template)
            _statusMessage.value = "Template de mensagem atualizado!"
        }
    }

    // Save WhatsApp API configurations
    fun saveWhatsAppConfig(config: WhatsAppConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertWhatsAppConfig(config)
            _whatsAppConfig.value = config
            _statusMessage.value = "Configurações do WhatsApp salvas!"
        }
    }

    // Renewal Logic
    fun renewClient(client: Client, nextDueDate: String, months: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // Fetch associated service
            val service = repository.getServiceById(client.servicoId)
            val serviceCost = service?.precoCompra ?: 0.0
            val serviceName = service?.nome ?: "Serviço Desconhecido"

            // 1. Calculate finance stats
            val valorRecebido = client.valor * months
            val custoFaturado = serviceCost * months
            val lucroLiquido = valorRecebido - custoFaturado

            // 2. Insert transaction log
            val transaction = FinanceTransaction(
                clienteId = client.id,
                clienteNome = client.nome,
                servicoId = client.servicoId,
                servicoNome = serviceName,
                dataTransacao = DateHelper.getTodayIso(),
                meses = months,
                valorRecebido = valorRecebido,
                custoFaturado = custoFaturado,
                lucroLiquido = lucroLiquido
            )
            repository.insertTransaction(transaction)

            // 3. Update Client with new due date
            val updatedClient = client.copy(
                dataVencimento = nextDueDate
            )
            repository.insertClient(updatedClient)

            _statusMessage.value = "Cliente renovado por $months mês(es) com sucesso!"
        }
    }

    // Delete a particular transaction
    fun deleteTransaction(transaction: FinanceTransaction) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTransaction(transaction)
            _statusMessage.value = "Registro financeiro removido!"
        }
    }

    // Clean transaction logs
    fun clearTransactions() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllTransactions()
            _statusMessage.value = "Histórico financeiro limpo!"
        }
    }

    // Message Composition Helper
    suspend fun getFormattedMessage(tipo: String, client: Client): String {
        val templateObj = repository.getTemplateByTipo(tipo) ?: return ""
        val service = repository.getServiceById(client.servicoId)
        val serviceName = service?.nome ?: "Sem Serviço"
        return DateHelper.replaceVariables(templateObj.template, client, serviceName)
    }

    // WhatsApp Message Dispatcher
    fun sendWhatsAppMessage(context: Context, client: Client, type: String) {
        viewModelScope.launch {
            val message = getFormattedMessage(type, client)
            if (message.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Template de mensagem vazio!", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val sanitizedPhone = client.whatsapp.replace(Regex("[^0-9]"), "")
            val config = whatsAppConfig.value

            if (config.apiType == "api" && config.apiUrl.isNotBlank()) {
                // Background Automatic API Send
                withContext(Dispatchers.IO) {
                    try {
                        // Build payload
                        var payload = config.payloadJson
                            .replace("{number}", sanitizedPhone)
                            .replace("{message}", JSONObject.quote(message).trim('"')) // Escapes correct quotes for raw JSON body

                        val headers = JSONObject(config.headersJson)

                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val reqBody = payload.toRequestBody(mediaType)

                        val reqBuilder = Request.Builder()
                            .url(config.apiUrl)

                        // Add headers
                        headers.keys().forEach { key ->
                            reqBuilder.addHeader(key, headers.getString(key))
                        }

                        if (config.apiMethod.equals("POST", ignoreCase = true)) {
                            reqBuilder.post(reqBody)
                        } else {
                            // If GET, append params to url safely
                            val separator = if (config.apiUrl.contains("?")) "&" else "?"
                            val urlWithParams = config.apiUrl + separator + "phone=" + sanitizedPhone + "&message=" + Uri.encode(message)
                            reqBuilder.url(urlWithParams).get()
                        }

                        val response = httpClient.newCall(reqBuilder.build()).execute()
                        val bodyText = response.body?.string() ?: ""

                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                Toast.makeText(context, "Mensagem enviada automaticamente via API com sucesso!", Toast.LENGTH_LONG).show()
                            } else {
                                Log.e("WA_API_ERROR", "Status: ${response.code} Body: $bodyText")
                                Toast.makeText(context, "Erro na API (${response.code}). Enviando via app...", Toast.LENGTH_LONG).show()
                                launchWhatsAppIntent(context, sanitizedPhone, message)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Falha na conexão de API. Enviando via app do WhatsApp...", Toast.LENGTH_LONG).show()
                            launchWhatsAppIntent(context, sanitizedPhone, message)
                        }
                    }
                }
            } else {
                // Standard App Launch Intent
                launchWhatsAppIntent(context, sanitizedPhone, message)
            }
        }
    }

    private fun launchWhatsAppIntent(context: Context, phone: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = "https://api.whatsapp.com/send?phone=$phone&text=" + Uri.encode(message)
                data = Uri.parse(uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp não instalado no dispositivo!", Toast.LENGTH_SHORT).show()
        }
    }

    // Backup & Restore
    fun runExport(context: Context, outStream: OutputStream?) {
        if (outStream == null) {
            _statusMessage.value = "Erro ao localizar mídia para salvar o backup."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.exportBackup(outStream)
            _statusMessage.value = if (success) "Backup exportado com sucesso!" else "Falha ao exportar backup."
        }
    }

    fun runImport(context: Context, inStream: InputStream?) {
        if (inStream == null) {
            _statusMessage.value = "Arquivo de backup inválido ou ilegível."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.importBackup(inStream)
            _statusMessage.value = if (success) "Dados de backup importados e restaurados com sucesso!" else "Falha ao restaurar dados do arquivo."
        }
    }
}

// Custom date helpers for zero desugaring compatibility
object DateHelper {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun getTodayIso(): String = isoFormat.format(Calendar.getInstance().time)

    fun getTodayDisplay(): String = displayFormat.format(Calendar.getInstance().time)

    fun addDaysToIso(isoDate: String, days: Int): String {
        val cal = Calendar.getInstance()
        return try {
            val date = isoFormat.parse(isoDate)
            if (date != null) cal.time = date
            cal.add(Calendar.DAY_OF_YEAR, days)
            isoFormat.format(cal.time)
        } catch (e: Exception) {
            isoDate
        }
    }

    fun isoToDisplay(isoDate: String): String {
        return try {
            val date = isoFormat.parse(isoDate)
            if (date != null) displayFormat.format(date) else isoDate
        } catch (e: Exception) {
            isoDate
        }
    }

    fun displayToIso(displayDate: String): String {
        return try {
            val date = displayFormat.parse(displayDate)
            if (date != null) isoFormat.format(date) else displayDate
        } catch (e: Exception) {
            displayDate
        }
    }

    fun isOverdue(isoDateString: String): Boolean {
        return try {
            val date = isoFormat.parse(isoDateString) ?: return false
            val today = isoFormat.parse(getTodayIso()) ?: return false
            date.before(today)
        } catch (e: Exception) {
            false
        }
    }

    fun daysRemaining(isoDateString: String): Int {
        return try {
            val date = isoFormat.parse(isoDateString) ?: return 0
            val today = isoFormat.parse(getTodayIso()) ?: return 0
            val diff = date.time - today.time
            (diff / (1000 * 60 * 60 * 24)).toInt()
        } catch (e: Exception) {
            0
        }
    }

    fun replaceVariables(template: String, client: Client, serviceName: String): String {
        return template
            .replace("{nome}", client.nome)
            .replace("{usuario}", client.usuario)
            .replace("{senha}", client.senha)
            .replace("{servico}", serviceName)
            .replace("{valor}", String.format(Locale.US, "%.2f", client.valor))
            .replace("{data_validade}", isoToDisplay(client.dataVencimento))
            .replace("{vencimento}", isoToDisplay(client.dataVencimento))
    }
}

// Factory to inject repository manually in ComponentActivity
class ViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

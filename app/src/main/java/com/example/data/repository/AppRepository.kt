package com.example.data.repository

import com.example.data.dao.AppDao
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

class AppRepository(private val appDao: AppDao) {

    val allServices: Flow<List<Service>> = appDao.getAllServices()
    val allClients: Flow<List<Client>> = appDao.getAllClients()
    val allTemplates: Flow<List<MessageTemplate>> = appDao.getAllTemplates()
    val allTransactions: Flow<List<FinanceTransaction>> = appDao.getAllTransactions()

    suspend fun getServiceById(id: Int): Service? = appDao.getServiceById(id)
    suspend fun insertService(service: Service): Long = appDao.insertService(service)
    suspend fun deleteService(service: Service) = appDao.deleteService(service)
    suspend fun deleteServiceById(id: Int) = appDao.deleteServiceById(id)

    suspend fun getClientById(id: Int): Client? = appDao.getClientById(id)
    suspend fun insertClient(client: Client): Long = appDao.insertClient(client)
    suspend fun deleteClient(client: Client) = appDao.deleteClient(client)
    suspend fun deleteClientById(id: Int) = appDao.deleteClientById(id)

    suspend fun getTemplateByTipo(tipo: String): MessageTemplate? = appDao.getTemplateByTipo(tipo)
    suspend fun insertTemplate(template: MessageTemplate) = appDao.insertTemplate(template)

    suspend fun insertTransaction(transaction: FinanceTransaction): Long = appDao.insertTransaction(transaction)
    suspend fun deleteTransaction(transaction: FinanceTransaction) = appDao.deleteTransaction(transaction)
    suspend fun clearAllTransactions() = appDao.clearAllTransactions()

    suspend fun getWhatsAppConfig(): WhatsAppConfig {
        return appDao.getWhatsAppConfig() ?: WhatsAppConfig(
            id = 1,
            apiType = "intent",
            apiUrl = "",
            apiMethod = "POST",
            payloadJson = """{"phone": "{number}", "message": "{message}"}""",
            headersJson = """{"Content-Type": "application/json"}"""
        )
    }

    suspend fun insertWhatsAppConfig(config: WhatsAppConfig) = appDao.insertWhatsAppConfig(config)

    // Checks and pre-populates default database records if they are missing
    suspend fun prepopulateDefaults() {
        // Prepopulate message templates
        val defaultTemplates = listOf(
            MessageTemplate(
                tipo = "vencimento",
                template = "Olá {nome}, sua mensalidade do serviço {servico} vence no dia {data_validade}. Valor: R$ {valor}. Solicitamos realizar o pagamento para garantir a continuidade do serviço."
            ),
            MessageTemplate(
                tipo = "confirmacao_renovacao",
                template = "Olá {nome}! Confirmamos o recebimento e a renovação de sua assinatura do serviço {servico} até dia {data_validade}. Obrigado pela parceria!"
            ),
            MessageTemplate(
                tipo = "dados_cliente",
                template = "Olá {nome}! Seguem os dados do seu serviço:\n\nServiço: {servico}\nUsuário: {usuario}\nSenha: {senha}\nValor: R$ {valor}\nValidade: {data_validade}\n\nAgradecemos a preferência!"
            )
        )

        for (tpl in defaultTemplates) {
            if (appDao.getTemplateByTipo(tpl.tipo) == null) {
                appDao.insertTemplate(tpl)
            }
        }

        // Prepopulate WhatsApp Configuration if missing
        if (appDao.getWhatsAppConfig() == null) {
            appDao.insertWhatsAppConfig(
                WhatsAppConfig(
                    id = 1,
                    apiType = "intent",
                    apiUrl = "",
                    apiMethod = "POST",
                    payloadJson = """{"phone": "{number}", "message": "{message}"}""",
                    headersJson = """{"Content-Type": "application/json"}"""
                )
            )
        }
    }

    // Export entire database contents as polished JSON to output stream
    suspend fun exportBackup(outputStream: OutputStream): Boolean {
        return try {
            val backupObj = JSONObject()

            // 1. Services
            val servicesList = allServices.first()
            val servicesArray = JSONArray()
            servicesList.forEach { s ->
                val sObj = JSONObject()
                sObj.put("id", s.id)
                sObj.put("nome", s.nome)
                sObj.put("precoCompra", s.precoCompra)
                sObj.put("precoVenda", s.precoVenda)
                servicesArray.put(sObj)
            }
            backupObj.put("services", servicesArray)

            // 2. Clients
            val clientsList = allClients.first()
            val clientsArray = JSONArray()
            clientsList.forEach { c ->
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("nome", c.nome)
                cObj.put("usuario", c.usuario)
                cObj.put("senha", c.senha)
                cObj.put("whatsapp", c.whatsapp)
                cObj.put("dataAdesao", c.dataAdesao)
                cObj.put("dataVencimento", c.dataVencimento)
                cObj.put("servicoId", c.servicoId)
                cObj.put("valor", c.valor)
                cObj.put("observacao", c.observacao)
                clientsArray.put(cObj)
            }
            backupObj.put("clients", clientsArray)

            // 3. Templates
            val templatesList = allTemplates.first()
            val templatesArray = JSONArray()
            templatesList.forEach { t ->
                val tObj = JSONObject()
                tObj.put("tipo", t.tipo)
                tObj.put("template", t.template)
                templatesArray.put(tObj)
            }
            backupObj.put("templates", templatesArray)

            // 4. Transactions
            val transactionsList = allTransactions.first()
            val transactionsArray = JSONArray()
            transactionsList.forEach { tx ->
                val txObj = JSONObject()
                txObj.put("id", tx.id)
                txObj.put("clienteId", tx.clienteId)
                txObj.put("clienteNome", tx.clienteNome)
                txObj.put("servicoId", tx.servicoId)
                txObj.put("servicoNome", tx.servicoNome)
                txObj.put("dataTransacao", tx.dataTransacao)
                txObj.put("meses", tx.meses)
                txObj.put("valorRecebido", tx.valorRecebido)
                txObj.put("custoFaturado", tx.custoFaturado)
                txObj.put("lucroLiquido", tx.lucroLiquido)
                transactionsArray.put(txObj)
            }
            backupObj.put("transactions", transactionsArray)

            // 5. WhatsApp API Settings
            val config = getWhatsAppConfig()
            val configObj = JSONObject()
            configObj.put("apiType", config.apiType)
            configObj.put("apiUrl", config.apiUrl)
            configObj.put("apiMethod", config.apiMethod)
            configObj.put("payloadJson", config.payloadJson)
            configObj.put("headersJson", config.headersJson)
            backupObj.put("whatsapp_config", configObj)

            // Write JSON string
            val jsonString = backupObj.toString(4)
            outputStream.use { out ->
                out.write(jsonString.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Import and replace / merge entire database from file input stream
    suspend fun importBackup(inputStream: InputStream): Boolean {
        return try {
            val jsonString = inputStream.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }
            val backupObj = JSONObject(jsonString)

            // Import Services
            if (backupObj.has("services")) {
                val servicesArray = backupObj.getJSONArray("services")
                for (i in 0 until servicesArray.length()) {
                    val sObj = servicesArray.getJSONObject(i)
                    val s = Service(
                        id = sObj.optInt("id", 0),
                        nome = sObj.getString("nome"),
                        precoCompra = sObj.getDouble("precoCompra"),
                        precoVenda = sObj.getDouble("precoVenda")
                    )
                    appDao.insertService(s)
                }
            }

            // Import Clients
            if (backupObj.has("clients")) {
                val clientsArray = backupObj.getJSONArray("clients")
                for (i in 0 until clientsArray.length()) {
                    val cObj = clientsArray.getJSONObject(i)
                    val c = Client(
                        id = cObj.optInt("id", 0),
                        nome = cObj.getString("nome"),
                        usuario = cObj.getString("usuario"),
                        senha = cObj.getString("senha"),
                        whatsapp = cObj.getString("whatsapp"),
                        dataAdesao = cObj.getString("dataAdesao"),
                        dataVencimento = cObj.getString("dataVencimento"),
                        servicoId = cObj.getInt("servicoId"),
                        valor = cObj.getDouble("valor"),
                        observacao = cObj.optString("observacao", "")
                    )
                    appDao.insertClient(c)
                }
            }

            // Import Templates
            if (backupObj.has("templates")) {
                val templatesArray = backupObj.getJSONArray("templates")
                for (i in 0 until templatesArray.length()) {
                    val tObj = templatesArray.getJSONObject(i)
                    val t = MessageTemplate(
                        tipo = tObj.getString("tipo"),
                        template = tObj.getString("template")
                    )
                    appDao.insertTemplate(t)
                }
            }

            // Import Transactions
            if (backupObj.has("transactions")) {
                val transactionsArray = backupObj.getJSONArray("transactions")
                for (i in 0 until transactionsArray.length()) {
                    val txObj = transactionsArray.getJSONObject(i)
                    val tx = FinanceTransaction(
                        id = txObj.optInt("id", 0),
                        clienteId = txObj.getInt("clienteId"),
                        clienteNome = txObj.getString("clienteNome"),
                        servicoId = txObj.getInt("servicoId"),
                        servicoNome = txObj.getString("servicoNome"),
                        dataTransacao = txObj.getString("dataTransacao"),
                        meses = txObj.getInt("meses"),
                        valorRecebido = txObj.getDouble("valorRecebido"),
                        custoFaturado = txObj.getDouble("custoFaturado"),
                        lucroLiquido = txObj.getDouble("lucroLiquido")
                    )
                    appDao.insertTransaction(tx)
                }
            }

            // Import WhatsApp Settings
            if (backupObj.has("whatsapp_config")) {
                val cObj = backupObj.getJSONObject("whatsapp_config")
                val config = WhatsAppConfig(
                    id = 1,
                    apiType = cObj.optString("apiType", "intent"),
                    apiUrl = cObj.optString("apiUrl", ""),
                    apiMethod = cObj.optString("apiMethod", "POST"),
                    payloadJson = cObj.optString("payloadJson", """{"phone": "{number}", "message": "{message}"}"""),
                    headersJson = cObj.optString("headersJson", """{"Content-Type": "application/json"}""")
                )
                appDao.insertWhatsAppConfig(config)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

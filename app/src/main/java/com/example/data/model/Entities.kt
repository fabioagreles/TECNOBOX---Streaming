package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "services")
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val precoCompra: Double, // Preço de custo
    val precoVenda: Double   // Preço de venda cobrado
) : Serializable

@Entity(tableName = "clients")
data class Client(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val usuario: String,
    val senha: String,
    val whatsapp: String,
    val dataAdesao: String, // Formato "yyyy-MM-dd"
    val dataVencimento: String, // Formato "yyyy-MM-dd"
    val servicoId: Int, // ID do serviço contratado
    val valor: Double, // Valor cobrado (copiado do serviço por padrão)
    val observacao: String
) : Serializable

@Entity(tableName = "message_templates")
data class MessageTemplate(
    @PrimaryKey val tipo: String, // "vencimento", "confirmacao_renovacao", "dados_cliente"
    val template: String
) : Serializable

@Entity(tableName = "finance_transactions")
data class FinanceTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clienteId: Int,
    val clienteNome: String,
    val servicoId: Int,
    val servicoNome: String,
    val dataTransacao: String, // Formato "yyyy-MM-dd"
    val meses: Int,
    val valorRecebido: Double,
    val custoFaturado: Double,
    val lucroLiquido: Double
) : Serializable

@Entity(tableName = "whatsapp_config")
data class WhatsAppConfig(
    @PrimaryKey val id: Int = 1,
    val apiType: String = "intent", // "intent" ou "api"
    val apiUrl: String = "",
    val apiMethod: String = "POST", // "POST" ou "GET"
    val payloadJson: String = """{"phone": "{number}", "message": "{message}"}""",
    val headersJson: String = """{"Content-Type": "application/json"}"""
) : Serializable

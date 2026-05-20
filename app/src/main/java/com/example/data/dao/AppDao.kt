package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // Services
    @Query("SELECT * FROM services ORDER BY nome ASC")
    fun getAllServices(): Flow<List<Service>>

    @Query("SELECT * FROM services WHERE id = :id LIMIT 1")
    suspend fun getServiceById(id: Int): Service?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: Service): Long

    @Delete
    suspend fun deleteService(service: Service)

    @Query("DELETE FROM services WHERE id = :id")
    suspend fun deleteServiceById(id: Int)


    // Clients
    @Query("SELECT * FROM clients ORDER BY nome ASC")
    fun getAllClients(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getClientById(id: Int): Client?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: Client): Long

    @Delete
    suspend fun deleteClient(client: Client)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClientById(id: Int)


    // Message Templates
    @Query("SELECT * FROM message_templates WHERE tipo = :tipo LIMIT 1")
    suspend fun getTemplateByTipo(tipo: String): MessageTemplate?

    @Query("SELECT * FROM message_templates")
    fun getAllTemplates(): Flow<List<MessageTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: MessageTemplate)


    // Finance Transactions
    @Query("SELECT * FROM finance_transactions ORDER BY dataTransacao DESC, id DESC")
    fun getAllTransactions(): Flow<List<FinanceTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: FinanceTransaction): Long

    @Delete
    suspend fun deleteTransaction(transaction: FinanceTransaction)

    @Query("DELETE FROM finance_transactions")
    suspend fun clearAllTransactions()


    // WhatsApp Configurations
    @Query("SELECT * FROM whatsapp_config WHERE id = 1 LIMIT 1")
    suspend fun getWhatsAppConfig(): WhatsAppConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhatsAppConfig(config: WhatsAppConfig)
}

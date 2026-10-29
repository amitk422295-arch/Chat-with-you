package com.chatarena.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // 1. Naya message save (REPLACE use karne se duplicate nahi honge)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    // 2. Chat ke saare messages load karne ke liye
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    // 3. Status update by MessageId (Server se aane wale updates ke liye)
    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    // 🔥 NAYA FIX: Status update by ClientMessageId (Jab hum message bhejte hain aur callback aata hai)
    @Query("UPDATE messages SET status = :status WHERE clientMessageId = :clientMessageId")
    suspend fun updateMessageStatusByClientId(clientMessageId: String, status: String)

    // 🔥 NAYA FIX: Server ka messageId aur exact timestamp update karne ke liye (Jab message Sent ho jaye)
    @Query("UPDATE messages SET messageId = :newServerId, createdAt = :serverTime, status = 'sent' WHERE clientMessageId = :clientMessageId")
    suspend fun updateMessageWithServerDetails(clientMessageId: String, newServerId: String, serverTime: Long)

    // 🔥 NAYA FIX: Saare unread messages ko ek sath Read/Delivered mark karne ke liye
    @Query("UPDATE messages SET status = :status WHERE chatId = :chatId AND status != :status")
    suspend fun updateAllMessagesStatusInChat(chatId: String, status: String)

    // Chat delete karne ke liye
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChat(chatId: String)

    // Inbox (Recent Chats) ke liye
    @Query("""
        SELECT m1.* FROM messages m1 
        INNER JOIN (
            SELECT chatId, MAX(createdAt) as maxTime 
            FROM messages 
            GROUP BY chatId
        ) m2 ON m1.chatId = m2.chatId AND m1.createdAt = m2.maxTime 
        ORDER BY m1.createdAt DESC
    """)
    fun getRecentChats(): Flow<List<MessageEntity>>
}

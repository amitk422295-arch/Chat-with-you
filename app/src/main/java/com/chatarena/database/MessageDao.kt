package com.chatarena.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    // 1. Naya message save karne ke liye (Agar pehle se hai toh update kar dega)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    // 2. Kisi specific chat ke saare messages load karne ke liye (Flow se auto-update hoga)
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    // 3. Message ka status update karne ke liye (Pending se Sent ya Failed karne ke liye)
    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    // 4. Chat delete karne ke liye (Baad me kaam aayega)
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChat(chatId: String)

    // 5. NAYA: Home screen par Inbox (Recent Chats) dikhane ke liye
    // Yeh query har chatId ka sabse aakhiri (latest timestamp wala) message fetch karegi
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

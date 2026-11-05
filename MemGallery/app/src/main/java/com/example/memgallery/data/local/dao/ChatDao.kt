package com.example.memgallery.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.memgallery.data.local.entity.ChatEntity
import com.example.memgallery.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Chat Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Delete
    suspend fun deleteChat(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY timestamp DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: Int): ChatEntity?

    @Query("SELECT * FROM chats WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchChats(query: String): Flow<List<ChatEntity>>

    // Message Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: Int): Flow<List<ChatMessageEntity>>

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Int)

    @Query("DELETE FROM chats WHERE id IN (:ids)")
    suspend fun deleteChatsByIds(ids: List<Int>)

    // --- Agent Bridge (read-only; the in-app assistant owns writes) ---

    @Query("SELECT * FROM chats ORDER BY timestamp DESC")
    suspend fun getAllChatsOnce(): List<ChatEntity>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChatOnce(chatId: Int): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE chatId = :chatId")
    suspend fun countMessages(chatId: Int): Int
}

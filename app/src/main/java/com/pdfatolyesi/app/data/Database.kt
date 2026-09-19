package com.pdfatolyesi.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey val id: String,
    val name: String,
    val bytes: Long,
    val pages: Int,
    val addedAt: Long,
    val openedAt: Long,
    val favorite: Boolean = false,
    val lastPage: Int = 0
)
@Entity(tableName = "history")
data class History(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String, val action: String, val timestamp: Long)
@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY openedAt DESC") fun observe(): Flow<List<Document>>
    @Query("SELECT * FROM documents WHERE id = :id") suspend fun get(id: String): Document?
    @Insert suspend fun insert(document: Document)
    @Query("UPDATE documents SET favorite = NOT favorite WHERE id = :id") suspend fun favorite(id: String)
    @Query("UPDATE documents SET name = :name WHERE id = :id") suspend fun rename(id: String, name: String)
    @Query("UPDATE documents SET openedAt = :time, pages = :pages WHERE id = :id")
    suspend fun opened(id: String, time: Long, pages: Int)
    @Query("UPDATE documents SET lastPage = :page WHERE id = :id") suspend fun position(id: String, page: Int)
    @Query("DELETE FROM documents WHERE id = :id") suspend fun delete(id: String)
    @Insert suspend fun record(history: History)
    @Query("DELETE FROM history WHERE documentId = :id") suspend fun deleteHistory(id: String)
    @Query("SELECT id FROM documents") suspend fun ids(): List<String>
}
@Database(entities = [Document::class, History::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun documents(): DocumentDao }

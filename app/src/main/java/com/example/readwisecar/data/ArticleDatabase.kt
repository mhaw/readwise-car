package com.example.readwisecar.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val category: String,
    val location: String,
    val wordCount: Int,
    val readingProgress: Float,
    val imageUrl: String?,
    val htmlContent: String?,
    val updatedAt: String,
    val lastPlayedChunk: Int = 0,
    val totalChunks: Int = 0
)

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE location = :location ORDER BY updatedAt DESC")
    suspend fun getByLocation(location: String): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE htmlContent IS NULL ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getMissingHtml(limit: Int): List<ArticleEntity>

    @Query("SELECT COUNT(*) FROM articles")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE htmlContent IS NOT NULL")
    fun observeCachedHtmlCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ArticleEntity>)

    @Query("UPDATE articles SET htmlContent = :html WHERE id = :id")
    suspend fun updateHtml(id: String, html: String)

    @Query("UPDATE articles SET lastPlayedChunk = :chunk, totalChunks = :total, readingProgress = :progress WHERE id = :id")
    suspend fun updateLocalProgress(id: String, chunk: Int, total: Int, progress: Float)

    @Query("UPDATE articles SET location = :location WHERE id = :id")
    suspend fun updateLocation(id: String, location: String)

    @Query("DELETE FROM articles WHERE location = 'archive'")
    suspend fun deleteArchived()
}

@Database(entities = [ArticleEntity::class], version = 1, exportSchema = false)
abstract class ArticleDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        fun create(context: Context): ArticleDatabase = Room.databaseBuilder(
            context.applicationContext,
            ArticleDatabase::class.java,
            "readwise_car.db"
        ).build()
    }
}

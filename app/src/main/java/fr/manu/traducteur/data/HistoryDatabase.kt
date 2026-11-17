package fr.manu.traducteur.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translations ORDER BY createdAt DESC")
    fun all(): Flow<List<Translation>>

    @Query(
        "SELECT * FROM translations WHERE sourceText LIKE '%' || :q || '%' " +
            "OR translatedText LIKE '%' || :q || '%' ORDER BY createdAt DESC"
    )
    fun search(q: String): Flow<List<Translation>>

    @Insert
    suspend fun insert(t: Translation): Long

    @Update
    suspend fun update(t: Translation)

    @Delete
    suspend fun delete(t: Translation)

    @Query("DELETE FROM translations")
    suspend fun clear()
}

@Database(entities = [Translation::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun dao(): TranslationDao

    companion object {
        @Volatile private var instance: HistoryDatabase? = null

        fun get(context: Context): HistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "historique.db",
                ).build().also { instance = it }
            }
    }
}

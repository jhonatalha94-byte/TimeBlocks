package ma.jmal.timeblocks

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A scheduled task. For weekly tasks, [daysMask] has bit (Calendar.DAY_OF_WEEK - 1) set per day. */
@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val repeat: Int = REPEAT_ONCE,
    val daysMask: Int = 0,
    val enabled: Boolean = true,
) {
    companion object {
        const val REPEAT_ONCE = 0
        const val REPEAT_DAILY = 1
        const val REPEAT_WEEKLY = 2
    }
}

/** One finished block of work. */
@Entity(tableName = "log")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val plannedSec: Int,
    val actualSec: Int,
    val startedAt: Long,
    val finishedAt: Long,
)

@Dao
interface AppDao {
    @Query("SELECT * FROM tasks ORDER BY hour, minute")
    fun tasksFlow(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE enabled = 1")
    suspend fun enabledTasks(): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun task(id: Long): TaskItem?

    @Insert
    suspend fun insert(t: TaskItem): Long

    @Update
    suspend fun update(t: TaskItem)

    @Delete
    suspend fun delete(t: TaskItem)

    @Insert
    suspend fun insertLog(e: LogEntry)

    @Query("SELECT * FROM log WHERE finishedAt >= :from AND finishedAt < :to ORDER BY finishedAt DESC")
    fun logFlow(from: Long, to: Long): Flow<List<LogEntry>>

    @Delete
    suspend fun deleteLog(e: LogEntry)
}

@Database(entities = [TaskItem::class, LogEntry::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var inst: AppDb? = null

        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "timeblocks.db")
                .build().also { inst = it }
        }
    }
}

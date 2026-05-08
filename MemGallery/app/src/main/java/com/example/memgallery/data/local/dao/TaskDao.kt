package com.example.memgallery.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.memgallery.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isApproved = 1 ORDER BY dueDate ASC, dueTime ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND isApproved = 1 ORDER BY dueDate ASC, dueTime ASC")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueDate = :date AND isApproved = 1 ORDER BY isCompleted ASC, dueTime ASC")
    fun getTasksByDate(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueDate >= :date AND isApproved = 1 ORDER BY isCompleted ASC, dueDate ASC, dueTime ASC")
    fun getUpcomingTasksFromDate(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueDate < :date AND isCompleted = 0 AND isApproved = 1 ORDER BY dueDate ASC")
    fun getOverdueTasks(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isApproved = 0 ORDER BY creationTimestamp DESC")
    fun getUnapprovedTasks(): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET isApproved = 1 WHERE id IN (:ids)")
    suspend fun approveTasks(ids: List<Int>)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteTasksByIds(ids: List<Int>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE memoryId = :memoryId")
    suspend fun deleteTasksByMemoryId(memoryId: Int)
}

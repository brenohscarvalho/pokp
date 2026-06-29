package com.pokp.pokedex.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val membersJson: String,
    val updatedAt: Long,
)

@Dao
interface TeamDao {

    @Query("SELECT * FROM teams ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun getById(id: Long): TeamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(team: TeamEntity): Long

    @Query("DELETE FROM teams WHERE id = :id")
    suspend fun deleteById(id: Long)
}

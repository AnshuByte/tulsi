package com.tulsicare.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tulsicare.app.core.database.entity.FamilyGroupEntity
import com.tulsicare.app.core.database.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FamilyGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: FamilyGroupEntity)

    @Query("SELECT * FROM family_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): FamilyGroupEntity?

    @Query("SELECT * FROM family_groups WHERE id = :groupId")
    fun observeGroup(groupId: String): Flow<FamilyGroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY joinedAt ASC")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId ORDER BY joinedAt ASC")
    suspend fun getMembers(groupId: String): List<GroupMemberEntity>

    @Query("UPDATE group_members SET displayName = :displayName WHERE userId = :userId AND groupId = :groupId")
    suspend fun updateMemberDisplayName(userId: String, groupId: String, displayName: String)

    @Query("DELETE FROM group_members WHERE userId = :userId AND groupId = :groupId")
    suspend fun deleteMember(userId: String, groupId: String)

    @Query("DELETE FROM family_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: String)
}

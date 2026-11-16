package com.tulsicare.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tulsicare.app.core.database.converter.Converters
import com.tulsicare.app.core.database.dao.AlarmRecordDao
import com.tulsicare.app.core.database.dao.DoseEventDao
import com.tulsicare.app.core.database.dao.FamilyGroupDao
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.dao.ScheduleDao
import com.tulsicare.app.core.database.entity.AlarmRecordEntity
import com.tulsicare.app.core.database.entity.DoseEventEntity
import com.tulsicare.app.core.database.entity.FamilyGroupEntity
import com.tulsicare.app.core.database.entity.GroupMemberEntity
import com.tulsicare.app.core.database.entity.MedicineEntity
import com.tulsicare.app.core.database.entity.ScheduleEntity

@Database(
    entities = [
        MedicineEntity::class,
        ScheduleEntity::class,
        DoseEventEntity::class,
        FamilyGroupEntity::class,
        GroupMemberEntity::class,
        AlarmRecordEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TulsiDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseEventDao(): DoseEventDao
    abstract fun familyGroupDao(): FamilyGroupDao
    abstract fun alarmRecordDao(): AlarmRecordDao

    companion object {
        const val DATABASE_NAME = "tulsi.db"
    }
}

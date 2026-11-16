package com.tulsicare.app.core.di

import android.content.Context
import androidx.room.Room
import com.tulsicare.app.core.database.TulsiDatabase
import com.tulsicare.app.core.database.dao.AlarmRecordDao
import com.tulsicare.app.core.database.dao.DoseEventDao
import com.tulsicare.app.core.database.dao.FamilyGroupDao
import com.tulsicare.app.core.database.dao.MedicineDao
import com.tulsicare.app.core.database.dao.ScheduleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): TulsiDatabase {
        return Room.databaseBuilder(
            context,
            TulsiDatabase::class.java,
            TulsiDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMedicineDao(db: TulsiDatabase): MedicineDao = db.medicineDao()

    @Provides
    fun provideScheduleDao(db: TulsiDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideDoseEventDao(db: TulsiDatabase): DoseEventDao = db.doseEventDao()

    @Provides
    fun provideFamilyGroupDao(db: TulsiDatabase): FamilyGroupDao = db.familyGroupDao()

    @Provides
    fun provideAlarmRecordDao(db: TulsiDatabase): AlarmRecordDao = db.alarmRecordDao()
}

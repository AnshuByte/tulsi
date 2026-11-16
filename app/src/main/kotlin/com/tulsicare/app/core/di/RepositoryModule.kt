package com.tulsicare.app.core.di

import com.tulsicare.app.core.data.repository.AuthRepository
import com.tulsicare.app.core.data.repository.AuthRepositoryImpl
import com.tulsicare.app.core.data.repository.DoseEventRepository
import com.tulsicare.app.core.data.repository.DoseEventRepositoryImpl
import com.tulsicare.app.core.data.repository.FamilyGroupRepository
import com.tulsicare.app.core.data.repository.FamilyGroupRepositoryImpl
import com.tulsicare.app.core.data.repository.MedicineRepository
import com.tulsicare.app.core.data.repository.MedicineRepositoryImpl
import com.tulsicare.app.core.data.repository.ScheduleRepository
import com.tulsicare.app.core.data.repository.ScheduleRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMedicineRepository(
        impl: MedicineRepositoryImpl
    ): MedicineRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(
        impl: ScheduleRepositoryImpl
    ): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindDoseEventRepository(
        impl: DoseEventRepositoryImpl
    ): DoseEventRepository

    @Binds
    @Singleton
    abstract fun bindFamilyGroupRepository(
        impl: FamilyGroupRepositoryImpl
    ): FamilyGroupRepository
}

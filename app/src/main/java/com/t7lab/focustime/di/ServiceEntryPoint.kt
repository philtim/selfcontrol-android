package com.t7lab.focustime.di

import com.t7lab.focustime.data.db.SessionDao
import com.t7lab.focustime.data.preferences.PreferencesManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceEntryPoint {
    fun sessionDao(): SessionDao
    fun preferencesManager(): PreferencesManager
}

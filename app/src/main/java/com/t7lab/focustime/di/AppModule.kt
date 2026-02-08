package com.t7lab.focustime.di

import android.content.Context
import androidx.room.Room
import com.t7lab.focustime.data.db.BlockedItemDao
import com.t7lab.focustime.data.db.FocusTimeDatabase
import com.t7lab.focustime.data.db.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FocusTimeDatabase {
        return Room.databaseBuilder(
            context,
            FocusTimeDatabase::class.java,
            "focustime.db"
        ).build()
    }

    @Provides
    fun provideBlockedItemDao(database: FocusTimeDatabase): BlockedItemDao {
        return database.blockedItemDao()
    }

    @Provides
    fun provideSessionDao(database: FocusTimeDatabase): SessionDao {
        return database.sessionDao()
    }
}

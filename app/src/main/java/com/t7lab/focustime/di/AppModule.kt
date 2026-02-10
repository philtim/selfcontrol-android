package com.t7lab.focustime.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add index on sessions.isActive for faster active session lookups
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_isActive` ON `sessions` (`isActive`)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FocusTimeDatabase {
        return Room.databaseBuilder(
            context,
            FocusTimeDatabase::class.java,
            "focustime.db"
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideBlockedItemDao(database: FocusTimeDatabase): BlockedItemDao {
        return database.blockedItemDao()
    }

    @Provides
    @Singleton
    fun provideSessionDao(database: FocusTimeDatabase): SessionDao {
        return database.sessionDao()
    }
}

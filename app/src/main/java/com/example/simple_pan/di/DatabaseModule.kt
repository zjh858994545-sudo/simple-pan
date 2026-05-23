package com.example.simple_pan.di

import android.content.Context
import androidx.room.Room
import com.example.simple_pan.data.local.AppDatabase
import com.example.simple_pan.data.local.dao.FileDao
import com.example.simple_pan.data.local.dao.OpenHistoryDao
import com.example.simple_pan.data.local.dao.ShareDao
import com.example.simple_pan.data.local.dao.ShareFileSnapshotDao
import com.example.simple_pan.data.local.dao.TransferHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// [语法] object 是 Kotlin 单例，类似 Java 的 static 工具类实例；Hilt 会读取里面的 @Provides 方法。
// [设计] 为什么这样写：数据库和 DAO 的创建集中交给 Hilt，Repository 不手动 new，后续测试或替换数据库更清晰。
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // [语法] @ApplicationContext 标在函数参数上，类似 Java 方法参数注解，用来告诉 Hilt 这里要注入 Application 级 Context。
    // [设计] 为什么这样写：Room Database 需要 Application Context，不能持有 Activity Context，否则会有生命周期泄漏风险。
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }

    // [设计] 为什么这样写：DAO 从同一个 AppDatabase 提供，保证所有 Repository 操作的是同一个本地数据库实例。
    @Provides
    fun provideFileDao(database: AppDatabase): FileDao = database.fileDao()

    @Provides
    fun provideOpenHistoryDao(database: AppDatabase): OpenHistoryDao = database.openHistoryDao()

    @Provides
    fun provideTransferHistoryDao(database: AppDatabase): TransferHistoryDao = database.transferHistoryDao()

    @Provides
    fun provideShareDao(database: AppDatabase): ShareDao = database.shareDao()

    @Provides
    fun provideShareFileSnapshotDao(database: AppDatabase): ShareFileSnapshotDao = database.shareFileSnapshotDao()
}

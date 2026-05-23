package com.example.simple_pan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.simple_pan.data.local.dao.FileDao
import com.example.simple_pan.data.local.dao.OpenHistoryDao
import com.example.simple_pan.data.local.dao.ShareDao
import com.example.simple_pan.data.local.dao.ShareFileSnapshotDao
import com.example.simple_pan.data.local.dao.TransferHistoryDao
import com.example.simple_pan.data.local.entity.FileEntity
import com.example.simple_pan.data.local.entity.OpenHistoryEntity
import com.example.simple_pan.data.local.entity.ShareEntity
import com.example.simple_pan.data.local.entity.ShareFileSnapshotEntity
import com.example.simple_pan.data.local.entity.TransferHistoryEntity

@Database(
    entities = [
        FileEntity::class,
        OpenHistoryEntity::class,
        TransferHistoryEntity::class,
        ShareEntity::class,
        ShareFileSnapshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao

    abstract fun openHistoryDao(): OpenHistoryDao

    abstract fun transferHistoryDao(): TransferHistoryDao

    abstract fun shareDao(): ShareDao

    abstract fun shareFileSnapshotDao(): ShareFileSnapshotDao

    companion object {
        const val DATABASE_NAME = "simple_pan.db"
    }
}

package com.narro.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class, DocumentSegmentEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NarroDatabase : RoomDatabase() {
    abstract fun narroDao(): NarroDao
}

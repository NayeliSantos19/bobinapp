package com.bobinapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bobinapp.data.local.dao.AnimalDao
import com.bobinapp.data.local.dao.EventoDao
import com.bobinapp.data.local.entity.AnimalEntity
import com.bobinapp.data.local.entity.EventoEntity

@Database(entities = [AnimalEntity::class, EventoEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class BobinappDatabase : RoomDatabase() {
    abstract fun animalDao(): AnimalDao
    abstract fun eventoDao(): EventoDao

    companion object {
        /** v2: fotos de cada animal. Conserva todos los datos de quien ya tenía la app instalada. */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE animales ADD COLUMN fotoActualizadaEn INTEGER")
                db.execSQL("ALTER TABLE animales ADD COLUMN fotoLocalEn INTEGER")
                db.execSQL("ALTER TABLE animales ADD COLUMN fotoPendiente INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun crear(context: Context): BobinappDatabase =
            Room.databaseBuilder(context, BobinappDatabase::class.java, "bobinapp.db")
                .addMigrations(MIGRACION_1_2)
                .build()
    }
}

package com.monolith

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    @Query("SELECT * FROM user_profile WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)
}

@Dao
interface VaultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEncryptedFile(file: EncryptedFileEntity)
    @Query("SELECT * FROM encrypted_files ORDER BY dateEncrypted DESC")
    suspend fun getAllEncryptedFiles(): List<EncryptedFileEntity>
    @Query("DELETE FROM encrypted_files WHERE id = :id")
    suspend fun deleteEncryptedFileById(id: String)
    @Query("SELECT COUNT(*) FROM encrypted_files")
    suspend fun getFilesCount(): Int
    @Query("SELECT SUM(fileSize) FROM encrypted_files")
    suspend fun getTotalEncryptedSize(): Long?
    @Query("SELECT COUNT(*) FROM encrypted_files WHERE isSteganography = 1")
    suspend fun getSteganographyCount(): Int
    @Query("SELECT * FROM encrypted_files WHERE originalName LIKE '%' || :query || '%'")
    suspend fun searchEncryptedFiles(query: String): List<EncryptedFileEntity>
}

@Dao
interface VirtualAlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: VirtualAlbumEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMediaToAlbum(crossRef: AlbumMediaCrossRef)
    @Query("SELECT * FROM virtual_albums WHERE isVault = 0 ORDER BY sortOrder ASC")
    suspend fun getAllAlbums(): List<VirtualAlbumEntity>
    @Query("SELECT mediaUri FROM album_media_cross_ref WHERE albumId = :albumId")
    suspend fun getMediaForAlbum(albumId: String): List<String>
    @Query("DELETE FROM virtual_albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `encrypted_files` (`id` TEXT NOT NULL, `originalName` TEXT NOT NULL, `encryptedPath` TEXT NOT NULL, `uniquePasswordEncrypted` TEXT NOT NULL, `isSteganography` INTEGER NOT NULL, `carrierImagePath` TEXT, `fileSize` INTEGER NOT NULL, `dateEncrypted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `virtual_albums` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `coverUri` TEXT, `createdAt` INTEGER NOT NULL, `isVault` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `album_media_cross_ref` (`albumId` TEXT NOT NULL, `mediaUri` TEXT NOT NULL, PRIMARY KEY(`albumId`, `mediaUri`), FOREIGN KEY(`albumId`) REFERENCES `virtual_albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_album_media_cross_ref_albumId` ON `album_media_cross_ref` (`albumId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_album_media_cross_ref_mediaUri` ON `album_media_cross_ref` (`mediaUri`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `virtual_albums` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `coverUri` TEXT, `createdAt` INTEGER NOT NULL, `isVault` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        database.execSQL("CREATE TABLE IF NOT EXISTS `album_media_cross_ref` (`albumId` TEXT NOT NULL, `mediaUri` TEXT NOT NULL, PRIMARY KEY(`albumId`, `mediaUri`), FOREIGN KEY(`albumId`) REFERENCES `virtual_albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_album_media_cross_ref_albumId` ON `album_media_cross_ref` (`albumId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_album_media_cross_ref_mediaUri` ON `album_media_cross_ref` (`mediaUri`)")
    }
}

@Database(
    entities = [
        UserEntity::class, 
        MessageEntity::class, 
        EncryptedFileEntity::class, 
        VirtualAlbumEntity::class,
        AlbumMediaCrossRef::class
    ], 
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun vaultDao(): VaultDao
    abstract fun virtualAlbumDao(): VirtualAlbumDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbPassphrase = getOrCreateDbPassphrase(context)
                
                // استخدام المكتبة الجديدة
                val factory = SupportOpenHelperFactory(dbPassphrase.toByteArray())

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "monolith_database"
                )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun getOrCreateDbPassphrase(context: Context): String {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val securePrefs = EncryptedSharedPreferences.create(
                    context, "DatabaseSecret", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                
                val existingPassphrase: String? = securePrefs.getString("db_passphrase", null)
                if (existingPassphrase != null) {
                    existingPassphrase
                } else {
                    val random = SecureRandom()
                    val bytes = ByteArray(32)
                    random.nextBytes(bytes)
                    val newPassphrase = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    securePrefs.edit().putString("db_passphrase", newPassphrase).apply()
                    newPassphrase
                }
            } catch (e: Exception) {
                // خطة الطوارئ لمنع الانهيار على أجهزة شاومي
                e.printStackTrace()
                "Monolith_Fallback_Secure_Key_2024"
            }
        }
    }
}

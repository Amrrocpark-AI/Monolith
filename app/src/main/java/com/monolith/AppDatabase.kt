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
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

// 1. أوامر المستخدم (User DAO)
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM user_profile WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?
}

// 2. أوامر الرسائل (Message DAO)
@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String)
}

// 3. أوامر الخزنة (Vault DAO) مع الإحصائيات والبحث
@Dao
interface VaultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEncryptedFile(file: EncryptedFileEntity)

    @Query("SELECT * FROM encrypted_files ORDER BY dateEncrypted DESC")
    suspend fun getAllEncryptedFiles(): List<EncryptedFileEntity>

    @Query("DELETE FROM encrypted_files WHERE id = :id")
    suspend fun deleteEncryptedFileById(id: String)

    // دوال الإحصائيات للوحة الصحة
    @Query("SELECT COUNT(*) FROM encrypted_files")
    suspend fun getFilesCount(): Int

    @Query("SELECT SUM(fileSize) FROM encrypted_files")
    suspend fun getTotalEncryptedSize(): Long?

    @Query("SELECT COUNT(*) FROM encrypted_files WHERE isSteganography = 1")
    suspend fun getSteganographyCount(): Int

    // البحث
    @Query("SELECT * FROM encrypted_files WHERE originalName LIKE '%' || :query || '%'")
    suspend fun searchEncryptedFiles(query: String): List<EncryptedFileEntity>
}

// 4. الترحيل الآمن
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `encrypted_files` (`id` TEXT NOT NULL, `originalName` TEXT NOT NULL, `encryptedPath` TEXT NOT NULL, `uniquePasswordEncrypted` TEXT NOT NULL, `isSteganography` INTEGER NOT NULL, `carrierImagePath` TEXT, `fileSize` INTEGER NOT NULL, `dateEncrypted` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}

// 5. المصنع السيادي (مع دعم SQLCipher)
@Database(entities = [UserEntity::class, MessageEntity::class, EncryptedFileEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun vaultDao(): VaultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // 1. جلب أو توليد مفتاح تشفير قاعدة البيانات
                val dbPassphrase = getOrCreateDbPassphrase(context)
                
                // 2. إعداد SQLCipher
                val factory = SupportFactory(SQLiteDatabase.getBytes(dbPassphrase.toCharArray()))

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "monolith_database"
                )
                .openHelperFactory(factory) // تفعيل تشفير قاعدة البيانات
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        // دالة توليد المفتاح السري وتخزينه بأمان
        private fun getOrCreateDbPassphrase(context: Context): String {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val securePrefs = EncryptedSharedPreferences.create(
                context, "DatabaseSecret", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            var passphrase = securePrefs.getString("db_passphrase", null)
            if (passphrase == null) {
                val random = SecureRandom()
                val bytes = ByteArray(32)
                random.nextBytes(bytes)
                passphrase = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                securePrefs.edit().putString("db_passphrase", passphrase).apply()
            }
            return passphrase
        }
    }
}

package com.monolith

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

class WallpaperUserIdentityRepository(context: Context) {

    private val database = AppDatabase(context.applicationContext)

    @Synchronized
    fun getOrCreateCurrentUser(
        defaultName: String = "مستخدم MONOLITH"
    ): UserEntity {

        val db = database.writableDatabase
        db.beginTransaction()

        try {
            val existingUser = db.rawQuery(
                """
                SELECT *
                FROM user_profile
                WHERE isCurrentUser = 1
                ORDER BY rowid ASC
                LIMIT 1
                """.trimIndent(),
                null
            ).use { cursor ->

                if (!cursor.moveToFirst()) {
                    null
                } else {
                    UserEntity(
                        id = cursor.getString(
                            cursor.getColumnIndexOrThrow("id")
                        ),
                        name = cursor.getString(
                            cursor.getColumnIndexOrThrow("name")
                        )?.takeIf { it.isNotBlank() } ?: defaultName,
                        bio = cursor.getString(
                            cursor.getColumnIndexOrThrow("bio")
                        ) ?: "",
                        profileImageUri = cursor.getString(
                            cursor.getColumnIndexOrThrow("profileImageUri")
                        ),
                        publicKey = cursor.getString(
                            cursor.getColumnIndexOrThrow("publicKey")
                        ) ?: "",
                        isCurrentUser = true,
                        followersCount = cursor.getInt(
                            cursor.getColumnIndexOrThrow("followersCount")
                        ),
                        followingCount = cursor.getInt(
                            cursor.getColumnIndexOrThrow("followingCount")
                        )
                    )
                }
            }

            val user = existingUser ?: run {
                val newUser = UserEntity(
                    id = UUID.randomUUID().toString(),
                    name = defaultName,
                    isCurrentUser = true
                )

                val values = ContentValues().apply {
                    put("id", newUser.id)
                    put("name", newUser.name)
                    put("bio", newUser.bio)
                    put("profileImageUri", newUser.profileImageUri)
                    put("publicKey", newUser.publicKey)
                    put("isCurrentUser", 1)
                    put("followersCount", 0)
                    put("followingCount", 0)
                }

                db.insertOrThrow("user_profile", null, values)
                newUser
            }

            db.execSQL(
                """
                UPDATE user_profile
                SET isCurrentUser =
                    CASE WHEN id = ? THEN 1 ELSE 0 END
                """.trimIndent(),
                arrayOf(user.id)
            )

            db.setTransactionSuccessful()
            return user.copy(isCurrentUser = true)

        } finally {
            db.endTransaction()
        }
    }
}

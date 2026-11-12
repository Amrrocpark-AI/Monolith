package com.monolith

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["albumId", "mediaUri"],
    foreignKeys = [
        ForeignKey(
            entity = VirtualAlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("albumId"), Index("mediaUri")]
)
data class AlbumMediaCrossRef(
    val albumId: String,
    val mediaUri: String
)

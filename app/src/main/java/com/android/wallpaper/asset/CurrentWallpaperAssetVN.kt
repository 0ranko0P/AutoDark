/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wallpaper.asset

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Point
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.io.InputStream

/**
 * Asset representing the currently-set image wallpaper on N+ devices, including when daily rotation
 * is set with a static wallpaper (but not when daily rotation uses a live wallpaper).
 *
 * [0ranko0P] changes:
 *     0. Rewrite to kotlin.
 *     1. Drop glide key implementation, use Object key directly.
 */
@SuppressLint("MissingPermission")
class CurrentWallpaperAssetVN(context: Context, private val which: Int) : StreamableAsset() {

    private val mManager = WallpaperManager.getInstance(context)
    val id: Int = mManager.getWallpaperId(which)

    init {
        if (id == -1) {
            throw IllegalArgumentException("Invalid id")
        }
    }

    @Throws(IOException::class)
    override fun decodeRawDimensions(): Point {
        if (mDimensions != null) {
            return mDimensions!!
        }

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        mManager.getWallpaperFile(which).use { pfd ->
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
        }

        if (options.outWidth == -1 || options.outHeight == -1) {
            throw IOException("Failed to decode dimensions")
        }
        return Point(options.outWidth, options.outHeight)
    }

    override fun openInputStream(): InputStream  {
        val pfd = mManager.getWallpaperFile(which)
            ?: throw NullPointerException("ParcelFileDescriptor for wallpaper $which, id $id is null, unable to open InputStream.")
        return ParcelFileDescriptor.AutoCloseInputStream(pfd)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return (other is CurrentWallpaperAssetVN && other.id == id)
    }

    override fun hashCode(): Int = id

    override fun toString(): String = "FileDescriptorAsset(id=$id)"
}
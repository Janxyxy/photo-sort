package com.fotosort

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.fotosort.eye.EyeIndex
import com.fotosort.eye.EyeScanner

class FotoSortApp : Application(), ImageLoaderFactory {
    val eyeIndex by lazy { EyeIndex(this) }
    val eyeScanner by lazy { EyeScanner(this, eyeIndex) }

    // lets every AsyncImage show a frame for video URIs
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}

val Context.app: FotoSortApp get() = applicationContext as FotoSortApp

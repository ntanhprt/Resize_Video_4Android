package com.ntanhprt.videoresizer

import android.app.Application
import com.ntanhprt.videoresizer.data.source.MediaStoreDataSource
import com.ntanhprt.videoresizer.data.repository.VideoRepository
import com.ntanhprt.videoresizer.domain.estimation.SizeEstimationEngine
import com.ntanhprt.videoresizer.domain.processor.HybridVideoProcessor

class App : Application() {
    val mediaStoreDataSource by lazy { MediaStoreDataSource(this) }
    val videoRepository by lazy { VideoRepository(mediaStoreDataSource) }
    val sizeEstimationEngine by lazy { SizeEstimationEngine() }
    val hybridVideoProcessor by lazy { HybridVideoProcessor(this) }
}

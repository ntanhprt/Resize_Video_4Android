package com.ntanhprt.videoresizer.data.repository

import com.ntanhprt.videoresizer.data.model.VideoFile
import com.ntanhprt.videoresizer.data.source.MediaStoreDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class VideoRepository(private val dataSource: MediaStoreDataSource) {

    fun getVideos(): Flow<List<VideoFile>> = flow {
        emit(dataSource.queryAllVideos())
    }
}

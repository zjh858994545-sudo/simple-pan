package com.example.simple_pan

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SimplePanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 统一用 Timber 作为调试日志入口，后续 MVI 状态流转、Room 初始化和文件操作问题都能从同一套日志里追踪。
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}

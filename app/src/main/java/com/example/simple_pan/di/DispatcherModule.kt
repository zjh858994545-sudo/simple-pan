package com.example.simple_pan.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// [语法] annotation class 是 Kotlin 声明注解的写法，类似 Java 的 @interface。
// [设计] 为什么这样写：多个 CoroutineDispatcher 类型相同，必须用 Qualifier 区分 IO 和默认调度器，避免 Hilt 不知道注入哪一个。
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

// [语法] object 是 Kotlin 单例，相当于 Java 里私有构造函数加 static INSTANCE。
// [设计] 为什么这样写：DispatcherModule 不需要保存状态，用单例对象声明最轻量，也符合 Hilt Module 的常见写法。
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    // [设计] 为什么这样写：只有 DI 层直接接触 Dispatchers.IO，业务代码统一注入 @IoDispatcher，方便测试时替换调度器。
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

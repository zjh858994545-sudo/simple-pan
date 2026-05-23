package com.example.simple_pan.di

import com.example.simple_pan.data.repository.FileRepositoryImpl
import com.example.simple_pan.data.repository.RecentRepositoryImpl
import com.example.simple_pan.data.repository.ShareRepositoryImpl
import com.example.simple_pan.domain.repository.FileRepository
import com.example.simple_pan.domain.repository.RecentRepository
import com.example.simple_pan.domain.repository.ShareRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// [设计] 为什么这样写：RepositoryModule 只声明“接口绑定到哪个实现”，让 ViewModel/UseCase 依赖 domain 接口，Hilt 在运行时提供 data 实现。
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    // [语法] abstract fun 是抽象函数，类似 Java abstract method；@Binds 告诉 Hilt 用实现类满足接口依赖。
    // [设计] 为什么这样写：FileRepositoryImpl 是唯一知道 Room 和 FakeRemoteDataSource 的地方，上层只认识 FileRepository。
    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds
    @Singleton
    abstract fun bindRecentRepository(impl: RecentRepositoryImpl): RecentRepository

    @Binds
    @Singleton
    abstract fun bindShareRepository(impl: ShareRepositoryImpl): ShareRepository
}

package com.example.agent.di

import com.example.agent.core.audit.AuditLog
import com.example.agent.data.repository.AuditRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the audit interface. A separate module so the audit work does not edit AppModule.
 * [AuditRepository] and [com.example.agent.core.audit.ManagedConfig] are @Singleton with
 * @Inject constructors, so they need no @Provides.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuditModule {
    @Binds
    abstract fun bindAuditLog(impl: AuditRepository): AuditLog
}

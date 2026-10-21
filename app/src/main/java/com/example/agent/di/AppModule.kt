package com.example.agent.di

import android.content.Context
import android.net.ConnectivityManager
import androidx.room.Room
import com.example.agent.automation.AndroidAutomationManager
import com.example.agent.automation.accessibility.AccessibilityController
import com.example.agent.automation.observer.ScreenObserver
import com.example.agent.automation.tools.AskUserTool
import com.example.agent.automation.tools.ClickTool
import com.example.agent.automation.tools.CloseAppTool
import com.example.agent.automation.tools.FindTextTool
import com.example.agent.automation.tools.FinishTool
import com.example.agent.automation.tools.GetNotificationsTool
import com.example.agent.automation.tools.LaunchUrlTool
import com.example.agent.automation.tools.LongClickTool
import com.example.agent.automation.tools.OpenAppTool
import com.example.agent.automation.tools.PressBackTool
import com.example.agent.automation.tools.PressHomeTool
import com.example.agent.automation.tools.ReadScreenTool
import com.example.agent.automation.tools.ScrollTool
import com.example.agent.automation.tools.SwipeTool
import com.example.agent.automation.tools.ToolRegistry
import com.example.agent.automation.tools.TypeTextTool
import com.example.agent.automation.tools.WaitTool
import com.example.agent.core.agent.ActionValidator
import com.example.agent.core.agent.AgentRuntime
import com.example.agent.core.agent.DeviceClock
import com.example.agent.core.agent.DeviceScreen
import com.example.agent.core.agent.StoredTaskJournal
import com.example.agent.core.agent.TouchToStopWatcher
import com.example.agent.core.ai.AIProviderFactory
import com.example.agent.core.ai.local.ModelDownloader
import com.example.agent.core.ai.local.OkHttpRangeSource
import com.example.agent.core.ai.local.OnDeviceModelManager
import com.example.agent.core.ai.provider.OpenAICompatibleProvider
import com.example.agent.core.security.SecureCredentialStore
import com.example.agent.data.local.AgentDatabase
import com.example.agent.data.local.TaskDao
import com.example.agent.data.preferences.MemoryStore
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.data.repository.MemoryRepository
import com.example.agent.data.repository.TaskHistoryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCredentials(@ApplicationContext context: Context): SecureCredentialStore =
        SecureCredentialStore(context)

    @Provides
    @Singleton
    fun provideSettings(
        @ApplicationContext context: Context,
        credentials: SecureCredentialStore,
    ): SettingsRepository = SettingsRepository(context, credentials)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AgentDatabase =
        Room.databaseBuilder(context, AgentDatabase::class.java, "spidey.db")
            .addMigrations(AgentDatabase.MIGRATION_1_2, AgentDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideTaskDao(db: AgentDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun provideHistory(dao: TaskDao): TaskHistoryRepository = TaskHistoryRepository(dao)

    @Provides
    @Singleton
    fun provideMemoryStore(@ApplicationContext context: Context): MemoryStore = MemoryStore(context)

    @Provides
    @Singleton
    fun provideMemory(
        store: MemoryStore,
        settings: SettingsRepository,
    ): MemoryRepository = MemoryRepository(store, settings)

    @Provides
    @Singleton
    fun provideAccessibilityController(): AccessibilityController = AccessibilityController()

    @Provides
    @Singleton
    fun provideAutomation(
        @ApplicationContext context: Context,
        controller: AccessibilityController,
    ): AndroidAutomationManager = AndroidAutomationManager(context, controller)

    @Provides
    @Singleton
    fun provideObserver(automation: AndroidAutomationManager): ScreenObserver = ScreenObserver(automation)

    @Provides
    @Singleton
    fun provideRegistry(
        automation: AndroidAutomationManager,
        observer: ScreenObserver,
    ): ToolRegistry = ToolRegistry(
        listOf(
            OpenAppTool(automation),
            CloseAppTool(automation),
            ReadScreenTool(observer),
            FindTextTool(automation),
            ClickTool(automation),
            LongClickTool(automation),
            TypeTextTool(automation),
            ScrollTool(automation),
            SwipeTool(automation),
            PressBackTool(automation),
            PressHomeTool(automation),
            WaitTool(),
            LaunchUrlTool(automation),
            GetNotificationsTool(automation),
            AskUserTool(),
            FinishTool(),
        )
    )

    @Provides
    @Singleton
    fun provideValidator(registry: ToolRegistry, observer: ScreenObserver): ActionValidator =
        ActionValidator(registry, observer::labelForKey)

    @Provides
    @Singleton
    fun provideProviderFactory(credentials: SecureCredentialStore): AIProviderFactory =
        AIProviderFactory(credentials)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideOnDeviceModels(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): OnDeviceModelManager {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return OnDeviceModelManager(
            downloader = ModelDownloader(
                source = OkHttpRangeSource(OpenAICompatibleProvider.defaultClient()),
                directory = File(context.filesDir, "models"),
            ),
            scope = scope,
            // Unknown counts as metered: better to wait for the user than spend their data.
            isMetered = { connectivity?.isActiveNetworkMetered ?: true },
        )
    }

    @Provides
    @Singleton
    fun provideRuntime(
        models: OnDeviceModelManager,
        settings: SettingsRepository,
        registry: ToolRegistry,
        validator: ActionValidator,
        observer: ScreenObserver,
        automation: AndroidAutomationManager,
        history: TaskHistoryRepository,
        memory: MemoryRepository,
        @ApplicationScope scope: CoroutineScope,
    ): AgentRuntime = AgentRuntime(
        models = models,
        registry = registry,
        validator = validator,
        screen = DeviceScreen(automation, observer),
        journal = StoredTaskJournal(history, memory, automation),
        clock = DeviceClock,
        scope = scope,
    ).also { runtime -> TouchToStopWatcher(settings).watch(runtime, scope) }
}

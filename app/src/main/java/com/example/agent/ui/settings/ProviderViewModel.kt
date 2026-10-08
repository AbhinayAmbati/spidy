package com.example.agent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.core.ai.AIProviderException
import com.example.agent.core.ai.AIProviderFactory
import com.example.agent.data.preferences.SettingsRepository
import com.example.agent.domain.model.AgentMessage
import com.example.agent.domain.model.MessageRole
import com.example.agent.domain.model.ProviderConfig
import com.example.agent.domain.model.ProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProviderViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val factory: AIProviderFactory,
) : ViewModel() {

    private val _selected = MutableStateFlow(ProviderType.OPENAI)
    val selected: StateFlow<ProviderType> = _selected.asStateFlow()

    private val _model = MutableStateFlow(ProviderType.OPENAI.defaultModel)
    val model: StateFlow<String> = _model.asStateFlow()

    private val _baseUrl = MutableStateFlow(ProviderType.OPENAI.defaultBaseUrl)
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    /** True when the user is typing a model name instead of picking a suggestion. */
    private val _customModel = MutableStateFlow(false)
    val customModel: StateFlow<Boolean> = _customModel.asStateFlow()

    private val _keyInput = MutableStateFlow("")
    val keyInput: StateFlow<String> = _keyInput.asStateFlow()

    private val _maskedKey = MutableStateFlow<String?>(null)
    val maskedKey: StateFlow<String?> = _maskedKey.asStateFlow()

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = settings.currentProviderConfig()
            applyConfig(config)
        }
    }

    private fun applyConfig(config: ProviderConfig) {
        _selected.value = config.type
        _model.value = config.model
        _baseUrl.value = config.baseUrl
        _maskedKey.value = settings.maskedApiKey(config.type)
        _keyInput.value = ""
        _customModel.value = config.type.requiresCustomModel ||
            (config.model.isNotBlank() && config.model !in config.type.suggestedModels)
    }

    fun select(type: ProviderType) {
        _selected.value = type
        _model.value = type.defaultModel
        _baseUrl.value = type.defaultBaseUrl
        _maskedKey.value = settings.maskedApiKey(type)
        _keyInput.value = ""
        _customModel.value = type.requiresCustomModel
        _testState.value = TestState.Idle
    }

    /** Pick one of the provider's suggested models. */
    fun selectModel(model: String) {
        _model.value = model
        _customModel.value = false
    }

    /** Switch to free-text entry; keeps whatever is typed so it can be edited. */
    fun chooseCustomModel() {
        _customModel.value = true
    }

    fun setModel(value: String) { _model.value = value }

    fun setBaseUrl(value: String) { _baseUrl.value = value }
    fun setKeyInput(value: String) { _keyInput.value = value }

    fun save() {
        viewModelScope.launch {
            persist()
            _testState.value = TestState.Saved
        }
    }

    fun deleteKey() {
        viewModelScope.launch {
            settings.deleteApiKey(_selected.value)
            _maskedKey.value = null
            _keyInput.value = ""
            _testState.value = TestState.Idle
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _testState.value = TestState.Testing
            persist()
            try {
                // Reading the key touches EncryptedSharedPreferences (crypto + disk);
                // keep it off the main thread along with the request itself.
                val provider = withContext(Dispatchers.IO) {
                    factory.create(
                        ProviderConfig(
                            type = _selected.value,
                            model = _model.value.ifBlank { _selected.value.defaultModel },
                            baseUrl = _baseUrl.value.ifBlank { _selected.value.defaultBaseUrl },
                            hasApiKey = true,
                        )
                    )
                }
                provider.generateResponse(
                    messages = listOf(
                        AgentMessage(MessageRole.System, "You are a connectivity check."),
                        AgentMessage(MessageRole.User, "Reply with the single word: OK"),
                    ),
                    tools = emptyList(),
                )
                _testState.value = TestState.Success("Connection successful.")
            } catch (e: AIProviderException) {
                _testState.value = TestState.Failure(
                    when (e.kind) {
                        AIProviderException.Kind.NoKey -> "No API key saved for this provider."
                        AIProviderException.Kind.Unauthorized ->
                            e.message ?: "The key was rejected by the provider."
                        AIProviderException.Kind.RateLimited -> "Rate limited. The key works, but try again shortly."
                        AIProviderException.Kind.Network -> "Could not reach the provider. Check your connection."
                        AIProviderException.Kind.Server ->
                            e.message ?: "The provider returned a server error."
                        AIProviderException.Kind.Parse -> "Connected, but the response could not be read."
                        AIProviderException.Kind.Unknown ->
                            e.message ?: "Connection failed."
                    }
                )
            } catch (e: Exception) {
                _testState.value = TestState.Failure("Connection failed: ${e.message ?: "unknown error"}")
            }
        }
    }

    private suspend fun persist() {
        settings.saveProvider(
            type = _selected.value,
            model = _model.value.ifBlank { _selected.value.defaultModel },
            baseUrl = _baseUrl.value.ifBlank { _selected.value.defaultBaseUrl },
        )
        val typed = _keyInput.value.trim()
        if (typed.isNotEmpty()) {
            settings.saveApiKey(_selected.value, typed)
            _maskedKey.value = settings.maskedApiKey(_selected.value)
            _keyInput.value = ""
        }
    }

    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data object Saved : TestState
        data class Success(val message: String) : TestState
        data class Failure(val message: String) : TestState
    }
}

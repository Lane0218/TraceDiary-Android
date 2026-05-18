package com.tracediary.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tracediary.app.TraceDiaryApplication
import com.tracediary.app.data.security.AppLockState
import com.tracediary.app.data.security.AppPasswordManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 应用解锁页 ViewModel：把 [AppPasswordManager] 的同步 API 转成 Compose 友好的 [StateFlow]。
 *
 * - 首次设置密码：内部做"两次一致 + 非空"校验，失败把错误文案写入 [AppLockUiState.errorMessage]。
 * - 解锁失败：把 [AppPasswordManager.unlock] 的异常消息透出，UI 直接显示。
 */
class AppLockViewModel(
    private val passwordManager: AppPasswordManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AppLockUiState(
            lockState = passwordManager.state.value,
            rememberPassword = passwordManager.isRememberPasswordEnabled(),
        )
    )
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    /** UI 仅观察当前状态用于切换内容；锁状态变化由 manager 推送。 */
    val lockState: StateFlow<AppLockState> = passwordManager.state

    fun onToggleRemember(value: Boolean) {
        passwordManager.setRememberPasswordEnabled(value)
        _uiState.update { it.copy(rememberPassword = value) }
    }

    fun onSetupPassword(password: String) {
        if (password.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请输入密码并确认两次一致") }
            return
        }
        val remember = _uiState.value.rememberPassword
        val result = passwordManager.setupInitialPassword(password, remember)
        result.onSuccess {
            _uiState.update { it.copy(errorMessage = null) }
        }.onFailure { e ->
            _uiState.update { it.copy(errorMessage = e.message ?: "设置密码失败") }
        }
    }

    fun onUnlock(password: String) {
        val result = passwordManager.unlock(password)
        result.onSuccess {
            _uiState.update { it.copy(errorMessage = null) }
        }.onFailure { e ->
            _uiState.update { it.copy(errorMessage = e.message ?: "密码错误") }
        }
    }

    companion object {
        fun factory(app: TraceDiaryApplication): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AppLockViewModel(app.appPasswordManager) }
            }
    }
}

data class AppLockUiState(
    val lockState: AppLockState,
    val rememberPassword: Boolean,
    val errorMessage: String? = null,
)

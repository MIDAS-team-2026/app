package com.midas26.mobileapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.midas26.mobileapp.network.ApiResponse
import com.midas26.mobileapp.network.LoginRequest
import com.midas26.mobileapp.network.ProtectorInfo
import com.midas26.mobileapp.network.ResetPasswordRequest
import com.midas26.mobileapp.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ProfileEditState {
    object Idle : ProfileEditState()
    object Loading : ProfileEditState()
    object PasswordChanged : ProfileEditState()
    data class Error(val message: String) : ProfileEditState()
}

class ProfileEditViewModel : ViewModel() {

    private val _state = MutableStateFlow<ProfileEditState>(ProfileEditState.Idle)
    val state: StateFlow<ProfileEditState> = _state

    private val _protectors = MutableStateFlow<List<ProtectorInfo>>(emptyList())
    val protectors: StateFlow<List<ProtectorInfo>> = _protectors

    fun loadProtectors(userId: Int) {
        viewModelScope.launch {
            runCatching { RetrofitClient.instance.getProtectors(userId) }
                .onSuccess { resp ->
                    _protectors.value = resp.body()?.data ?: emptyList()
                }
        }
    }

    fun changePassword(phone: String, currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _state.value = ProfileEditState.Loading
            try {
                // 현재 비밀번호 검증
                val loginResponse = RetrofitClient.instance.login(LoginRequest(phone, currentPassword))
                if (!loginResponse.isSuccessful) {
                    _state.value = ProfileEditState.Error("현재 비밀번호가 올바르지 않습니다.")
                    return@launch
                }
                // 비밀번호 변경
                val resetResponse = RetrofitClient.instance.resetPassword(ResetPasswordRequest(phone, newPassword))
                if (resetResponse.isSuccessful) {
                    _state.value = ProfileEditState.PasswordChanged
                } else {
                    _state.value = ProfileEditState.Error(
                        parseError(resetResponse.errorBody()?.string(), "비밀번호 변경에 실패했습니다.")
                    )
                }
            } catch (e: Exception) {
                _state.value = ProfileEditState.Error("서버에 연결할 수 없습니다.")
            }
        }
    }

    fun resetState() { _state.value = ProfileEditState.Idle }

    private fun parseError(json: String?, default: String): String = try {
        if (!json.isNullOrEmpty())
            Gson().fromJson(json, ApiResponse::class.java).message ?: default
        else default
    } catch (e: Exception) { default }
}

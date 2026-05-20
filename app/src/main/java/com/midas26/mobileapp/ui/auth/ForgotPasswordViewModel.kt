package com.midas26.mobileapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.midas26.mobileapp.network.ApiResponse
import com.midas26.mobileapp.network.ForgotPasswordRequest
import com.midas26.mobileapp.network.ResetPasswordRequest
import com.midas26.mobileapp.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ForgotPasswordState {
    object Idle : ForgotPasswordState()
    object Loading : ForgotPasswordState()
    object PhoneVerified : ForgotPasswordState()
    object PasswordReset : ForgotPasswordState()
    data class Error(val message: String) : ForgotPasswordState()
}

class ForgotPasswordViewModel : ViewModel() {

    private val _state = MutableStateFlow<ForgotPasswordState>(ForgotPasswordState.Idle)
    val state: StateFlow<ForgotPasswordState> = _state

    fun checkPhone(phone: String) {
        viewModelScope.launch {
            _state.value = ForgotPasswordState.Loading
            try {
                val response = RetrofitClient.instance.forgotPassword(ForgotPasswordRequest(phone))
                if (response.isSuccessful) {
                    _state.value = ForgotPasswordState.PhoneVerified
                } else {
                    _state.value = ForgotPasswordState.Error(
                        parseError(response.errorBody()?.string(), "등록되지 않은 전화번호입니다.")
                    )
                }
            } catch (e: Exception) {
                _state.value = ForgotPasswordState.Error("서버에 연결할 수 없습니다.")
            }
        }
    }

    fun resetPassword(phone: String, newPassword: String) {
        viewModelScope.launch {
            _state.value = ForgotPasswordState.Loading
            try {
                val response = RetrofitClient.instance.resetPassword(ResetPasswordRequest(phone, newPassword))
                if (response.isSuccessful) {
                    _state.value = ForgotPasswordState.PasswordReset
                } else {
                    _state.value = ForgotPasswordState.Error(
                        parseError(response.errorBody()?.string(), "비밀번호 변경에 실패했습니다.")
                    )
                }
            } catch (e: Exception) {
                _state.value = ForgotPasswordState.Error("서버에 연결할 수 없습니다.")
            }
        }
    }

    fun resetState() { _state.value = ForgotPasswordState.Idle }

    private fun parseError(json: String?, default: String): String = try {
        if (!json.isNullOrEmpty())
            Gson().fromJson(json, ApiResponse::class.java).message ?: default
        else default
    } catch (e: Exception) { default }
}

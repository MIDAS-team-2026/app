package com.midas26.mobileapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.midas26.mobileapp.network.RetrofitClient
import com.midas26.mobileapp.network.SendCodeRequest
import com.midas26.mobileapp.network.VerifyCodeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class VerificationState {
    object Idle : VerificationState()
    object Sending : VerificationState()
    object CodeSent : VerificationState()
    object Verifying : VerificationState()
    object Verified : VerificationState()
    data class Error(val message: String) : VerificationState()
}

class PhoneVerificationViewModel : ViewModel() {

    private val _state = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val state: StateFlow<VerificationState> = _state

    fun sendCode(phone: String) {
        viewModelScope.launch {
            _state.value = VerificationState.Sending
            try {
                val response = RetrofitClient.instance.sendCode(SendCodeRequest(phone))
                if (response.isSuccessful) {
                    _state.value = VerificationState.CodeSent
                } else {
                    _state.value = VerificationState.Error("인증번호 전송에 실패했습니다.")
                }
            } catch (e: Exception) {
                _state.value = VerificationState.Error("서버에 연결할 수 없습니다.")
            }
        }
    }

    fun verifyCode(phone: String, code: String) {
        viewModelScope.launch {
            _state.value = VerificationState.Verifying
            try {
                val response = RetrofitClient.instance.verifyCode(VerifyCodeRequest(phone, code))
                if (response.isSuccessful) {
                    _state.value = VerificationState.Verified
                } else {
                    _state.value = VerificationState.Error("인증번호가 올바르지 않습니다.")
                }
            } catch (e: Exception) {
                _state.value = VerificationState.Error("서버에 연결할 수 없습니다.")
            }
        }
    }

    fun resetState() { _state.value = VerificationState.Idle }
    fun resetToCodeSent() { _state.value = VerificationState.CodeSent }
}

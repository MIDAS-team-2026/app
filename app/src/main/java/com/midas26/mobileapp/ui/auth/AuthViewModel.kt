package com.midas26.mobileapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.midas26.mobileapp.network.LoginRequest
import com.midas26.mobileapp.network.RetrofitClient
import com.midas26.mobileapp.network.SignupRequest
import com.midas26.mobileapp.network.UserResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: UserResponse) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = RetrofitClient.instance.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body()?.data != null) {
                    _authState.value = AuthState.Success(response.body()!!.data!!)
                } else {
                    _authState.value = AuthState.Error("아이디 또는 비밀번호가 틀렸습니다.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("서버에 연결할 수 없습니다.")
            }
        }
    }

    fun signup(email: String, password: String, name: String, role: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = RetrofitClient.instance.signup(
                    SignupRequest(email, password, name, role, null, null)
                )
                if (response.isSuccessful && response.body()?.data != null) {
                    _authState.value = AuthState.Success(response.body()!!.data!!)
                } else {
                    _authState.value = AuthState.Error("회원가입에 실패했습니다.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("서버에 연결할 수 없습니다.")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}

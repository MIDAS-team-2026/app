package com.midas26.mobileapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.midas26.mobileapp.network.ApiResponse
import com.midas26.mobileapp.network.LoginRequest
import com.midas26.mobileapp.network.RetrofitClient
import com.midas26.mobileapp.network.SendCodeRequest
import com.midas26.mobileapp.network.SignupRequest
import com.midas26.mobileapp.network.UserResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object PhoneChecked : AuthState()   // 전화번호 중복 없음 + 인증코드 발송 완료
    data class Success(val user: UserResponse) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    // USER 역할 회원가입 시 SignupCompleteScreen에서 사용할 임시 데이터
    var pendingPhone: String = ""; private set
    var pendingPassword: String = ""; private set
    var pendingName: String = ""; private set
    var pendingRole: String = ""; private set
    var pendingBirth: String = ""; private set

    fun savePendingSignupData(phone: String, password: String, name: String, role: String, birth: String = "") {
        pendingPhone = phone
        pendingPassword = password
        pendingName = name
        pendingRole = role
        pendingBirth = birth
    }

    fun signupFromPending() {
        signup(pendingPhone, pendingPassword, pendingName, pendingRole)
    }

    /** 회원가입 전 전화번호 중복 체크 + 인증코드 발송. 성공하면 PhoneChecked 상태. */
    fun checkPhoneAndSendCode(phone: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = RetrofitClient.instance.sendCode(SendCodeRequest(phone, "SIGNUP"))
                if (response.isSuccessful) {
                    _authState.value = AuthState.PhoneChecked
                } else {
                    val message = try {
                        val json = response.errorBody()?.string()
                        if (!json.isNullOrEmpty())
                            Gson().fromJson(json, ApiResponse::class.java).message ?: "전화번호 확인에 실패했습니다."
                        else "전화번호 확인에 실패했습니다."
                    } catch (e: Exception) { "전화번호 확인에 실패했습니다." }
                    _authState.value = AuthState.Error(message)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error("서버에 연결할 수 없습니다.")
            }
        }
    }

    fun login(phone: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = RetrofitClient.instance.login(LoginRequest(phone, password))
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

    fun signup(phone: String, password: String, name: String, role: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = RetrofitClient.instance.signup(
                    SignupRequest(phone, password, name, role, null, null)
                )
                if (response.isSuccessful && response.body()?.data != null) {
                    _authState.value = AuthState.Success(response.body()!!.data!!)
                } else {
                    val message = try {
                        val json = response.errorBody()?.string()
                        if (!json.isNullOrEmpty())
                            Gson().fromJson(json, ApiResponse::class.java).message
                                ?: "회원가입에 실패했습니다."
                        else "회원가입에 실패했습니다."
                    } catch (e: Exception) { "회원가입에 실패했습니다." }
                    _authState.value = AuthState.Error(message)
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

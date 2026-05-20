package com.midas26.mobileapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.midas26.mobileapp.network.ApiResponse
import com.midas26.mobileapp.network.RetrofitClient
import com.midas26.mobileapp.network.WithdrawRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class WithdrawState {
    object Idle : WithdrawState()
    object Loading : WithdrawState()
    object Withdrawn : WithdrawState()
    data class Error(val message: String) : WithdrawState()
}

class WithdrawViewModel : ViewModel() {

    private val _state = MutableStateFlow<WithdrawState>(WithdrawState.Idle)
    val state: StateFlow<WithdrawState> = _state

    fun withdraw(phone: String) {
        viewModelScope.launch {
            _state.value = WithdrawState.Loading
            try {
                val response = RetrofitClient.instance.withdraw(WithdrawRequest(phone))
                if (response.isSuccessful) {
                    _state.value = WithdrawState.Withdrawn
                } else {
                    _state.value = WithdrawState.Error(
                        parseError(response.errorBody()?.string(), "탈퇴 처리에 실패했습니다.")
                    )
                }
            } catch (e: Exception) {
                _state.value = WithdrawState.Error("서버에 연결할 수 없습니다.")
            }
        }
    }

    fun resetState() { _state.value = WithdrawState.Idle }

    private fun parseError(json: String?, default: String): String = try {
        if (!json.isNullOrEmpty())
            Gson().fromJson(json, ApiResponse::class.java).message ?: default
        else default
    } catch (e: Exception) { default }
}

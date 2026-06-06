package com.midas26.mobileapp.ui.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.network.LinkRequest
import com.midas26.mobileapp.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GuardianViewModel : ViewModel() {

    private val _patients = MutableStateFlow<List<LinkedUserInfo>>(emptyList())
    val patients: StateFlow<List<LinkedUserInfo>> = _patients

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentGuardianId: Int = -1

    /** 보호자 ID로 연결된 환자 목록 로드 */
    fun loadPatients(guardianId: Int) {
        if (guardianId <= 0) return
        currentGuardianId = guardianId
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val resp = RetrofitClient.instance.getPatientsByProtector(guardianId)
                if (resp.isSuccessful) {
                    _patients.value = resp.body()?.data ?: emptyList()
                } else {
                    _error.value = "환자 목록을 불러오지 못했습니다."
                }
            } catch (e: Exception) {
                _error.value = "서버에 연결할 수 없습니다."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 환자 코드로 연동 추가 */
    fun linkPatient(
        patientCode: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (currentGuardianId <= 0) { onError("로그인 정보가 없습니다."); return }
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.instance.linkPatient(
                    LinkRequest(currentGuardianId, patientCode.trim().uppercase())
                )
                if (resp.isSuccessful) {
                    loadPatients(currentGuardianId)   // 목록 갱신
                    onSuccess()
                } else {
                    val msg = resp.body()?.message ?: "연동에 실패했습니다."
                    onError(msg)
                }
            } catch (e: Exception) {
                onError("서버에 연결할 수 없습니다.")
            }
        }
    }

    /** 연동 해제 */
    fun unlinkPatient(
        patientId: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (currentGuardianId <= 0) { onError("로그인 정보가 없습니다."); return }
        viewModelScope.launch {
            try {
                val resp = RetrofitClient.instance.unlinkPatient(currentGuardianId, patientId)
                if (resp.isSuccessful) {
                    loadPatients(currentGuardianId)   // 목록 갱신
                    onSuccess()
                } else {
                    onError("연동 해제에 실패했습니다.")
                }
            } catch (e: Exception) {
                onError("서버에 연결할 수 없습니다.")
            }
        }
    }
}

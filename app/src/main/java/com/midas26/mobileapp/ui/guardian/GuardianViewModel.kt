package com.midas26.mobileapp.ui.guardian

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.midas26.mobileapp.network.LinkedUserInfo
import com.midas26.mobileapp.network.LinkRequest
import com.midas26.mobileapp.network.RetrofitClient
import com.midas26.mobileapp.util.PrefsManager
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

    /**
     * 코드로 환자 정보 조회 후 이름·전화번호 교차검증, 통과 시 연동 + 관계 로컬 저장.
     * @param inputName     보호자가 입력한 이름
     * @param inputPhone    보호자가 입력한 전화번호 (하이픈 무관)
     * @param relation      관계 (로컬 저장)
     */
    fun verifyAndLinkPatient(
        context: Context,
        patientCode: String,
        inputName: String,
        inputPhone: String,
        relation: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (currentGuardianId <= 0) { onError("로그인 정보가 없습니다."); return }
        val code = patientCode.trim().uppercase()
        viewModelScope.launch {
            try {
                // 1. 코드로 환자 조회
                val infoResp = RetrofitClient.instance.getPatientByCode(code)
                if (!infoResp.isSuccessful) {
                    onError("존재하지 않는 사용자 코드입니다."); return@launch
                }
                val patient = infoResp.body()?.data
                    ?: run { onError("환자 정보를 불러오지 못했습니다."); return@launch }

                // 2. 이름 검증
                val serverName = patient.name?.trim() ?: ""
                if (!serverName.equals(inputName.trim(), ignoreCase = true)) {
                    onError("이름이 일치하지 않습니다."); return@launch
                }

                // 3. 전화번호 검증 (하이픈 제거 후 비교)
                val serverPhone = patient.phone?.replace("-", "") ?: ""
                val inputPhoneClean = inputPhone.replace("-", "")
                if (serverPhone != inputPhoneClean) {
                    onError("전화번호가 일치하지 않습니다."); return@launch
                }

                // 4. 연동 API 호출
                val linkResp = RetrofitClient.instance.linkPatient(
                    LinkRequest(currentGuardianId, code)
                )
                if (!linkResp.isSuccessful) {
                    val msg = linkResp.body()?.message ?: "연동에 실패했습니다."
                    onError(msg); return@launch
                }

                // 5. 관계 로컬 저장
                patient.userId?.let { patientId ->
                    PrefsManager.from(context).savePatientRelation(patientId, relation.trim())
                }

                loadPatients(currentGuardianId)
                onSuccess()

            } catch (e: Exception) {
                onError("서버에 연결할 수 없습니다.")
            }
        }
    }

    /** 연동 해제 시 로컬 관계 정보도 함께 삭제 */
    fun unlinkPatientWithRelation(
        context: Context,
        patientId: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        unlinkPatient(
            patientId = patientId,
            onSuccess = {
                PrefsManager.from(context).removePatientRelation(patientId)
                onSuccess()
            },
            onError = onError
        )
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

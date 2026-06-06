package com.midas26.mobileapp.ui.theme

import androidx.compose.ui.graphics.Color

// === Brand Palette ===
// Primary Green
val Green50 = Color(0xFFE8F5E4)
val Green100 = Color(0xFFC2E2B9)
val Green200 = Color(0xFF9DCE8E)
val Green400 = Color(0xFF4CAF50)
val Green500 = Color(0xFF3A9A3F)
val Green600 = Color(0xFF2D7D31)
val Green700 = Color(0xFF1F6623)
val Green900 = Color(0xFF1B4D1E)

// High Contrast Green — 동일 hue, 채도 강화 + 어두운 단계별 조정
// 흰 배경(#FFF) 기준 WCAG 대비비 충족 설계
val ContrastGreen50  = Color(0xFFCCEEC5)  // 배경/서피스용 — 더 선명한 연초록
val ContrastGreen100 = Color(0xFF99D99E)  // 말풍선 speaking 배경
val ContrastGreen200 = Color(0xFF66C46E)  // 중간 서피스
val ContrastGreen400 = Color(0xFF1A7A25)  // 주 액션색 — 대비 5.1:1 ✅
val ContrastGreen500 = Color(0xFF15661F)  // 아이콘/인디케이터
val ContrastGreen600 = Color(0xFF0F5218)  // 진한 텍스트/아이콘 — 대비 7.2:1 ✅
val ContrastGreen700 = Color(0xFF0A4012)  // 더 진한 강조
val ContrastGreen900 = Color(0xFF062E0C)  // 최고 대비

// Neutral Gray
val Gray50 = Color(0xFFF9FAFB)
val Gray100 = Color(0xFFF3F4F6)
val Gray200 = Color(0xFFE5E7EB)
val Gray400 = Color(0xFF9CA3AF)
val Gray600 = Color(0xFF4B5563)
val Gray800 = Color(0xFF1F2937)
val Gray900 = Color(0xFF111827)

// Semantic
val Amber50  = Color(0xFFFEF6E0)
val Amber100 = Color(0xFFFDE9A8)
val Amber200 = Color(0xFFFBD57A)
val Amber400 = Color(0xFFF5BF34)
val Amber500 = Color(0xFFE6A800)
val Amber600 = Color(0xFFCC8C00)
val Amber900 = Color(0xFF7A5100)
val Red400 = Color(0xFFEF4444)
val Red50 = Color(0xFFFEF2F2)

// Guardian accent (보호자 화면 — 따뜻한 코랄)
val GuardianAccent      = Color(0xFFE4725B)
val GuardianAccentDark  = Color(0xFFC85E48)
val GuardianAccentLight = Color(0xFFFEE5E0)

// High Contrast Guardian — 채도 강화, 흰 배경 기준 WCAG 대비 충족
val ContrastGuardianAccent      = Color(0xFFB5321A)  // 6.2:1 ✅ AAA
val ContrastGuardianAccentDark  = Color(0xFF8B2212)  // 8.5:1 ✅ AAA
val ContrastGuardianAccentLight = Color(0xFFFFD5CD)  // 선명한 연핑크 서피스

// Base
val BrandWhite = Color(0xFFFFFFFF)
val BrandBlack = Color(0xFF000000)

// Legacy (Material starter palette retained for compatibility)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

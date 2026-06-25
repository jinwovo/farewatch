package com.portfolio.farewatch.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.portfolio.farewatch.R

// DM Sans (Latin); Korean falls back to the system Noto Sans KR automatically — same as web.
val DMSans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

private val d = Typography()
val FarewatchTypography = Typography(
    displayLarge = d.displayLarge.copy(fontFamily = DMSans),
    displayMedium = d.displayMedium.copy(fontFamily = DMSans),
    displaySmall = d.displaySmall.copy(fontFamily = DMSans),
    headlineLarge = d.headlineLarge.copy(fontFamily = DMSans),
    headlineMedium = d.headlineMedium.copy(fontFamily = DMSans),
    headlineSmall = d.headlineSmall.copy(fontFamily = DMSans),
    titleLarge = d.titleLarge.copy(fontFamily = DMSans),
    titleMedium = d.titleMedium.copy(fontFamily = DMSans),
    titleSmall = d.titleSmall.copy(fontFamily = DMSans),
    bodyLarge = d.bodyLarge.copy(fontFamily = DMSans),
    bodyMedium = d.bodyMedium.copy(fontFamily = DMSans),
    bodySmall = d.bodySmall.copy(fontFamily = DMSans),
    labelLarge = d.labelLarge.copy(fontFamily = DMSans),
    labelMedium = d.labelMedium.copy(fontFamily = DMSans),
    labelSmall = d.labelSmall.copy(fontFamily = DMSans),
)

package com.example.pelletswaterheater.ui.misc

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

data class UiState(
    val input: String = "",
    val isError: Boolean = false,
    val canSubmit: Boolean = false,
)
@Composable
fun combinePadding(innerPadding: PaddingValues, dp: Dp): PaddingValues {
    val combinedPadding = PaddingValues(
        start = innerPadding.calculateStartPadding(LayoutDirection.Ltr) + dp,
        top = innerPadding.calculateTopPadding() + dp,
        end = innerPadding.calculateEndPadding(LayoutDirection.Ltr) + dp,
        bottom = innerPadding.calculateBottomPadding() + dp
    )
    return combinedPadding
}
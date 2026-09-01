package com.sdau.campuskit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class LoginMode { PERSONAL, PUBLIC }

/**
 * Compose-ready state for the login page.
 *
 * The current View implementation reads it through compatibility properties in
 * MainActivity.  Keeping the dependent public-schedule selections here ensures
 * the same cascade rules can be reused unchanged by a future Compose screen.
 */
@Stable
internal class LoginUiState {
    var mode by mutableStateOf(LoginMode.PERSONAL)
    var college by mutableStateOf("")
    var grade by mutableStateOf("")
    var major by mutableStateOf("")
    var className by mutableStateOf("")

    fun resetPublicSelection() {
        college = ""
        grade = ""
        major = ""
        className = ""
    }

    fun selectCollege(value: String) {
        college = value
        grade = ""
        major = ""
        className = ""
    }

    fun selectGrade(value: String) {
        grade = value
        major = ""
        className = ""
    }

    fun selectMajor(value: String) {
        major = value
        className = ""
    }

    fun selectClass(value: String) {
        className = value
    }

    fun resolvePublicSelection(
        college: String,
        grade: String,
        major: String,
        className: String
    ) {
        this.college = college
        this.grade = grade
        this.major = major
        this.className = className
    }
}

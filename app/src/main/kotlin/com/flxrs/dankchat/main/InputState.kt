package com.flxrs.dankchat.main

sealed interface InputState {
    object Default : InputState
    object Replying : InputState
    object NotLoggedIn : InputState
    object Disconnected: InputState
    data class Restricted(val restrictions: List<ChatRestriction>) : InputState
}

package com.flxrs.dankchat.main

/**
 * A reason why the current user can not chat freely in the active channel.
 * [blocksSending] restrictions mean the user is not able to send messages at all.
 */
sealed interface ChatRestriction {
    val blocksSending: Boolean

    data object Banned : ChatRestriction {
        override val blocksSending = true
    }

    /** [remainingSeconds] is null if the remaining time is unknown. */
    data class TimedOut(val remainingSeconds: Long?) : ChatRestriction {
        override val blocksSending = true
    }

    data object PhoneVerificationRequired : ChatRestriction {
        override val blocksSending = true
    }

    data object EmailVerificationRequired : ChatRestriction {
        override val blocksSending = true
    }

    data object ChannelSuspended : ChatRestriction {
        override val blocksSending = true
    }

    data object SubscribersOnly : ChatRestriction {
        override val blocksSending = true
    }

    /**
     * [minutes] is either the full follow duration (not following / unknown) or the remaining minutes until eligible (following).
     * A value of 0 means any follower is allowed to chat.
     * [isCertain] is false if the follow status of the user could not be determined.
     * [isFollowing] is true when the user already follows the channel and is waiting for the follow-age duration.
     */
    data class FollowersOnly(
        val minutes: Long,
        val isCertain: Boolean,
        val isFollowing: Boolean = false,
    ) : ChatRestriction {
        override val blocksSending get() = isCertain
    }

    data object EmoteOnly : ChatRestriction {
        override val blocksSending = false
    }

    data class SlowMode(val seconds: Int) : ChatRestriction {
        override val blocksSending = false
    }
}

enum class SendIconState {
    Hidden,
    Send,
    Blocked,
}


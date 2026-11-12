package com.flxrs.dankchat.data.repo.chat

import android.util.Log
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks per-channel information about whether the logged-in user is allowed to chat,
 * which can not be derived from ROOMSTATE/USERSTATE alone (bans, timeouts, follow age, ...).
 */
@Single
class ChatRestrictionRepository(
    private val helixApiClient: HelixApiClient,
    private val preferenceStore: DankChatPreferenceStore,
) {

    private val _moderation = MutableStateFlow<Map<UserName, ModerationRestriction>>(emptyMap())
    val moderation: StateFlow<Map<UserName, ModerationRestriction>> = _moderation.asStateFlow()

    private val _followStatus = MutableStateFlow<Map<UserName, FollowStatus>>(emptyMap())
    val followStatus: StateFlow<Map<UserName, FollowStatus>> = _followStatus.asStateFlow()

    private val followMutex = Mutex()

    fun handleClearChat(message: ModerationMessage, isHistoric: Boolean = false) {
        val target = message.targetUser ?: return
        val self = preferenceStore.userName ?: return
        if (!target.matches(self)) return

        when (message.action) {
            ModerationMessage.Action.Ban     -> if (!isHistoric) setModeration(message.channel, ModerationRestriction.Banned)
            ModerationMessage.Action.Timeout -> {
                val seconds = message.durationInt ?: return
                val start = if (isHistoric) message.timestamp else System.currentTimeMillis()
                val until = start + seconds.seconds.inWholeMilliseconds
                if (until <= System.currentTimeMillis()) return
                setModeration(message.channel, ModerationRestriction.TimedOut(until))
            }

            else                             -> Unit
        }
    }

    fun handleNotice(msg: IrcMessage) {
        if (msg.command != "NOTICE") return
        val msgId = msg.tags["msg-id"] ?: return
        val channel = msg.params.getOrNull(0)?.removePrefix("#")?.ifBlank { null }?.toUserName() ?: return
        val text = msg.params.getOrNull(1).orEmpty()
        when (msgId) {
            "msg_banned"                        -> setModeration(channel, ModerationRestriction.Banned)
            "msg_timedout"                      -> {
                val remaining = TIMED_OUT_REGEX.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
                val until = remaining?.let { System.currentTimeMillis() + it.seconds.inWholeMilliseconds }
                setModeration(channel, ModerationRestriction.TimedOut(until))
            }

            "msg_requires_verified_phone_number" -> setModeration(channel, ModerationRestriction.PhoneVerificationRequired)
            "msg_verified_email"                -> setModeration(channel, ModerationRestriction.EmailVerificationRequired)
            "msg_channel_suspended"             -> setModeration(channel, ModerationRestriction.ChannelSuspended)
            "msg_followersonly",
            "msg_followersonly_zero"            -> _followStatus.update { it + (channel to FollowStatus.NotFollowing(System.currentTimeMillis())) }
        }
    }

    /** Called when one of our own messages showed up in chat, which proves we are able to chat. */
    fun handleOwnMessage(channel: UserName) {
        if (channel !in _moderation.value) return
        _moderation.update { it - channel }
    }

    fun removeChannel(channel: UserName) {
        _moderation.update { it - channel }
        _followStatus.update { it - channel }
    }

    fun clear() {
        _moderation.value = emptyMap()
        _followStatus.value = emptyMap()
    }

    fun clearModeration() {
        _moderation.value = emptyMap()
    }

    /**
     * Fetches the follow date of the current user for [channel], if it is not known yet, outdated, or [force] is true.
     * Requires the optional `user:read:follows` scope; without it the status stays [FollowStatus.Unknown].
     */
    suspend fun refreshFollowStatusIfNeeded(
        channel: UserName,
        channelId: UserId,
        userId: UserId,
        force: Boolean = false,
    ) = followMutex.withLock {
        val now = System.currentTimeMillis()
        val current = _followStatus.value[channel]
        val isFresh = !force && when (current) {
            is FollowStatus.Following    -> true
            is FollowStatus.NotFollowing -> now - current.checkedAt < NOT_FOLLOWING_RECHECK.inWholeMilliseconds
            is FollowStatus.Unknown      -> now - current.checkedAt < UNKNOWN_RECHECK.inWholeMilliseconds
            null                         -> false
        }
        if (isFresh) return@withLock

        val status = helixApiClient.getFollowedChannel(userId, channelId).fold(
            onSuccess = { result ->
                val followedAt = result.data.firstOrNull()?.followedAt
                when (followedAt) {
                    null -> FollowStatus.NotFollowing(now)
                    else -> runCatching { Instant.parse(followedAt).toEpochMilli() }
                        .map { FollowStatus.Following(it) }
                        .getOrElse { FollowStatus.Unknown(now) }
                }
            },
            onFailure = {
                Log.d(TAG, "Failed to load follow status for $channel: ${it.message}")
                FollowStatus.Unknown(now)
            }
        )
        _followStatus.update { it + (channel to status) }
    }

    private fun setModeration(channel: UserName, restriction: ModerationRestriction) {
        _moderation.update { it + (channel to restriction) }
    }

    companion object {
        private val TAG = ChatRestrictionRepository::class.java.simpleName
        private val TIMED_OUT_REGEX = """(\d+)\s+more\s+seconds?""".toRegex(RegexOption.IGNORE_CASE)
        private val NOT_FOLLOWING_RECHECK = 1.minutes
        private val UNKNOWN_RECHECK = 10.minutes
    }
}

sealed interface ModerationRestriction {
    data object Banned : ModerationRestriction

    /** [untilMillis] is null when the remaining time is unknown. */
    data class TimedOut(val untilMillis: Long?) : ModerationRestriction
    data object PhoneVerificationRequired : ModerationRestriction
    data object EmailVerificationRequired : ModerationRestriction
    data object ChannelSuspended : ModerationRestriction
}

sealed interface FollowStatus {
    data class Following(val followedAtMillis: Long) : FollowStatus
    data class NotFollowing(val checkedAt: Long) : FollowStatus
    data class Unknown(val checkedAt: Long) : FollowStatus
}


package com.flxrs.dankchat.data.repo.chat

import android.graphics.Color
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.api.helix.dto.UserFollowsDataDto
import com.flxrs.dankchat.data.api.helix.dto.UserFollowsDto
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
internal class ChatRestrictionRepositoryTest {

    @MockK
    lateinit var helixApiClient: HelixApiClient

    @MockK
    lateinit var preferenceStore: DankChatPreferenceStore

    @InjectMockKs
    lateinit var repository: ChatRestrictionRepository

    private val selfUser = "testuser".toUserName()
    private val channel = "somechannel".toUserName()

    @BeforeEach
    fun setup() {
        mockkStatic(Color::class)
        every { Color.parseColor(any()) } returns 0
        every { preferenceStore.userName } returns selfUser
    }

    @Test
    fun `handleClearChat records ban and timeout for self`() {
        val timeout = ModerationMessage(
            channel = channel,
            action = ModerationMessage.Action.Timeout,
            targetUser = selfUser,
            durationInt = 60,
        )
        repository.handleClearChat(timeout)
        val restriction = repository.moderation.value[channel]
        assertIs<ModerationRestriction.TimedOut>(restriction)
        assertTrue((restriction.untilMillis ?: 0L) > System.currentTimeMillis())

        val ban = ModerationMessage(
            channel = channel,
            action = ModerationMessage.Action.Ban,
            targetUser = selfUser,
        )
        repository.handleClearChat(ban)
        assertEquals(ModerationRestriction.Banned, repository.moderation.value[channel])
    }

    @Test
    fun `handleClearChat ignores other users and historic bans`() {
        val otherTimeout = ModerationMessage(
            channel = channel,
            action = ModerationMessage.Action.Timeout,
            targetUser = "someoneelse".toUserName(),
            durationInt = 60,
        )
        repository.handleClearChat(otherTimeout)
        assertNull(repository.moderation.value[channel])

        val historicBan = ModerationMessage(
            channel = channel,
            action = ModerationMessage.Action.Ban,
            targetUser = selfUser,
        )
        repository.handleClearChat(historicBan, isHistoric = true)
        assertNull(repository.moderation.value[channel])
    }

    @Test
    fun `handleNotice parses msg_timedout and clears on own message`() {
        val notice = IrcMessage.parse("@msg-id=msg_timedout :tmi.twitch.tv NOTICE #$channel :You are timed out for 42 more seconds.")
        repository.handleNotice(notice)
        val restriction = repository.moderation.value[channel]
        assertIs<ModerationRestriction.TimedOut>(restriction)
        assertTrue((restriction.untilMillis ?: 0L) > System.currentTimeMillis())

        repository.handleOwnMessage(channel)
        assertNull(repository.moderation.value[channel])
    }

    @Test
    fun `refreshFollowStatusIfNeeded caches following result`() = runBlocking {
        val channelId: UserId = "123".toUserId()
        val userId: UserId = "456".toUserId()
        coEvery { helixApiClient.getFollowedChannel(userId, channelId) } returns Result.success(
            UserFollowsDto(total = 1, data = listOf(UserFollowsDataDto(followedAt = "2024-01-01T00:00:00Z")))
        )

        repository.refreshFollowStatusIfNeeded(channel, channelId, userId)
        repository.refreshFollowStatusIfNeeded(channel, channelId, userId)

        assertIs<FollowStatus.Following>(repository.followStatus.value[channel])
        coVerify(exactly = 1) { helixApiClient.getFollowedChannel(userId, channelId) }
    }
}


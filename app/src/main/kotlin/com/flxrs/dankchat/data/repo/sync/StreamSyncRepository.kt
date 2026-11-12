package com.flxrs.dankchat.data.repo.sync

import android.util.Log
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.di.WebSocketOkHttpClient
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.seconds

@Single
class StreamSyncRepository(
    @Named(type = WebSocketOkHttpClient::class) private val okHttpClient: OkHttpClient,
    private val preferenceStore: DankChatPreferenceStore,
    private val json: Json,
    private val dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(dispatchersProvider.io + SupervisorJob())
    private val _syncedChannel = MutableSharedFlow<UserName>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val syncedChannel: SharedFlow<UserName> = _syncedChannel.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null

    @Serializable
    private data class NtfyMessage(
        val id: String? = null,
        val time: Long? = null,
        val event: String? = null,
        val message: String? = null,
    )

    init {
        scope.launch {
            preferenceStore.pcSyncTopicFlow.collectLatest { topic ->
                connectWebSocket(topic, pollCached = true)
            }
        }
    }

    private fun connectWebSocket(topic: String, pollCached: Boolean = false) {
        reconnectJob?.cancel()
        webSocket?.cancel()
        webSocket = null

        if (topic.isBlank()) return

        val cleanTopic = toNtfyTopic(topic)
        if (pollCached) {
            scope.launch {
                fetchLatestChannel(cleanTopic)?.let { channel ->
                    _syncedChannel.tryEmit(channel)
                }
            }
        }

        val request = Request.Builder()
            .url("wss://ntfy.sh/$cleanTopic/ws")
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseChannelFromNtfyLine(text)?.let { channel ->
                        Log.d(TAG, "Received real-time synced channel: $channel")
                        _syncedChannel.tryEmit(channel)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scheduleReconnect(cleanTopic)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Stream sync WebSocket failure: ${t.message}")
                    scheduleReconnect(cleanTopic)
                }
            }
        )
    }

    private fun scheduleReconnect(topic: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5.seconds)
            if (isActive && preferenceStore.pcSyncTopic.isNotBlank() && toNtfyTopic(preferenceStore.pcSyncTopic) == topic) {
                connectWebSocket(topic)
            }
        }
    }

    suspend fun refreshLatestChannel(): UserName? = withContext(dispatchersProvider.io) {
        val rawId = preferenceStore.pcSyncTopic.trim()
        if (rawId.isBlank()) return@withContext null
        val cleanTopic = toNtfyTopic(rawId)
        // Re-ensure WebSocket is connected in case it got stuck
        connectWebSocket(rawId)
        val latest = fetchLatestChannel(cleanTopic)
        if (latest != null) {
            _syncedChannel.tryEmit(latest)
        }
        latest
    }

    private fun toNtfyTopic(syncId: String): String {
        val trimmed = syncId.trim()
        return if (trimmed.startsWith("dankchat-sync-") || trimmed.startsWith("tcop-")) {
            trimmed
        } else {
            "tcop-$trimmed"
        }
    }

    private suspend fun fetchLatestChannel(topic: String): UserName? = withContext(dispatchersProvider.io) {
        runCatching {
            val request = Request.Builder()
                .url("https://ntfy.sh/$topic/json?poll=1&since=all")
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string()
                body.lineSequence()
                    .mapNotNull { line -> parseChannelFromNtfyLine(line) }
                    .lastOrNull()
            }
        }.onFailure {
            Log.w(TAG, "Failed to poll latest channel from ntfy.sh: ${it.message}")
        }.getOrNull()
    }

    private fun parseChannelFromNtfyLine(line: String): UserName? {
        if (line.isBlank()) return null
        val msg = runCatching { json.decodeFromString<NtfyMessage>(line) }.getOrNull() ?: return null
        if (msg.event != "message") return null
        val rawTrimmed = msg.message?.trim()?.removePrefix("#").orEmpty()
        val rawChannel = rawTrimmed.lowercase()
        if (!CHANNEL_REGEX.matches(rawChannel) || rawChannel in RESERVED_PATHS) return null
        val userName = rawChannel.toUserName()
        if (rawTrimmed.any(Char::isUpperCase)) {
            preferenceStore.recordOpenedChannel(userName, rawTrimmed)
        }
        return userName
    }

    companion object {
        private const val TAG = "StreamSyncRepository"
        private val CHANNEL_REGEX = Regex("^[a-z0-9_]{1,25}$")
        private val RESERVED_PATHS = setOf(
            "directory", "videos", "settings", "drops", "inventory", "subscriptions",
            "wallet", "search", "downloads", "jobs", "p", "u", "moderator", "popout",
            "friends", "messages", "prime", "turbo", "login", "signup", "activate"
        )

        fun buildTampermonkeyScript(topic: String): String = """
            // ==UserScript==
            // @name         Twitch Chat on Phone
            // @namespace    https://github.com/flxrs/DankChat
            // @version      1.0.0
            // @description  Watch Twitch on PC and automatically open the current channel's chat in the Android app. Based on DankChat; built 100% with AI.
            // @match        https://www.twitch.tv/*
            // @match        https://twitch.tv/*
            // @grant        GM_xmlhttpRequest
            // @connect      ntfy.sh
            // @run-at       document-idle
            // ==/UserScript==

            (function () {
                'use strict';

                const TOPIC = '$topic';
                const NTFY_URL = 'https://ntfy.sh/' + TOPIC;
                const RESERVED = new Set([
                    'directory', 'videos', 'settings', 'drops', 'inventory', 'subscriptions',
                    'wallet', 'search', 'downloads', 'jobs', 'p', 'u', 'friends', 'messages',
                    'prime', 'turbo', 'login', 'signup', 'activate', 'embed', 'products'
                ]);

                let lastSentChannel = '';
                let lastSentTime = 0;

                function extractChannel(pathname) {
                    const parts = pathname.split('/').filter(Boolean).map(p => p.toLowerCase());
                    if (parts.length === 0) return null;

                    if ((parts[0] === 'popout' || parts[0] === 'moderator') && parts.length >= 2) {
                        const candidate = parts[1];
                        return /^[a-z0-9_]{1,25}${'$'}/.test(candidate) && !RESERVED.has(candidate) ? candidate : null;
                    }

                    const first = parts[0];
                    if (RESERVED.has(first)) return null;
                    if (!/^[a-z0-9_]{1,25}${'$'}/.test(first)) return null;
                    return first;
                }

                function syncCurrentChannel(force = false) {
                    if (document.visibilityState === 'hidden') return;
                    const channel = extractChannel(window.location.pathname);
                    if (!channel) return;

                    const now = Date.now();
                    if (!force && channel === lastSentChannel && (now - lastSentTime) < 1500) {
                        return;
                    }

                    lastSentChannel = channel;
                    lastSentTime = now;

                    GM_xmlhttpRequest({
                        method: 'POST',
                        url: NTFY_URL,
                        data: channel,
                        headers: {
                            'Content-Type': 'text/plain;charset=UTF-8',
                            'Title': 'DankChat Stream Sync',
                            'Priority': 'default'
                        }
                    });
                }

                // Sync when tab becomes visible or window gains focus
                document.addEventListener('visibilitychange', () => {
                    if (document.visibilityState === 'visible') {
                        syncCurrentChannel(true);
                    }
                });
                window.addEventListener('focus', () => syncCurrentChannel(true));

                // Watch SPA URL navigation inside Twitch
                let lastHref = window.location.href;
                const observer = new MutationObserver(() => {
                    if (window.location.href !== lastHref) {
                        lastHref = window.location.href;
                        syncCurrentChannel(false);
                    }
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });

                window.addEventListener('popstate', () => syncCurrentChannel(false));

                // Initial sync when script loads on an active tab
                syncCurrentChannel(true);
            })();
        """.trimIndent()
    }
}


(function () {
    'use strict';

    const RESERVED = new Set([
        'directory', 'videos', 'settings', 'drops', 'inventory', 'subscriptions',
        'wallet', 'search', 'downloads', 'jobs', 'p', 'u', 'friends', 'messages',
        'prime', 'turbo', 'login', 'signup', 'activate', 'embed', 'products'
    ]);

    // Minimum interval before re-announcing the exact same channel (30 minutes)
    const SAME_CHANNEL_TTL_MS = 30 * 60 * 1000;
    let debounceTimer = null;

    function generateSyncId(length = 16) {
        const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
        const values = new Uint8Array(length);
        crypto.getRandomValues(values);
        return Array.from(values, (v) => chars[v % chars.length]).join('');
    }

    function extractChannel(pathname) {
        const parts = pathname.split('/').filter(Boolean);
        if (parts.length === 0) return null;

        if ((parts[0].toLowerCase() === 'popout' || parts[0].toLowerCase() === 'moderator') && parts.length >= 2) {
            const candidate = parts[1];
            return /^[A-Za-z0-9_]{1,25}$/.test(candidate) && !RESERVED.has(candidate.toLowerCase()) ? candidate : null;
        }

        const first = parts[0];
        if (RESERVED.has(first.toLowerCase())) return null;
        if (!/^[A-Za-z0-9_]{1,25}$/.test(first)) return null;
        return first;
    }

    function syncCurrentChannel() {
        if (document.visibilityState === 'hidden') return;
        const channel = extractChannel(window.location.pathname);
        if (!channel) return;

        chrome.storage.local.get(['syncId', 'lastSyncedChannel', 'lastSyncedTime'], (items) => {
            let syncId = items.syncId;
            if (!syncId) {
                syncId = generateSyncId(16);
                chrome.storage.local.set({ syncId });
            }

            const now = Date.now();
            const sameChannel =
                items.lastSyncedChannel &&
                items.lastSyncedChannel.toLowerCase() === channel.toLowerCase();
            const recentlySent =
                items.lastSyncedTime && (now - items.lastSyncedTime) < SAME_CHANNEL_TTL_MS;

            // Skip network request if this channel is already the active synced channel
            if (sameChannel && recentlySent) {
                return;
            }

            chrome.storage.local.set({
                lastSyncedChannel: channel,
                lastSyncedTime: now
            });

            const topic = 'tcop-' + syncId.trim();
            fetch('https://ntfy.sh/' + encodeURIComponent(topic), {
                method: 'POST',
                body: channel,
                headers: {
                    'Content-Type': 'text/plain;charset=UTF-8',
                    'Title': 'Twitch Chat on Phone'
                }
            }).catch(() => {});
        });
    }

    function scheduleSync() {
        if (debounceTimer) clearTimeout(debounceTimer);
        debounceTimer = setTimeout(syncCurrentChannel, 350);
    }

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            scheduleSync();
        }
    });
    window.addEventListener('focus', scheduleSync);

    // If user generates a new Sync ID while on an active Twitch tab, publish immediately
    chrome.storage.onChanged.addListener((changes, area) => {
        if (area === 'local' && changes.syncId && document.visibilityState === 'visible') {
            scheduleSync();
        }
    });

    let lastHref = window.location.href;
    const observer = new MutationObserver(() => {
        if (window.location.href !== lastHref) {
            lastHref = window.location.href;
            scheduleSync();
        }
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
    window.addEventListener('popstate', scheduleSync);

    scheduleSync();
})();

const SAME_CHANNEL_TTL_MS = 30 * 60 * 1000;

function generateSyncId(length = 16) {
    const chars = 'abcdefghijkmnopqrstuvwxyz023456789';
    const values = new Uint8Array(length);
    crypto.getRandomValues(values);
    return Array.from(values, (v) => chars[v % chars.length]).join('');
}

function ensureSyncId(callback) {
    chrome.storage.local.get(['syncId'], (items) => {
        if (items.syncId) {
            if (callback) callback(items.syncId);
        } else {
            const syncId = generateSyncId(16);
            chrome.storage.local.set({ syncId }, () => {
                if (callback) callback(syncId);
            });
        }
    });
}

function publishChannel(channel) {
    if (!channel) return;

    chrome.storage.local.get(['syncId', 'lastSyncedChannel', 'lastSyncedTime'], (items) => {
        const syncId = items.syncId || generateSyncId(16);
        if (!items.syncId) {
            chrome.storage.local.set({ syncId });
        }

        const now = Date.now();
        const sameChannel =
            items.lastSyncedChannel &&
            items.lastSyncedChannel.toLowerCase() === channel.toLowerCase();
        const recentlySent =
            items.lastSyncedTime && (now - items.lastSyncedTime) < SAME_CHANNEL_TTL_MS;

        if (sameChannel && recentlySent) {
            return;
        }

        const topic = 'tcop-' + syncId.trim();
        fetch('https://ntfy.sh/' + encodeURIComponent(topic), {
            method: 'POST',
            body: channel,
            headers: {
                'Content-Type': 'text/plain;charset=UTF-8',
                'Title': 'Twitch Chat on Phone'
            }
        }).then((response) => {
            if (response.ok) {
                chrome.storage.local.set({
                    lastSyncedChannel: channel,
                    lastSyncedTime: now
                });
            }
        }).catch(() => {});
    });
}

chrome.runtime.onMessage.addListener((message) => {
    if (message && message.type === 'SYNC_CHANNEL' && typeof message.channel === 'string') {
        publishChannel(message.channel);
    }
});

chrome.runtime.onInstalled.addListener(() => {
    ensureSyncId();
});

ensureSyncId();

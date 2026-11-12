function generateSyncId(length = 16) {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    const values = new Uint8Array(length);
    crypto.getRandomValues(values);
    return Array.from(values, (v) => chars[v % chars.length]).join('');
}

function ensureSyncId() {
    chrome.storage.local.get(['syncId'], (items) => {
        if (!items.syncId) {
            const syncId = generateSyncId(16);
            chrome.storage.local.set({ syncId });
        }
    });
}

chrome.runtime.onInstalled.addListener(() => {
    ensureSyncId();
});

ensureSyncId();


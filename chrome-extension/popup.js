function generateSyncId(length = 16) {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    const values = new Uint8Array(length);
    crypto.getRandomValues(values);
    return Array.from(values, (v) => chars[v % chars.length]).join('');
}

const syncIdEl = document.getElementById('syncId');
const copyBtn = document.getElementById('copyBtn');
const regenerateBtn = document.getElementById('regenerateBtn');
let copyTimer = null;

chrome.storage.local.get(['syncId'], (items) => {
    if (items.syncId) {
        syncIdEl.textContent = items.syncId;
    } else {
        const syncId = generateSyncId(16);
        chrome.storage.local.set({ syncId }, () => {
            syncIdEl.textContent = syncId;
        });
    }
});

copyBtn.addEventListener('click', () => {
    const value = syncIdEl.textContent.trim();
    if (!value || value === '...') return;
    navigator.clipboard.writeText(value).then(() => {
        copyBtn.classList.add('copied');
        if (copyTimer) clearTimeout(copyTimer);
        copyTimer = setTimeout(() => {
            copyBtn.classList.remove('copied');
        }, 1200);
    }).catch(() => {});
});

regenerateBtn.addEventListener('click', () => {
    const syncId = generateSyncId(16);
    chrome.storage.local.set({
        syncId,
        lastSyncedChannel: '',
        lastSyncedTime: 0
    }, () => {
        syncIdEl.textContent = syncId;
    });
});

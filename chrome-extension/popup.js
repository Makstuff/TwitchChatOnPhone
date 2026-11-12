const DEFAULT_TOPIC = 'dankchat-sync-mxmln-8f4a92c1';
const input = document.getElementById('topic');
const statusEl = document.getElementById('status');

chrome.storage.local.get({ syncTopic: DEFAULT_TOPIC }, (items) => {
    input.value = items.syncTopic || DEFAULT_TOPIC;
});

document.getElementById('save').addEventListener('click', () => {
    const syncTopic = input.value.trim() || DEFAULT_TOPIC;
    input.value = syncTopic;
    chrome.storage.local.set({ syncTopic }, () => {
        statusEl.textContent = 'Saved!';
        setTimeout(() => { statusEl.textContent = ''; }, 1500);
    });
});


(function () {
    'use strict';

    const DEFAULT_TOPIC = 'dankchat-sync-mxmln-8f4a92c1';
    const RESERVED = new Set([
        'directory', 'videos', 'settings', 'drops', 'inventory', 'subscriptions',
        'wallet', 'search', 'downloads', 'jobs', 'p', 'u', 'friends', 'messages',
        'prime', 'turbo', 'login', 'signup', 'activate', 'embed', 'products'
    ]);

    let lastSentChannel = '';
    let lastSentTime = 0;

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

    function syncCurrentChannel(force = false) {
        if (document.visibilityState === 'hidden') return;
        const channel = extractChannel(window.location.pathname);
        if (!channel) return;

        const now = Date.now();
        if (!force && channel.toLowerCase() === lastSentChannel.toLowerCase() && (now - lastSentTime) < 1500) {
            return;
        }

        lastSentChannel = channel;
        lastSentTime = now;

        chrome.storage.local.get({ syncTopic: DEFAULT_TOPIC }, (items) => {
            const topic = (items.syncTopic || DEFAULT_TOPIC).trim();
            if (!topic) return;
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

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') {
            syncCurrentChannel(true);
        }
    });
    window.addEventListener('focus', () => syncCurrentChannel(true));

    let lastHref = window.location.href;
    const observer = new MutationObserver(() => {
        if (window.location.href !== lastHref) {
            lastHref = window.location.href;
            syncCurrentChannel(false);
        }
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
    window.addEventListener('popstate', () => syncCurrentChannel(false));

    syncCurrentChannel(true);
})();


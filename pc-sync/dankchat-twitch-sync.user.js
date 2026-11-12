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

    const TOPIC = 'dankchat-sync-mxmln-8f4a92c1';
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
            return /^[a-z0-9_]{1,25}$/.test(candidate) && !RESERVED.has(candidate) ? candidate : null;
        }

        const first = parts[0];
        if (RESERVED.has(first)) return null;
        if (!/^[a-z0-9_]{1,25}$/.test(first)) return null;
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


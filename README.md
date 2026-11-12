# Twitch Chat on Phone

Watch Twitch on PC and automatically open the current channel's chat in the Android app.

This project is a minimal, single-channel fork based on [DankChat](https://github.com/flex3r/DankChat). All modifications to the Android app and the companion Chrome extension / Tampermonkey userscript were created **100% with AI**.

## Components
- **Android App:** Stripped-down single-channel Twitch chat client that automatically switches to whichever Twitch channel tab you most recently opened or focused on your PC.
- **Chrome Extension (`chrome-extension/`):** Browser addon that syncs the active Twitch channel tab to the phone app via `ntfy.sh`.
- **Tampermonkey Userscript (`pc-sync/dankchat-twitch-sync.user.js`):** Userscript alternative to the Chrome extension.

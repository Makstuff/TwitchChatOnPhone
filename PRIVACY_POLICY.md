# Privacy Policy — Twitch Chat on Phone

**Last updated:** November 12, 2026

This Privacy Policy applies to the **Twitch Chat on Phone** browser extension (for Chrome and Firefox) and the **Twitch Chat on Phone** Android companion app.

## 1. Data We Collect and Process

**We do not collect, store on our own servers, sell, or share any personally identifiable information, analytics, or advertising data.**

### Browser Extension
- **Sync ID (`storage` permission):** Upon installation, the extension generates a random 16-character alphanumeric Sync ID and stores it locally in your browser (`chrome.storage.local`). It also stores the last synced Twitch channel name and timestamp locally to avoid sending duplicate requests.
- **Active Twitch Channel (`twitch.tv`):** When you visit or switch to a Twitch tab, the extension reads only the channel name from the URL path (for example, `twitch.tv/streamername`). It does not read chat messages, account details, passwords, or browsing history from non-Twitch sites.
- **Channel Sync (`ntfy.sh`):** The extension sends an HTTP `POST` request containing only the active Twitch channel name to `https://ntfy.sh/tcop-<your-sync-id>` so your Android companion app can open that channel's chat.

### Android Companion App
- **Sync ID & Local Preferences:** Your Sync ID, recently opened channel names, and app settings are stored locally on your device.
- **Third-Party Services:**
  - **ntfy.sh:** Used to receive the synced channel name associated with your Sync ID. See [ntfy.sh's Privacy Policy](https://ntfy.sh/docs/privacy/).
  - **Twitch:** When you view chat or log in, the app connects directly to Twitch's servers. See [Twitch's Privacy Notice](https://www.twitch.tv/p/legal/privacy-notice/).
  - **Recent Messages (optional):** If enabled, the app fetches recent chat history for the active channel from `recent-messages.robotty.de`. You can opt out at any time in Settings.

## 2. Data Retention and Deletion

- **Browser Extension:** All stored data remains locally in your browser and is deleted when you uninstall the extension. You can also generate a new Sync ID at any time from the extension popup.
- **Android App:** All local data is removed when you clear the app's storage or uninstall the app.

## 3. Contact

If you have any questions about this Privacy Policy, please contact:
**contact.makstuff@outlook.com**

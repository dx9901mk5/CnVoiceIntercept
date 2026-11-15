### 中文說明 (Chinese Release Notes)
- **首發版本 v1.0.0**：專為 **vivo (OriginOS — 藍心小V)**、**OPPO / 一加 / Realme (ColorOS — 小布助手)**、**小米 / Redmi (HyperOS — 小愛同學)** 陸版 CN ROM 打造的語音喚醒攔截工具（免 Root，需搭配 **Shizuku**）。
- **三語系介面**：完整支援 **繁體中文、簡體中文、English**（支援跟隨系統語言，亦可於 App 右上角「語言 / Lang」一鍵切換）。
- **只攔截語音呼叫，不干涉手動點開**：僅在「語音呼喊喚醒」產生收音狀態通知時觸發攔截；平時在桌面手動點開藍心小V、小布助手或小愛同學 App 打字或使用內建功能時完全不受影響。
- **預設 VISTrigger 喚起 Gemini + 自選 AI App**：預設透過 `IVoiceInteractionManagerService.showSessionFromSession` 直接彈出 Gemini 圈選／浮層對話視窗；亦可自由切換為啟動 ChatGPT、Claude、Perplexity、Copilot、豆包、Kimi 或手機內任何已安裝 App。
- **置頂系統預設數位助理設定捷徑** 與 **⚡ 一鍵透過 Shizuku 授予所有 ADB 權限**。
- **Material Design 3 動態主題**、**關於我 / GitHub 連結**、**自動更新檢查通知** 與 **固定簽章（可直接覆蓋更新）**。

---

### English Release Notes
- **Initial Release v1.0.0**: Rootless voice wake-up interceptor built for **vivo (OriginOS)**, **OPPO / OnePlus / Realme (ColorOS)**, and **Xiaomi / Redmi (HyperOS)** Mainland China (CN) ROMs (**Shizuku required**).
- **Trilingual UI**: Full **Traditional Chinese, Simplified Chinese, and English** interface (follows system locale + in-app language switcher in the top bar).
- **Voice Wake-Up Only**: Intercepts voice wake-up recording notifications only—opening the stock Chinese assistant manually from your home screen works normally without interference.
- **Gemini via VISTrigger + Custom AI App Picker**: Summons the Gemini overlay / Circle to Search window via `VISTrigger` by default, or launches any custom AI app (ChatGPT, Claude, Perplexity, Copilot, Doubao, Kimi, or any installed app).
- **Top Shortcut to Android Default Digital Assistant Settings** & **⚡ One-Tap ADB Permission Grant via Shizuku**.
- **Material Design 3 UI**, **About / GitHub Link**, **Automatic Update Notification**, and **Fixed APK Signing** for direct overwrite updates.

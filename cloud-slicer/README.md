# DualBoost Cloud Slicer (Cloudflare Worker)

Accelerate file downloads from non-range servers by transforming single-stream downloads into parallel `HTTP 206 Partial Content` byte ranges.

---

## ⚡ 60-Second Free Setup

1. **Sign Up / Log In**:
   - Go to [dash.cloudflare.com](https://dash.cloudflare.com) (100% Free plan, 100,000 requests/day, no credit card required).

2. **Create a Worker**:
   - In the left sidebar, click **Compute (Workers & Pages)** > **Create application** > **Create Worker**.
   - Name it (e.g. `dualboost-slicer`), then click **Deploy**.

3. **Paste the Code**:
   - Click **Edit Code**.
   - Replace the default code with the contents of [`worker.js`](worker.js).
   - Click **Deploy** in the top right.

4. **Copy Your URL**:
   - Copy your worker URL (e.g. `https://dualboost-slicer.your-subdomain.workers.dev`).

5. **(Optional) Configure Secret Key**:
   - In your Worker page, go to **Settings** > **Variables and Secrets**.
   - Add a Variable named `SECRET_KEY` with your chosen secret phrase (e.g. `myDualBoostSecret`).
   - Click **Save and Deploy**.

6. **Connect in DualBoost Downloader**:
   - Open DualBoost Downloader on your phone.
   - Tap the **Settings (⚙️)** icon in the top right.
   - Paste your Worker URL and optional Secret Key.
   - Tap **Test Worker** to verify connection, then tap **Save Settings**.

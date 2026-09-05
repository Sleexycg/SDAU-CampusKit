package com.sdau.campuskit

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resolves the QR image produced by the CCB payment page.
 *
 * The school endpoint returns a browser page URL rather than the QR image itself.
 * CCB creates the image after JavaScript and cookies have been initialized, so a
 * real WebView must execute the page before the image request can be captured.
 */
internal class DormPaymentQrResolver(private val activity: Activity) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var host: FrameLayout? = null
    private var webView: WebView? = null
    private var completion: ((Result<DormRechargeQr>) -> Unit)? = null
    private var amount = 0.0
    private var pageUrl = ""
    private var userAgent = ""
    private var completed = false
    private val downloading = AtomicBoolean(false)

    fun resolve(
        payment: DormRechargePayment,
        onResult: (Result<DormRechargeQr>) -> Unit
    ) {
        check(Looper.myLooper() == Looper.getMainLooper())
        cancelCurrent()
        val token = ++generation
        completion = onResult
        amount = payment.amount
        pageUrl = normalizePaymentUrl(payment.paymentUrl)
        completed = false
        downloading.set(false)

        val container = activity.findViewById<ViewGroup>(android.R.id.content)
        val hiddenHost = FrameLayout(activity).apply {
            alpha = 0.01f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isClickable = false
            isFocusable = false
        }
        val browser = WebView(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.setSupportMultipleWindows(false)
        }
        userAgent = browser.settings.userAgentString.orEmpty()
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(browser, true)
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val candidate = request?.url?.toString().orEmpty()
                if (isQrCandidate(candidate)) {
                    return interceptQrRequest(
                        token = token,
                        candidate = candidate,
                        referer = request?.requestHeaders?.get("Referer") ?: pageUrl,
                        requestHeaders = request?.requestHeaders.orEmpty()
                    )
                }
                return null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (token != generation || completed) return
                url?.takeIf(::isQrCandidate)?.let {
                    downloadCandidate(token, it, pageUrl, emptyMap())
                }
                inspectDocument(token)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                if (token != generation || completed) return
                url?.takeIf(::isQrCandidate)?.let {
                    downloadCandidate(token, it, view?.url ?: pageUrl, emptyMap())
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && token == generation && !completed) {
                    finish(
                        token,
                        Result.failure(
                            IllegalStateException("学校缴费页面加载失败，请检查网络后重试")
                        )
                    )
                }
            }
        }
        browser.webChromeClient = WebChromeClient()
        hiddenHost.addView(
            browser,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        // Keep the WebView attached and inside the viewport so Chromium does not
        // suspend rendering, but place it behind the app's opaque Compose root.
        container.addView(
            hiddenHost,
            0,
            ViewGroup.LayoutParams(dp(420), dp(760))
        )
        host = hiddenHost
        webView = browser
        browser.loadUrl(pageUrl)

        val poll = object : Runnable {
            override fun run() {
                if (token != generation || completed) return
                inspectDocument(token)
                mainHandler.postDelayed(this, DOCUMENT_POLL_INTERVAL_MS)
            }
        }
        mainHandler.postDelayed(poll, DOCUMENT_POLL_INTERVAL_MS)
        mainHandler.postDelayed({
            if (token == generation && !completed) {
                finish(
                    token,
                    Result.failure(
                        IllegalStateException("未能从学校缴费页面读取充值二维码，请稍后重试")
                    )
                )
            }
        }, RESOLVE_TIMEOUT_MS)
    }

    fun dispose() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cancelCurrent()
        } else {
            mainHandler.post(::cancelCurrent)
        }
        downloadExecutor.shutdownNow()
    }

    fun cancel() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cancelCurrent()
        } else {
            mainHandler.post(::cancelCurrent)
        }
    }

    private fun inspectDocument(token: Int) {
        val browser = webView ?: return
        if (token != generation || completed) return
        browser.evaluateJavascript(DOCUMENT_SCAN_SCRIPT) { rawResult ->
            if (token != generation || completed || rawResult.isNullOrBlank() || rawResult == "null") {
                return@evaluateJavascript
            }
            val encoded = runCatching {
                JSONObject("{\"value\":$rawResult}").optString("value")
            }.getOrNull().orEmpty()
            val candidates = runCatching { JSONArray(encoded) }.getOrNull() ?: return@evaluateJavascript
            for (index in 0 until candidates.length()) {
                val candidate = candidates.optString(index).trim()
                when {
                    candidate.startsWith("data:image/", ignoreCase = true) -> {
                        decodeDataImage(candidate)?.let { bytes ->
                            if (isValidImage(bytes)) {
                                finish(token, Result.success(DormRechargeQr(bytes, amount)))
                                return@evaluateJavascript
                            }
                        }
                    }

                    isQrCandidate(candidate) -> {
                        downloadCandidate(token, candidate, browser.url ?: pageUrl, emptyMap())
                    }
                }
            }
        }
    }

    private fun downloadCandidate(
        token: Int,
        candidate: String,
        referer: String,
        requestHeaders: Map<String, String>
    ) {
        if (token != generation || completed || !downloading.compareAndSet(false, true)) return
        downloadExecutor.execute {
            val result = runCatching {
                downloadImage(candidate, referer, requestHeaders)
            }
            if (result.isSuccess) {
                finish(token, Result.success(DormRechargeQr(result.getOrThrow(), amount)))
            } else {
                downloading.set(false)
            }
        }
    }

    /**
     * Read the original QR request instead of issuing a second request after the
     * page has consumed it. Some CCB sessions make the QR resource effectively
     * one-shot; returning the same bytes also lets the payment page finish normally.
     */
    private fun interceptQrRequest(
        token: Int,
        candidate: String,
        referer: String,
        requestHeaders: Map<String, String>
    ): WebResourceResponse? {
        if (token != generation || completed || !downloading.compareAndSet(false, true)) return null
        return runCatching {
            val bytes = downloadImage(candidate, referer, requestHeaders)
            finish(token, Result.success(DormRechargeQr(bytes, amount)))
            WebResourceResponse(
                "image/png",
                null,
                ByteArrayInputStream(bytes)
            )
        }.onFailure {
            downloading.set(false)
        }.getOrNull()
    }

    private fun downloadImage(
        candidate: String,
        referer: String,
        requestHeaders: Map<String, String>
    ): ByteArray {
        val normalizedCandidate = candidate.replace("&amp;", "&")
        val connection = (URL(normalizedCandidate).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            setRequestProperty("User-Agent", userAgent)
            if (referer.isNotBlank()) setRequestProperty("Referer", referer)
            CookieManager.getInstance().getCookie(normalizedCandidate)?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Cookie", it)
            }
            requestHeaders.forEach { (name, value) ->
                if (!name.equals("Cookie", true) &&
                    !name.equals("Host", true) &&
                    !name.equals("User-Agent", true) &&
                    !name.equals("Referer", true) &&
                    !name.equals("Accept-Encoding", true) &&
                    !name.equals("Connection", true)
                ) {
                    runCatching { setRequestProperty(name, value) }
                }
            }
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("充值二维码下载失败（$responseCode）")
            }
            connection.inputStream.use { it.readBytes() }.also { bytes ->
                if (!isValidImage(bytes)) throw IllegalStateException("缴费页面返回的二维码图片无效")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeDataImage(value: String): ByteArray? {
        val marker = ";base64,"
        val index = value.indexOf(marker, ignoreCase = true)
        if (index < 0) return null
        return runCatching {
            Base64.decode(value.substring(index + marker.length), Base64.DEFAULT)
        }.getOrNull()
    }

    private fun isValidImage(bytes: ByteArray): Boolean {
        if (bytes.size < 128) return false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null
    }

    private fun isQrCandidate(value: String): Boolean {
        val normalized = value.replace("\\/", "/").replace("&amp;", "&")
        return normalized.contains("QrcodeServlet", ignoreCase = true)
    }

    /**
     * The school uses JavaScript escape(), so the signed CCB URL may contain raw
     * `%uXXXX` sequences. Chromium navigation encodes the percent sign as `%25`;
     * doing it explicitly avoids Android versions that reject the malformed URI.
     * Already-normalized `%25uXXXX` sequences are left untouched.
     */
    private fun normalizePaymentUrl(value: String): String = value
        .trim()
        .replace(Regex("%u([0-9a-fA-F]{4})")) { match -> "%25u${match.groupValues[1]}" }
        .replace(" ", "%20")

    private fun finish(token: Int, result: Result<DormRechargeQr>) {
        mainHandler.post {
            if (token != generation || completed) return@post
            completed = true
            val callback = completion
            completion = null
            removeWebView()
            callback?.invoke(result)
        }
    }

    private fun cancelCurrent() {
        generation++
        completed = true
        completion = null
        removeWebView()
        downloading.set(false)
    }

    private fun removeWebView() {
        val browser = webView
        webView = null
        browser?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        host?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
        }
        host = null
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        private const val DOCUMENT_POLL_INTERVAL_MS = 450L
        private const val RESOLVE_TIMEOUT_MS = 30_000L

        private val DOCUMENT_SCAN_SCRIPT = """
            (function() {
              const output = [];
              const add = value => {
                if (value && output.indexOf(value) < 0) output.push(value);
              };
              const scan = doc => {
                try {
                  doc.querySelectorAll('img').forEach(img => {
                    add(img.currentSrc || img.src);
                    try {
                      const width = img.naturalWidth || img.width;
                      const height = img.naturalHeight || img.height;
                      if (img.complete && width >= 100 && width <= 600 &&
                          Math.abs(width - height) <= 4) {
                        const canvas = doc.createElement('canvas');
                        canvas.width = width;
                        canvas.height = height;
                        canvas.getContext('2d').drawImage(img, 0, 0, width, height);
                        add(canvas.toDataURL('image/png'));
                      }
                    } catch (_) {}
                  });
                  doc.querySelectorAll('canvas').forEach(canvas => {
                    try {
                      if (canvas.width >= 100 && canvas.width <= 600 &&
                          Math.abs(canvas.width - canvas.height) <= 4) {
                        add(canvas.toDataURL('image/png'));
                      }
                    } catch (_) {}
                  });
                  doc.querySelectorAll('*').forEach(element => {
                    try {
                      const background = doc.defaultView.getComputedStyle(element).backgroundImage || '';
                      const matches = background.match(/url\(["']?([^"')]+)["']?\)/g) || [];
                      matches.forEach(match => add(match.replace(/^url\(["']?/, '').replace(/["']?\)$/, '')));
                    } catch (_) {}
                  });
                  doc.querySelectorAll('iframe').forEach(frame => {
                    try { scan(frame.contentDocument); } catch (_) {}
                  });
                  try {
                    const html = doc.documentElement ? doc.documentElement.innerHTML : '';
                    const matches = html.match(/(?:https?:\\/\\/[^\"'<>\\s]+)?\\/CCBIS\\/QrcodeServlet[^\"'<>\\s]*/ig) || [];
                    matches.forEach(value => {
                      try { add(new URL(value.replace(/&amp;/g, '&'), doc.baseURI).href); }
                      catch (_) { add(value); }
                    });
                  } catch (_) {}
                } catch (_) {}
              };
              scan(document);
              return JSON.stringify(output);
            })();
        """.trimIndent()
    }
}

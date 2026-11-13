package ru.tolmachev.ogetrainer.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private const val TAG = "OgeMarkdown"

/**
 * Единственный рендерер markdown + LaTeX (решение гейта G1: WebView с офлайн-ассетами KaTeX).
 *
 * Ловушки, проверенные на устройстве:
 * 1. `file://`-страница не грузит относительные скрипты с API 30 → HTML передаётся через
 *    `loadDataWithBaseURL` с базой `file:///android_asset/render/`.
 * 2. `evaluateJavascript` из `update` приходит раньше window.load → текст хранится в теге
 *    WebView, рендер повторяется в `onPageFinished`, страница ставит вызов в очередь.
 * 3. JS-мост не приводит float к Int → `Math.round(h)` в JS и `onHeight(px: Double)` в Kotlin.
 * 4. Колбэк моста приходит не в главном потоке → запись состояния через Handler(main).
 * 5. WebView с высотой 1 px не раскладывает страницу → до измерения окно 600 dp.
 * 6. CSS-пиксели уже равны dp при viewport=device-width → `.dp`, без деления на плотность.
 * 7. Высота меняется после подстановки шрифтов KaTeX → ResizeObserver + document.fonts.ready.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownView(
    markdown: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
    baseFontPx: Int = 16,
) {
    var contentHeightPx by remember { mutableIntStateOf(0) }

    // Тема входит в key: при переключении оформления страница пересоздаётся с нужной палитрой.
    key(dark) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .height(if (contentHeightPx > 0) contentHeightPx.dp else 600.dp),
            factory = { context ->
                val template = context.assets.open("render/template.html")
                    .bufferedReader().use { it.readText() }
                WebView(context).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.domStorageEnabled = false
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = false
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    addJavascriptInterface(
                        HeightBridge { px ->
                            Handler(Looper.getMainLooper()).post {
                                if (px > 0) contentHeightPx = px
                            }
                        },
                        "Android",
                    )
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            Log.d(TAG, "${message.message()} @${message.lineNumber()}")
                            return true
                        }
                    }
                    tag = Triple(baseFontPx, markdown, dark)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val payload = tag as? Triple<*, *, *> ?: return
                            val text = payload.second as? String ?: return
                            val isDark = payload.third as? Boolean ?: true
                            view?.evaluateJavascript(
                                "window.setTheme('${if (isDark) "dark" else "light"}');" +
                                    "window.renderMarkdown(${JSONObject.quote(text)});",
                                null,
                            )
                        }
                    }
                    loadDataWithBaseURL(
                        "file:///android_asset/render/",
                        template,
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            update = { web ->
                web.tag = Triple(baseFontPx, markdown, dark)
                web.evaluateJavascript(
                    "window.setTheme('${if (dark) "dark" else "light"}');" +
                        "window.renderMarkdown(${JSONObject.quote(markdown)});",
                    null,
                )
            },
        )
    }
}

private class HeightBridge(private val onHeight: (Int) -> Unit) {
    @JavascriptInterface
    fun onHeight(px: Double) {
        onHeight(px.toInt())
    }
}

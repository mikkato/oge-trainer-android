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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

private const val TAG = "OgeMarkdown"

/**
 * Единственный рендерер markdown + LaTeX (решение гейта G1: WebView с офлайн-ассетами KaTeX).
 *
 * Ловушки, проверенные на устройстве:
 * 1. `file://`-страница по умолчанию не грузит относительные скрипты (allowFileAccessFromFileURLs
 *    с API 30 выключен), поэтому HTML передаётся через `loadDataWithBaseURL` с базой
 *    `file:///android_asset/render/`, а file-доступ включён явно.
 * 2. `evaluateJavascript` из `update` приходит раньше window.load — вызов теряется; текст хранится
 *    в теге WebView и рендер повторяется в `onPageFinished`. Страница тоже ставит вызов в очередь.
 * 3. Высота контента возвращается из JS-моста в пикселях, иначе WebView схлопывается в один экран.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    baseFontPx: Int = 16,
) {
    var contentHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            // JS отдаёт CSS-пиксели, а 1 CSS px в WebView с viewport=device-width равен 1 dp:
            // делить на плотность ещё раз нельзя, иначе карточка сжимается в одну строку.
            .height(if (contentHeightPx > 0) contentHeightPx.dp else 600.dp),
        factory = { context ->
            val template = context.assets.open("render/template.html")
                .bufferedReader().use { it.readText() }
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.loadsImagesAutomatically = true
                settings.domStorageEnabled = false
                // Без этих двух строк WebView берёт layout viewport 980 px: текст переносится по
                // нему и обрезается по правому краю реального окна.
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
                        // Мост вызывает колбэк из JavaBridge-потока: писать в состояние Compose
                        // оттуда нельзя (падает "Java exception was raised during method invocation").
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
                tag = baseFontPx to markdown
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val payload = tag as? Pair<*, *> ?: return
                        val text = payload.second as? String ?: return
                        view?.evaluateJavascript(
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
            web.tag = baseFontPx to markdown
            web.evaluateJavascript("window.renderMarkdown(${JSONObject.quote(markdown)});", null)
        },
    )
}

private class HeightBridge(private val onHeight: (Int) -> Unit) {
    @JavascriptInterface
    fun onHeight(px: Double) {
        onHeight(px.toInt())
    }
}

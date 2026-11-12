package ai.bureadcar.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** GET returning the body. Errors never include the URL, because URLs carry API keys. */
suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    currentCoroutineContext().ensureActive()
    val connection = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "*/*")
        }
    } catch (e: Exception) {
        throw IOException("연결 실패")
    }
    try {
        val code = try {
            connection.responseCode
        } catch (e: Exception) {
            throw IOException("연결 실패")
        }
        if (code != 200) throw IOException("HTTP $code")
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.also {
            currentCoroutineContext().ensureActive()
        }
    } finally {
        connection.disconnect()
    }
}

fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

/** Rows of a data.go.kr XML response. Fails when the API reports an error. */
fun parseRtmsItems(xml: String): List<Map<String, String>> = parseRtmsPage(xml).rows

fun parseRtmsPage(xml: String): ApiPage<Map<String, String>> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(InputSource(xml.reader()))
    val codes = document.getElementsByTagName("resultCode")
    if (codes.length == 0) throw IOException("실거래 API 응답 형식 오류")
    if (codes.length > 0) {
        val code = codes.item(0).textContent.trim()
        if (code != "000" && code != "00") throw IOException("API 오류 $code")
    }
    val items = document.getElementsByTagName("item")
    val rows = List(items.length) { i ->
        val children = (items.item(i) as Element).childNodes
        buildMap {
            for (c in 0 until children.length) {
                val child = children.item(c)
                if (child is Element) put(child.tagName, child.textContent.trim())
            }
        }
    }
    val totals = document.getElementsByTagName("totalCount")
    val total = if (totals.length > 0) totals.item(0).textContent.trim().toIntOrNull()
        ?: throw IOException("실거래 API 건수 형식 오류") else null
    return ApiPage(rows, total)
}

package ai.bureadcar.app.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject

/** Never turn a cancelled request into an empty result or a playable error segment. */
suspend fun <T> apiResult(block: suspend () -> T): Result<T> = try {
    currentCoroutineContext().ensureActive()
    Result.success(block()).also { currentCoroutineContext().ensureActive() }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}

data class ApiPage<T>(val rows: List<T>, val total: Int?)

/** Refuse incomplete results rather than announcing a partial count as the whole district. */
suspend fun <T> collectApiPages(pageSize: Int, load: suspend (Int) -> ApiPage<T>): List<T> {
    require(pageSize > 0)
    val result = mutableListOf<T>()
    var expected: Int? = null
    for (page in 1..100) {
        currentCoroutineContext().ensureActive()
        val response = load(page)
        val total = response.total
        if (total != null) {
            if (total < 0 || (expected != null && expected != total)) throw IOException("조회 중 데이터 건수가 변경됐습니다")
            expected = total
        }
        result += response.rows
        if (expected != null) {
            if (result.size == expected) return result
            if (result.size > expected || response.rows.isEmpty()) throw IOException("데이터 일부를 받지 못했습니다")
        } else if (response.rows.size < pageSize) return result
    }
    throw IOException("조회 가능한 페이지 수를 초과했습니다")
}

fun parseOdcloudPage(json: String): ApiPage<Map<String, Any?>> {
    val root = JSONObject(json)
    val code = root.optInt("code", 0)
    if (code != 0) throw IOException("청약 API 조회 실패")
    val data = root.optJSONArray("data") ?: throw IOException("청약 API 응답 형식 오류")
    val rows = List(data.length()) { i ->
        val row = data.getJSONObject(i)
        row.keys().asSequence().associateWith { key -> row.opt(key).takeUnless { it == JSONObject.NULL } }
    }
    // totalCount is unfiltered; matchCount is the total for the requested conditions.
    val total = if (root.has("matchCount")) root.getInt("matchCount") else null
    return ApiPage(rows, total)
}

fun parseRoneRows(json: String, name: String): List<JSONObject> {
    val root = JSONObject(json)
    root.optJSONObject("RESULT")?.let { result ->
        if (result.optString("CODE") == "INFO-200") return emptyList()
        throw IOException("주택가격지수 API 조회 실패")
    }
    val sections = root.optJSONArray(name) ?: throw IOException("주택가격지수 API 응답 형식 오류")
    var data: JSONArray? = null
    for (i in 0 until sections.length()) {
        val section = sections.getJSONObject(i)
        section.optJSONArray("head")?.let { heads ->
            for (j in 0 until heads.length()) {
                val code = heads.getJSONObject(j).optJSONObject("RESULT")?.optString("CODE")
                if (code == "INFO-200") return emptyList()
                if (code != null && code != "INFO-000") throw IOException("주택가격지수 API 조회 실패")
            }
        }
        section.optJSONArray("row")?.let { data = it }
    }
    val rows = data ?: throw IOException("주택가격지수 API 데이터 누락")
    return List(rows.length()) { rows.getJSONObject(it) }
}

fun parseEcosRows(json: String): List<Pair<String, Double>> {
    val root = JSONObject(json)
    root.optJSONObject("RESULT")?.let { result ->
        if (result.optString("CODE") == "INFO-200") return emptyList()
        throw IOException("금리 API 조회 실패")
    }
    val data = root.optJSONObject("StatisticSearch")?.optJSONArray("row")
        ?: throw IOException("금리 API 응답 형식 오류")
    return List(data.length()) { i ->
        val row = data.getJSONObject(i)
        row.getString("TIME") to (row.getString("DATA_VALUE").toDoubleOrNull()
            ?: throw IOException("금리 API 수치 형식 오류"))
    }
}

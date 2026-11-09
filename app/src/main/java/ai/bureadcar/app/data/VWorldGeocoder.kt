package ai.bureadcar.app.data

import ai.bureadcar.app.BuildConfig
import ai.bureadcar.app.model.Place
import ai.bureadcar.app.model.Region
import org.json.JSONObject

/**
 * Coordinate → si/gun/gu. V-World's terms forbid storing results, so nothing here is cached
 * beyond the current drive.
 */
class VWorldGeocoder {
    suspend fun region(place: Place): Region? {
        val url = "https://api.vworld.kr/req/address?service=address&request=getAddress&version=2.0" +
            "&crs=epsg:4326&point=${place.lng},${place.lat}&format=json&type=PARCEL&zipcode=false" +
            "&simple=false&key=${BuildConfig.VWORLD_API_KEY}"
        return parseRegion(httpGet(url))
    }
}

fun parseRegion(json: String): Region? {
    val response = JSONObject(json).getJSONObject("response")
    if (response.optString("status") != "OK") return null
    val structure = response.getJSONArray("result").getJSONObject(0).getJSONObject("structure")
    val code = structure.optString("level4LC")
    if (code.length < 5) return null
    val sido = structure.optString("level1")
    val level2 = structure.optString("level2")
    val level3 = structure.optString("level3")
    val sigungu = listOf(level2, level3).filter { it.isNotBlank() }.joinToString(" ").ifBlank { sido }
    return Region(lawdCode = code.take(5), sido = sido, sigungu = sigungu)
}

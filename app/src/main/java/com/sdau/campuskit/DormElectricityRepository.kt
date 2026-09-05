package com.sdau.campuskit

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class DormElectricityOption(
    val label: String,
    val code: String
)

internal data class DormElectricityReading(
    val remainingKwh: Double,
    val supplyStatus: String,
    val location: String
)

internal data class DormRechargeQr(
    val imageBytes: ByteArray,
    val amount: Double
)

internal data class DormRechargePayment(
    val paymentUrl: String,
    val amount: Double
)

internal object DormElectricityPolicy {
    val defaultTypes = listOf(
        DormElectricityOption("照明用电", "1"),
        DormElectricityOption("空调用电", "2")
    )

    fun equipmentTypes(campusCode: String, buildingName: String): List<DormElectricityOption> {
        val building = normalizeBuilding(buildingName)
        val combined = campusCode == "2" && building in setOf(
            "4号楼", "5号楼", "6号楼", "7号楼", "8号楼", "17号楼"
        ) || campusCode == "3" && building in setOf(
            "1号楼", "2号楼", "3号楼", "4号楼", "5号楼", "6号楼",
            "7号楼", "8号楼", "9号楼", "10号楼", "11号楼", "12号楼"
        )
        return if (combined) {
            listOf(DormElectricityOption("空调照明", "1"))
        } else {
            defaultTypes
        }
    }

    fun queryEquipmentCode(
        campusCode: String,
        buildingName: String,
        roomName: String,
        selectedCode: String
    ): String {
        val building = normalizeBuilding(buildingName)
        val room = roomName.trim()
        if (campusCode == "2" && building in setOf(
                "4号楼", "5号楼", "6号楼", "7号楼", "8号楼", "17号楼"
            )
        ) {
            return "1"
        }
        if (campusCode == "3") {
            if (
                room in setOf("901", "902") ||
                building in setOf("7号楼", "10号楼") && room == "903" ||
                building == "2号楼" && room in setOf("801", "802")
            ) {
                return "1"
            }
            if (building in setOf(
                    "1号楼", "2号楼", "3号楼", "4号楼", "5号楼", "6号楼",
                    "7号楼", "9号楼", "10号楼", "11号楼", "12号楼"
                )
            ) {
                return "2"
            }
            if (building == "8号楼") return "1"
        }
        return selectedCode
    }

    private fun normalizeBuilding(value: String): String = value.trim().replace(" ", "")
}

internal class DormElectricityRepository {
    private val baseUrl = "http://gysd.sdau.edu.cn/mrest"
    private val accessToken = "qsiwoapc3o34ms1ms34ll44psca908kd"

    fun loadCampuses(): List<DormElectricityOption> =
        loadOptions("getJQueryXq.do", emptyMap(), "xq")

    fun loadBuildings(campusCode: String): List<DormElectricityOption> =
        loadOptions("getJQueryLh.do", mapOf("code" to campusCode), "lh")

    fun loadRooms(buildingCode: String): List<DormElectricityOption> =
        loadOptions("getJQueryFjh.do", mapOf("code" to buildingCode), "fjh")

    fun query(
        campus: DormElectricityOption,
        building: DormElectricityOption,
        room: DormElectricityOption,
        equipment: DormElectricityOption
    ): DormElectricityReading {
        val equipmentCode = DormElectricityPolicy.queryEquipmentCode(
            campus.code,
            building.label,
            room.label,
            equipment.code
        )
        val json = post(
            "ndDocumentdl2.do",
            linkedMapOf(
                "xq" to campus.label,
                "lh" to building.label,
                "fjh" to room.label,
                "equipment" to equipmentCode
            )
        )
        if (!json.optBoolean("success")) {
            throw IllegalStateException(json.optString("msg").ifBlank { "供电系统暂时无法查询" })
        }
        val result = json.optJSONObject("result")
            ?: throw IllegalStateException("供电系统未返回电量数据")
        val remaining = result.optDouble("sydl", Double.NaN)
        if (remaining.isNaN()) throw IllegalStateException("剩余电量数据异常")
        return DormElectricityReading(
            remainingKwh = remaining,
            supplyStatus = result.optString("gdzt").ifBlank { "状态未知" },
            location = result.optString("ssh").ifBlank {
                "${campus.label}-${building.label}-${room.label}"
            }
        )
    }

    fun createRechargePayment(
        campus: DormElectricityOption,
        building: DormElectricityOption,
        room: DormElectricityOption,
        equipment: DormElectricityOption,
        amount: Double
    ): DormRechargePayment {
        require(amount > 0.0 && amount <= 100.0) { "充值金额须大于 0 且不超过 100 元" }
        val equipmentCode = (equipment.code.toIntOrNull()?.minus(1) ?: 0).coerceAtLeast(0)
        val description = "${campus.label}${building.label}${room.label}${equipment.label}缴费"
        val json = post(
            "payccb.do",
            linkedMapOf(
                "REGINFO" to javascriptEscape("山东农业大学"),
                "PROINFO" to javascriptEscape(description),
                "xqname" to campus.label,
                "lhname" to building.label,
                "fjhname" to room.label,
                "equipment" to equipmentCode.toString(),
                "money" to String.format(Locale.US, "%.2f", amount)
            )
        )
        if (!json.optBoolean("success")) {
            throw IllegalStateException(json.optString("msg").ifBlank { "暂时无法创建充值订单" })
        }
        val paymentUrl = json.optString("result").trim()
        if (paymentUrl.isBlank()) throw IllegalStateException("学校缴费平台未返回支付链接")
        return DormRechargePayment(paymentUrl, amount)
    }

    private fun loadOptions(
        path: String,
        parameters: Map<String, String>,
        labelKey: String
    ): List<DormElectricityOption> {
        val json = post(path, parameters)
        if (!json.optBoolean("success")) {
            throw IllegalStateException(json.optString("msg").ifBlank { "供电系统数据加载失败" })
        }
        val array = json.optJSONArray("result") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val label = item.optString(labelKey).trim()
                val code = item.optString("code").trim()
                if (label.isNotEmpty() && code.isNotEmpty()) {
                    add(DormElectricityOption(label, code))
                }
            }
        }
    }

    private fun post(path: String, parameters: Map<String, String>): JSONObject {
        val authenticatedParameters = linkedMapOf(
            "token" to accessToken,
            "username" to "admin"
        ).apply { putAll(parameters) }
        val body = authenticatedParameters.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.toByteArray(StandardCharsets.UTF_8)
        val connection = (URL("$baseUrl/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "WeSDAU-Android")
        }
        return try {
            connection.outputStream.use { it.write(body) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                throw IllegalStateException("供电系统请求失败（$responseCode）")
            }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun javascriptEscape(value: String): String = buildString {
        value.forEach { character ->
            when {
                character.isLetterOrDigit() && character.code < 128 -> append(character)
                character in "@*_+-./" -> append(character)
                character.code < 256 -> append("%%%02X".format(Locale.US, character.code))
                else -> append("%%u%04X".format(Locale.US, character.code))
            }
        }
    }

    private fun downloadPaymentQr(paymentUrl: String): ByteArray {
        val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
        // These browser-style %uXXXX values are covered by CCB's MAC. Keep the
        // school-provided URL intact and only sanitize the URI used for cookies.
        val paymentPage = getWebResponse(URL(paymentUrl), cookieManager)
        val source = findQrSource(decodePaymentHtml(paymentPage.bytes))
            ?: throw IllegalStateException("未能从学校缴费页面读取充值二维码")
        val bytes = getWebResponse(
            url = URL(paymentPage.finalUrl, source),
            cookieManager = cookieManager,
            referer = paymentPage.finalUrl
        ).bytes
        if (bytes.isEmpty()) throw IllegalStateException("充值二维码下载失败")
        return bytes
    }

    private data class WebResponse(val bytes: ByteArray, val finalUrl: URL)

    private fun getWebResponse(
        url: URL,
        cookieManager: CookieManager,
        referer: URL? = null
    ): WebResponse {
        var currentUrl = url
        var currentReferer = referer
        repeat(8) {
            val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "text/html,application/xhtml+xml,image/avif,image/webp,image/*,*/*;q=0.8")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                currentReferer?.let { setRequestProperty("Referer", it.toExternalForm()) }
                cookieManager.get(cookieUri(currentUrl), emptyMap())["Cookie"]?.forEach {
                    addRequestProperty("Cookie", it)
                }
            }
            try {
                val responseCode = connection.responseCode
                cookieManager.put(
                    cookieUri(currentUrl),
                    connection.headerFields.filterKeys { it != null }
                )
                if (responseCode in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("缴费平台返回了无效的跳转地址")
                    currentReferer = currentUrl
                    currentUrl = URL(currentUrl, location)
                    return@repeat
                }
                if (responseCode !in 200..299) {
                    throw IllegalStateException("缴费平台请求失败（$responseCode）")
                }
                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    input.copyTo(output)
                    output.toByteArray()
                }
                return WebResponse(bytes, currentUrl)
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("缴费平台跳转次数过多")
    }

    private fun cookieUri(url: URL): URI = URI(
        url.protocol,
        null,
        url.host,
        url.port,
        url.path.ifBlank { "/" },
        null,
        null
    )

    private fun decodePaymentHtml(bytes: ByteArray): String {
        val utf8 = bytes.toString(StandardCharsets.UTF_8)
        return if ('\uFFFD' in utf8) bytes.toString(Charset.forName("GB18030")) else utf8
    }

    private fun findQrSource(html: String): String? {
        val normalized = html
            .replace("\\/", "/")
            .replace(Regex("\\\\u0026", RegexOption.IGNORE_CASE), "&")
            .replace("&amp;", "&")
        val patterns = listOf(
            Regex("""<img[^>]*\bsrc\s*=\s*[\"']([^\"']*QrcodeServlet[^\"']*)[\"']""", RegexOption.IGNORE_CASE),
            Regex("""[\"']([^\"']*QrcodeServlet[^\"']*)[\"']""", RegexOption.IGNORE_CASE),
            Regex("""((?:https?:)?//[^\s\"'<>]*QrcodeServlet[^\s\"'<>]*)""", RegexOption.IGNORE_CASE),
            Regex("""(/CCBIS/QrcodeServlet\?[^\s\"'<>]+)""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)
        }?.trim()
    }
}

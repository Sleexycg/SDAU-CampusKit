package com.sdau.campuskit

import android.util.JsonReader
import android.util.JsonToken
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Reader
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import kotlin.math.roundToInt

data class RemoteCourse(
    val day: Int,
    val startSlot: Int,
    val slotCount: Int,
    val name: String,
    val room: String,
    val teacher: String,
    val weeks: String = "",
    val courseCode: String = ""
)

class CourseScheduleNotPublishedException :
    IllegalStateException("课表暂未公布，不能查看课表！")

data class RemotePublicCourse(
    val college: String,
    val grade: String,
    val major: String,
    val className: String,
    val day: Int,
    val startSlot: Int,
    val slotCount: Int,
    val name: String,
    val room: String,
    val teacher: String,
    val weeks: String = "",
    val courseCode: String = ""
)

data class RemotePublicScheduleDownload(
    val sha256: String,
    val charsetName: String
)

data class RemoteScore(
    val courseCode: String,
    val courseName: String,
    val credit: String,
    val score: String,
    val gpa: String,
    val studentIdRaw: String = "",
    val teachingTaskId: String = "",
    val scoreRecordId: String = ""
)

data class RemoteScoreResult(
    val term: String,
    val records: List<RemoteScore>,
    val averageScore: String,
    val averageCreditGpa: String,
    val totalCredits: String
)

data class RemoteStudentProfile(
    val name: String,
    val studentId: String,
    val displayName: String = "$name-$studentId"
)

fun scoreToNumericValue(score: String): Double? {
    val normalized = score.trim().replace(Regex("\\s+"), "")
    return when (normalized) {
        "优秀" -> 95.0
        "良好" -> 85.0
        "中等" -> 75.0
        "及格" -> 65.0
        "不及格", "不合格" -> 0.0
        else -> normalized.toDoubleOrNull()
    }
}

fun scoreToGradePoint(score: String): Double? {
    val numericScore = scoreToNumericValue(score) ?: return null
    return if (numericScore < 60.0) 0.0 else numericScore / 10.0 - 5.0
}

private fun formatGradePoint(value: Double): String = String.format(Locale.US, "%.1f", value)

fun applyCalculatedGradePoints(records: List<RemoteScore>): List<RemoteScore> = records.map { record ->
    record.copy(gpa = scoreToGradePoint(record.score)?.let(::formatGradePoint) ?: "-")
}

fun calculateAverageCreditGpa(records: List<RemoteScore>): String {
    val weightedCourses = records.mapNotNull { record ->
        val credit = record.credit.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
        val gradePoint = scoreToGradePoint(record.score) ?: return@mapNotNull null
        credit to gradePoint
    }
    val validCredits = weightedCourses.sumOf { it.first }
    return if (validCredits > 0.0) {
        String.format(Locale.US, "%.2f", weightedCourses.sumOf { it.first * it.second } / validCredits)
    } else {
        "-"
    }
}

fun calculateAverageScore(records: List<RemoteScore>): String {
    val numericScores = records.mapNotNull { scoreToNumericValue(it.score) }
    return if (numericScores.isNotEmpty()) {
        String.format(Locale.US, "%.2f", numericScores.average())
    } else {
        "-"
    }
}

fun recalculateScoreResult(
    result: RemoteScoreResult,
    allTermRecords: List<RemoteScore> = result.records
): RemoteScoreResult {
    return result.copy(
        records = applyCalculatedGradePoints(result.records),
        averageScore = calculateAverageScore(allTermRecords),
        averageCreditGpa = calculateAverageCreditGpa(allTermRecords)
    )
}

data class RemoteScoreDetail(
    val usualScore: String,
    val usualRatio: String,
    val finalScore: String,
    val finalRatio: String,
    val totalScore: String
)

data class RemoteExam(
    val courseName: String,
    val examWeek: String,
    val examWeekday: String,
    val examSessions: String,
    val classroom: String
)

data class RemoteGradeExam(
    val id: String,
    val sequence: String,
    val examName: String,
    val examCategory: String,
    val score: String,
    val examTime: String
)

data class RemoteEmptyRoomResult(
    val term: String,
    val week: Int,
    val campus: String,
    val weekday: Int,
    val sectionCode: String,
    val rooms: List<String>
)

data class RemoteTrainingPlanSubject(
    val term: String,
    val courseCode: String,
    val courseName: String,
    val credit: String,
    val courseType: String,
    val categoryCode: String,
    val status: String,
    val score: String
)

data class RemoteTrainingPlanItem(
    val category: String,
    val requiredCredits: String,
    val completedCredits: String,
    val currentCredits: String,
    val remainingCredits: String,
    val subjects: List<RemoteTrainingPlanSubject> = emptyList()
)

data class RemoteTrainingPlanSummary(
    val requiredCredits: String,
    val completedCredits: String,
    val currentCredits: String,
    val remainingCredits: String
)

data class RemoteTrainingPlanResult(
    val items: List<RemoteTrainingPlanItem>,
    val summary: RemoteTrainingPlanSummary
)

private data class RemoteMeeting(val day: Int, val startSlot: Int, val slotCount: Int, val weeks: String)
private data class PersonalMeeting(val name: String, val code: String, val day: Int, val startSlot: Int, val slotCount: Int, val weeks: String, val room: String)
private data class EmptyRoomBootstrap(
    val term: String,
    val token: String,
    val startWeekday: String,
    val campusCodes: Map<String, String>
)
private data class EmptyRoomSectionPlan(val selectCode: String, val targetToken: String)

class SdauCourseRepository {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    fun queryStudentProfile(account: String, password: String): RemoteStudentProfile {
        login(account, password)
        val candidates = listOf(
            "/framework/xsMainV_new.htmlx?t1=1",
            "/framework/xsMainV.jsp"
        )
        candidates.forEach { path ->
            val body = requestStage("读取个人信息") { request(path, "GET", null) }
            if (isLoginPage(body)) throw IllegalStateException("登录状态已失效，请重新登录")
            parseStudentProfile(body, account)?.let { return it }
        }
        throw IllegalStateException("未能从教务系统主页面解析到个人信息")
    }

    fun queryCourses(account: String, password: String, term: String): List<RemoteCourse> {
        login(account, password)
        val path = "/xkgl/loadXsxkjgList?lx=xkrz&type=list&pageNum=1&pageSize=200&xnxqid=" +
            URLEncoder.encode(term, "UTF-8")
        val selected = try {
            parseCourses(requestStage("读取已选课程") { request(path, "GET", null) })
        } catch (error: Exception) {
            throw IllegalStateException("已选课结果解析失败：${error.message ?: "返回数据格式异常"}", error)
        }
        val bootstrapPath = "/xskb/xskb_list.do?viweType=0&xnxq01id=" +
            URLEncoder.encode(term, "UTF-8")
        val bootstrapHtml = requestStage("读取个人课表") {
            request(bootstrapPath, "GET", null)
        }
        ensurePersonalTimetableAvailable(bootstrapHtml)
        val timetableToken = htmlControlValue(bootstrapHtml, "kbjcmsid")
        val timetableHtml = if (timetableToken.isBlank()) {
            bootstrapHtml
        } else {
            val timetablePath = buildString {
                append("/xskb/xskb_list.do?viweType=0")
                append("&showallprint=0&showkchprint=0&showkink=0&showfzmprint=0")
                append("&baseUrl=")
                append("&xsflMapListJsonStr=")
                append(URLEncoder.encode("讲课,实验,实践,上机,讨论,", "UTF-8"))
                append("&xnxq01id=")
                append(URLEncoder.encode(term, "UTF-8"))
                append("&zc=")
                append("&kbjcmsid=")
                append(URLEncoder.encode(timetableToken, "UTF-8"))
            }
            requestStage("读取个人课表详情") {
                request(timetablePath, "GET", null)
            }
        }
        ensurePersonalTimetableAvailable(timetableHtml)
        val personal = parsePersonalTimetable(timetableHtml)
        if (selected.isNotEmpty() && personal.isEmpty()) {
            throw CourseScheduleNotPublishedException()
        }
        return selected.map { course ->
            val weeks = findPersonalWeeks(course, personal)
            if (weeks.isBlank()) course else course.copy(weeks = weeks)
        }
    }

    fun downloadPublicScheduleMirror(
        term: String,
        destination: File,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): RemotePublicScheduleDownload {
        val parts = term.split("-")
        val startYear = parts.getOrNull(0)?.toIntOrNull()
            ?: throw IllegalArgumentException("学期格式不正确")
        val endYear = parts.getOrNull(1)?.toIntOrNull()
            ?: throw IllegalArgumentException("学期格式不正确")
        val semester = parts.getOrNull(2)?.takeIf { it == "1" || it == "2" }
            ?: throw IllegalArgumentException("暂不支持该学期数据")
        val fileName = "sc${String.format(Locale.US, "%02d", startYear % 100)}-" +
            "${String.format(Locale.US, "%02d", endYear % 100)}-$semester.json"
        val mirrorUrl = "$PUBLIC_SCHEDULE_MIRROR_BASE/$fileName"
        onProgress(8, "正在连接课程数据镜像")
        val connection = (URL(mirrorUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 60000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("User-Agent", "SDAU-ClassSchedule-Android/2.0")
            setRequestProperty("Accept-Encoding", "identity")
        }
        val contentType: String?
        val status: Int
        try {
            status = connection.responseCode
            contentType = connection.contentType
            if (status !in 200..299) {
                connection.errorStream?.close()
                throw IllegalStateException("课程镜像返回 HTTP $status")
            }
            val contentLength = connection.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            DigestInputStream(connection.inputStream, digest).use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (contentLength > 0L) {
                            val progress = (8L + copied * 62L / contentLength).toInt().coerceIn(8, 70)
                            onProgress(progress, "正在下载课程镜像")
                        }
                    }
                }
            }
            val sha256 = digest.digest()
                .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
            return RemotePublicScheduleDownload(
                sha256 = sha256,
                charsetName = responseCharset(contentType).name()
            )
        } finally {
            connection.disconnect()
        }
    }

    fun streamPublicCourses(input: Reader, onCourse: (RemotePublicCourse) -> Unit): Int {
        val buffered = input.buffered()
        buffered.mark(1)
        if (buffered.read() != '\uFEFF'.code) buffered.reset()
        return JsonReader(buffered).use { reader ->
            reader.isLenient = true
            streamPublicValue(reader, 0, onCourse)
        }
    }

    private fun scoreListPath(selectedTerm: String): String {
        val parameters = linkedMapOf(
            "pageNum" to "1",
            "pageSize" to "200",
            "kksj" to selectedTerm,
            "kcxz" to "",
            "kcsx" to "",
            "kcmc" to "",
            "xsfs" to "all",
            "sfxsbcxq" to "1"
        )
        return "/kscj/cjcx_list?" + parameters.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
    }

    /** Lightweight list-only query used by the background new-score monitor. */
    fun queryScoreRecords(account: String, password: String, term: String): List<RemoteScore> {
        login(account, password)
        val formPath = "/kscj/cjcx_frm"
        requestStage("打开成绩查询页") { request(formPath, "GET", null) }
        val referer = "$BASE_URL$formPath"
        val listBody = requestStage("读取课程成绩") {
            request(scoreListPath(term), "GET", null, referer)
        }
        return parseScoreRecords(listBody, term)
    }

    fun queryScores(
        account: String,
        password: String,
        term: String,
        allTerms: List<String> = listOf(term)
    ): RemoteScoreResult {
        login(account, password)
        val formPath = "/kscj/cjcx_frm"
        requestStage("打开成绩查询页") { request(formPath, "GET", null) }
        val referer = "$BASE_URL$formPath"
        val listBody = requestStage("读取课程成绩") {
            request(scoreListPath(term), "GET", null, referer)
        }
        val summaryBody = runCatching {
            request(scoreListPath(""), "GET", null, referer)
        }.getOrDefault("{}")
        val selectedRecords = parseScoreRecords(listBody, term)
        val allTermRecords = allTerms.distinct().flatMap { scoreTerm ->
            if (scoreTerm == term) {
                selectedRecords
            } else {
                parseScoreRecords(
                    requestStage("读取 $scoreTerm 学期成绩") {
                        request(scoreListPath(scoreTerm), "GET", null, referer)
                    },
                    scoreTerm
                )
            }
        }
        return parseScoreResult(selectedRecords, summaryBody, term, allTermRecords)
    }

    fun queryExams(account: String, password: String, term: String): List<RemoteExam> {
        login(account, password)

        // 正常考试安排接口先接入请求链路。当前尚无可验证样本，保留原始响应但暂不参与展示解析。
        val scheduledPath = "/xsks/xsksap_list?pageNum=1&pageSize=20&xnxqid=" +
            URLEncoder.encode(term, "UTF-8") + "&xqlb="
        runCatching {
            requestStage("读取考试安排") { request(scheduledPath, "GET", null) }
        }

        val earlyPath = "/xsks/xsstk_list?pageNum=1&pageSize=20&xnxqid=" +
            URLEncoder.encode(term, "UTF-8") + "&kslb="
        val earlyBody = requestStage("读取提前考试安排") {
            request(earlyPath, "GET", null)
        }
        return parseEarlyExams(earlyBody)
    }

    fun queryTrainingPlan(account: String, password: String): RemoteTrainingPlanResult {
        login(account, password)
        val path = "/xxwcqk/xxwcqkOnkctx.do?isdb=0"
        val body = requestStage("读取培养方案") { request(path, "GET", null) }
        if (isLoginPage(body)) {
            throw IllegalStateException("登录状态已失效，请重新登录")
        }
        return parseTrainingPlan(body)
    }

    fun queryGradeExams(account: String, password: String): List<RemoteGradeExam> {
        login(account, password)
        val path = "/kscj/djkscj_list?type=listData&pageNum=1&pageSize=100"
        val body = requestStage("读取等级考试成绩") { request(path, "GET", null) }
        if (isLoginPage(body)) {
            throw IllegalStateException("登录状态已失效，请重新登录")
        }
        val rows = normalizeJsonRows(body)
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val sequence = jsonText(row, "xh", "rownum_", "xh_", "index", "id")
                val category = jsonText(row, "skkcmc", "djkcmc", "kcmc", "kc_mc")
                val name = jsonText(row, "skkcdjmc", "skdjmc", "djmc", "ksdj", "level")
                    .ifBlank { category }
                val score = jsonText(row, "fslcj", "cj", "kscj", "score", "zcj")
                val examTime = jsonText(row, "kssj", "ksrq", "time", "sj")
                if (name.isBlank() && score.isBlank() && examTime.isBlank()) continue
                val id = jsonText(row, "id", "cjbh", "kscj_id")
                    .ifBlank { listOf(sequence, name, examTime).joinToString("|") }
                add(
                    RemoteGradeExam(
                        id = id,
                        sequence = sequence,
                        examName = name.ifBlank { "等级考试" },
                        examCategory = category.takeUnless { it == name }.orEmpty(),
                        score = score.ifBlank { "-" },
                        examTime = examTime.ifBlank { "时间未记录" }
                    )
                )
            }
        }.distinctBy { it.id }
    }

    fun queryEmptyRooms(
        account: String,
        password: String,
        campus: String,
        week: Int,
        weekday: Int,
        sectionCode: String
    ): RemoteEmptyRoomResult {
        require(week in 1..30) { "查询周次无效" }
        require(weekday in 1..7) { "查询星期无效" }
        login(account, password)

        val pagePath = "/kbxx/jsjy_query"
        val page = requestStage("打开空教室查询页") { request(pagePath, "GET", null) }
        if (
            page.contains("欢迎登录教务系统") ||
            page.contains("请先登录系统") ||
            (page.contains("LoginToXk") && page.contains("userAccount"))
        ) {
            throw IllegalStateException("登录状态已失效，请重新登录")
        }
        val bootstrap = parseEmptyRoomBootstrap(page)
        val campusCode = bootstrap.campusCodes[campus] ?: when (campus) {
            "岱宗校区" -> "001"
            "泮河校区" -> "002"
            "西北片区" -> "A5F850229661E843E0536685C2CAF624"
            else -> throw IllegalArgumentException("未知校区")
        }

        var lastError: Exception? = null
        for (plan in emptyRoomSectionPlans(sectionCode)) {
            val form = linkedMapOf(
                "xnxqh" to bootstrap.term,
                "xqbh" to campusCode,
                "jxqbh" to "",
                "jxlbh" to "",
                "jsbh" to "",
                "jslx" to "",
                "bjfh" to "=",
                "rnrs" to "",
                "yx" to "",
                "kbjcmsid" to bootstrap.token,
                "selectZc" to week.toString(),
                "startdate" to "",
                "enddate" to "",
                "selectXq" to weekday.toString(),
                "selectJc" to plan.selectCode,
                "syjs0601id" to "",
                "typewhere" to "jszq",
                "qsxq" to bootstrap.startWeekday,
                "jyms" to "0"
            )
            try {
                val body = request(
                    "/kbxx/jsjy_query2",
                    "POST",
                    form,
                    "$BASE_URL$pagePath",
                    mapOf(
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "X-Requested-With" to "XMLHttpRequest",
                        "Origin" to BASE_URL
                    )
                )
                val rooms = parseEmptyRoomRows(body, resolveEmptyRoomCellIndex(plan))
                return RemoteEmptyRoomResult(
                    term = bootstrap.term,
                    week = week,
                    campus = campus,
                    weekday = weekday,
                    sectionCode = sectionCode,
                    rooms = rooms
                )
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IllegalStateException(lastError?.message ?: "空教室接口暂时无法查询")
    }

    fun queryScoreDetail(account: String, password: String, score: RemoteScore): RemoteScoreDetail {
        if (score.studentIdRaw.isBlank() || score.teachingTaskId.isBlank() || score.scoreRecordId.isBlank()) {
            throw IllegalStateException("该课程缺少成绩明细参数")
        }
        login(account, password)
        val parameters = linkedMapOf(
            "xs0101id" to score.studentIdRaw,
            "jx0404id" to score.teachingTaskId,
            "cj0708id" to score.scoreRecordId,
            "zcj" to score.score
        )
        val path = "/kscj/pscj_list.do?" + parameters.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val body = requestStage("读取成绩构成") { request(path, "GET", null) }
        val arrayText = Regex("(?:let\\s+)?arr\\s*=\\s*(\\[[\\s\\S]*?]);?", RegexOption.IGNORE_CASE)
            .find(body.trimStart('\uFEFF'))?.groupValues?.getOrNull(1)
            ?: throw IllegalStateException("成绩构成接口返回格式异常")
        val first = runCatching { JSONArray(arrayText).optJSONObject(0) }.getOrNull()
            ?: throw IllegalStateException("暂无成绩构成数据")
        // 教务页面的字段语义与编号相反：cjxm3 是平时，cjxm1 是期末。
        return RemoteScoreDetail(
            usualScore = jsonText(first, "cjxm3").ifBlank { "-" },
            usualRatio = jsonText(first, "cjxm3bl").ifBlank { "-" },
            finalScore = jsonText(first, "cjxm1").ifBlank { "-" },
            finalRatio = jsonText(first, "cjxm1bl").ifBlank { "-" },
            totalScore = jsonText(first, "zcj").ifBlank { score.score.ifBlank { "-" } }
        )
    }

    private fun login(account: String, password: String) {
        cookies.cookieStore.removeAll()
        val loginPage = requestStage("读取登录页") { request("/", "GET", null) }
        val scode = capture(loginPage, "var\\s+scode\\s*=\\s*['\"](.*?)['\"]")
        val sxh = capture(loginPage, "var\\s+sxh\\s*=\\s*['\"](.*?)['\"]")
        if (scode.isEmpty() || sxh.isEmpty()) throw IllegalStateException("无法提取登录动态参数")

        val form = linkedMapOf(
            "loginMethod" to "LoginToXk",
            "userlanguage" to "0",
            "userAccount" to account,
            "userPassword" to "",
            "encoded" to buildCredential(account, password, scode, sxh)
        )
        val loginResponse = requestStage("提交登录") { request("/xk/LoginToXk", "POST", form) }
        val loginMessage = capture(
            loginResponse,
            "id\\s*=\\s*['\"]showMsg['\"][^>]*>(.*?)</[^>]+>"
        ).let(::clean)
        if (loginMessage.isNotBlank() && !loginMessage.contains("请先登录系统")) {
            throw IllegalArgumentException(loginMessage)
        }
        if (isLoginPage(loginResponse)) {
            throw IllegalArgumentException("学号或密码错误，或教务系统未建立登录会话")
        }
    }

    private fun parseScoreRecords(listBody: String, term: String): List<RemoteScore> {
        val rows = normalizeJsonRows(listBody)
        val records = mutableListOf<RemoteScore>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val courseCode = jsonText(row, "kch", "courseCode", "kcdm")
            val courseName = jsonText(row, "kc_mc", "kcmc", "courseName")
            if (courseCode.isBlank() && courseName.isBlank()) continue
            records += RemoteScore(
                courseCode = courseCode,
                courseName = courseName,
                credit = jsonText(row, "xf", "credit"),
                score = jsonText(row, "zcj", "zcjstr", "score").ifBlank { "-" },
                // 单科绩点统一由总成绩换算，不采用教务系统更新可能滞后的 jd 字段。
                gpa = "-",
                studentIdRaw = jsonText(row, "xs0101id"),
                teachingTaskId = jsonText(row, "jx0404id"),
                scoreRecordId = jsonText(row, "cj0708id")
            )
        }
        return records.distinctBy {
            it.scoreRecordId.ifBlank { "$term|${it.courseCode}|${it.courseName}" }
        }
    }

    private fun parseScoreResult(
        selectedRecords: List<RemoteScore>,
        summaryBody: String,
        term: String,
        allTermRecords: List<RemoteScore>
    ): RemoteScoreResult {
        val unique = selectedRecords
        val summary = runCatching { JSONObject(summaryBody.trimStart('\uFEFF').trim()) }.getOrNull()
        val credits = unique.mapNotNull { it.credit.toDoubleOrNull() }
        return recalculateScoreResult(RemoteScoreResult(
            term = term,
            records = unique,
            averageScore = "-",
            averageCreditGpa = "-",
            totalCredits = summary?.let { jsonText(it, "sxzxf") }.orEmpty()
                .ifBlank { credits.takeIf { it.isNotEmpty() }?.sum()?.let { formatNumber(it) }.orEmpty() }
                .ifBlank { "-" }
        ), allTermRecords)
    }

    private fun parseEarlyExams(body: String): List<RemoteExam> {
        val root = runCatching { JSONObject(body.trimStart('\uFEFF').trim()) }.getOrElse {
            throw IllegalStateException("提前考试接口返回格式异常")
        }
        if (root.optInt("code", 0) != 0) {
            throw IllegalStateException(root.optString("msg").ifBlank { "提前考试接口查询失败" })
        }
        val data = root.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                val row = data.optJSONObject(index) ?: continue
                val courseName = jsonText(row, "kc_mc")
                if (courseName.isBlank()) continue
                add(RemoteExam(
                    courseName = courseName,
                    examWeek = normalizeExamNumber(jsonText(row, "kszc")),
                    examWeekday = normalizeExamNumber(jsonText(row, "ksxq")),
                    examSessions = normalizeExamSessions(jsonText(row, "ksjc")),
                    classroom = normalizeClassroomName(jsonText(row, "js_mc")).ifBlank { "-" }
                ))
            }
        }.distinct()
    }

    private fun normalizeExamNumber(value: String): String = value.trim().toIntOrNull()?.toString()
        ?: value.trim().trimStart('0').ifBlank { value.trim() }

    private fun normalizeExamSessions(value: String): String {
        val numbers = Regex("\\d+").findAll(value).mapNotNull { it.value.toIntOrNull() }.toList()
        return when {
            numbers.size >= 2 -> "${numbers.first()}-${numbers.last()}"
            numbers.size == 1 -> numbers.first().toString()
            else -> value.trim()
        }
    }

    private fun parseEmptyRoomBootstrap(html: String): EmptyRoomBootstrap {
        val term = htmlControlValue(html, "xnxqh")
        val token = htmlControlValue(html, "kbjcmsid")
        val startWeekday = htmlControlValue(html, "qsxq").ifBlank { "1" }
        if (term.isBlank() || token.isBlank()) {
            val missing = buildList {
                if (term.isBlank()) add("xnxqh")
                if (token.isBlank()) add("kbjcmsid")
            }
            throw IllegalStateException("空教室页面关键参数缺失（${missing.joinToString("、")}）")
        }

        val campusCodes = linkedMapOf<String, String>()
        val selectBody = Regex(
            "<select[^>]*\\bid\\s*=\\s*['\"]xqbh['\"][^>]*>([\\s\\S]*?)</select>",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1).orEmpty()
        val optionRegex = Regex("<option([^>]*)>([\\s\\S]*?)</option>", RegexOption.IGNORE_CASE)
        optionRegex.findAll(selectBody).forEach { match ->
            val code = htmlAttribute(match.groupValues[1], "value")
            val name = clean(match.groupValues[2]).replace(Regex("\\s+"), "")
            if (code.isBlank() || name.isBlank()) return@forEach
            // 实际第三项为“泮河校区西北片区”，必须先识别西北，不能覆盖泮河的 002。
            when {
                name.contains("西北") -> campusCodes["西北片区"] = code
                name.contains("岱宗") || name.contains("岱总") -> campusCodes["岱宗校区"] = code
                name.contains("泮河") || name.contains("中央") -> campusCodes["泮河校区"] = code
            }
        }
        return EmptyRoomBootstrap(term, token, startWeekday, campusCodes)
    }

    private fun parseEmptyRoomRows(body: String, targetCellIndex: Int): List<String> {
        val raw = body.trimStart('\uFEFF').trim()
        val payload = runCatching { JSONArray(raw) }.getOrElse {
            val message = runCatching { JSONObject(raw).optString("msg") }.getOrDefault("")
            throw IllegalStateException(message.ifBlank { "空教室接口返回格式异常" })
        }
        val rows = payload.optJSONArray(4)
            ?: throw IllegalStateException("空教室接口数据结构发生变化")
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONArray(index) ?: continue
                val cellIndex = if (targetCellIndex < row.length()) targetCellIndex else 1
                val occupied = jsonCellText(row.opt(cellIndex))
                if (occupied.isNotEmpty()) continue
                val roomName = normalizeClassroomName(jsonCellText(row.opt(0)))
                if (roomName.isNotEmpty()) add(roomName)
            }
        }.distinct()
    }

    private fun emptyRoomSectionPlans(sectionCode: String): List<EmptyRoomSectionPlan> = when (sectionCode) {
        "0102" -> listOf(EmptyRoomSectionPlan("0102", "0102"))
        "0304" -> listOf(EmptyRoomSectionPlan("0304", "0304"))
        "中午" -> listOf(
            EmptyRoomSectionPlan("中午", "中午"),
            EmptyRoomSectionPlan("0102,中午", "中午"),
            EmptyRoomSectionPlan("0304,中午", "中午")
        )
        "0506" -> listOf(
            EmptyRoomSectionPlan("0506", "0506"),
            EmptyRoomSectionPlan("第三大节", "第三大节"),
            EmptyRoomSectionPlan("0102,0506", "0506"),
            EmptyRoomSectionPlan("0304,0506", "0506"),
            EmptyRoomSectionPlan("0102,0304,0506", "0506")
        )
        "0708" -> listOf(
            EmptyRoomSectionPlan("0708", "0708"),
            EmptyRoomSectionPlan("第四大节", "第四大节"),
            EmptyRoomSectionPlan("0102,0708", "0708"),
            EmptyRoomSectionPlan("0304,0708", "0708"),
            EmptyRoomSectionPlan("0102,0304,0708", "0708")
        )
        "0910" -> listOf(
            EmptyRoomSectionPlan("0910", "0910"),
            EmptyRoomSectionPlan("第五大节", "第五大节"),
            EmptyRoomSectionPlan("0102,0910", "0910"),
            EmptyRoomSectionPlan("0304,0910", "0910")
        )
        "晚间" -> listOf(
            EmptyRoomSectionPlan("晚间", "晚间"),
            EmptyRoomSectionPlan("晚间时段", "晚间时段"),
            EmptyRoomSectionPlan("0102,晚间", "晚间"),
            EmptyRoomSectionPlan("0304,晚间", "晚间")
        )
        else -> throw IllegalArgumentException("未知节次")
    }

    private fun resolveEmptyRoomCellIndex(plan: EmptyRoomSectionPlan): Int {
        fun canonical(value: String) = when (value.trim()) {
            "第一大节" -> "0102"
            "第二大节" -> "0304"
            "第三大节" -> "0506"
            "第四大节" -> "0708"
            "第五大节" -> "0910"
            "中午时段" -> "中午"
            "晚间时段" -> "晚间"
            else -> value.trim()
        }
        val target = canonical(plan.targetToken)
        val index = plan.selectCode.split(',').map(::canonical).indexOf(target)
        return if (index >= 0) index + 1 else 1
    }

    /**
     * Mirrors the browser/jQuery `val()` behavior used by WeSDAU. The legacy
     * teaching-system page may expose a value through an input attribute, a
     * selected option, or a small inline script depending on its deployed UI.
     */
    private fun htmlControlValue(html: String, key: String): String {
        val escapedKey = Regex.escape(key)
        val control = Regex(
            "<([a-z][a-z0-9]*)\\b[^>]*(?:\\bid|\\bname)\\s*=\\s*(?:['\"]$escapedKey['\"]|$escapedKey(?=\\s|/?>))[^>]*>",
            RegexOption.IGNORE_CASE
        ).find(html)
        if (control != null) {
            htmlAttribute(control.value, "value").takeIf { it.isNotBlank() }?.let { return it }
            if (control.groupValues[1].equals("select", ignoreCase = true)) {
                val bodyStart = control.range.last + 1
                val bodyEnd = Regex("</select\\s*>", RegexOption.IGNORE_CASE)
                    .find(html, bodyStart)?.range?.first ?: html.length
                val selectBody = html.substring(bodyStart, bodyEnd)
                val options = Regex(
                    "<option\\b([^>]*)>([\\s\\S]*?)</option\\s*>",
                    RegexOption.IGNORE_CASE
                ).findAll(selectBody).toList()
                val selectedOption = options.firstOrNull {
                    Regex("\\bselected\\b", RegexOption.IGNORE_CASE)
                        .containsMatchIn(it.groupValues[1])
                } ?: options.firstOrNull()
                selectedOption?.let { option ->
                    htmlAttribute(option.groupValues[1], "value")
                        .ifBlank { clean(option.groupValues[2]) }
                        .takeIf { it.isNotBlank() }
                        ?.let { return it }
                }
            }
        }

        val scriptValue = Regex(
            "(?:var\\s+|let\\s+|const\\s+)?$escapedKey\\s*=\\s*['\"]([^'\"]+)['\"]",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (scriptValue.isNotBlank()) return scriptValue

        return Regex(
            "[?&]$escapedKey=([^&'\"\\s<]+)",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    private fun htmlAttribute(tag: String, attribute: String): String {
        val match = Regex(
            "\\b${Regex.escape(attribute)}\\s*=\\s*(?:(['\"])(.*?)\\1|([^\\s>]+))",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(tag) ?: return ""
        return match.groupValues.getOrNull(2).orEmpty()
            .ifBlank { match.groupValues.getOrNull(3).orEmpty() }
            .trim()
            .trimEnd('/')
    }

    private fun jsonCellText(value: Any?): String = when {
        value == null || value === JSONObject.NULL -> ""
        else -> value.toString().trim()
    }

    private fun normalizeJsonRows(body: String): JSONArray {
        val raw = body.trimStart('\uFEFF').trim()
        runCatching { JSONArray(raw) }.getOrNull()?.let { return it }
        val root = runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalStateException("课程成绩接口返回格式异常")
        }
        return root.optJSONArray("rows") ?: root.optJSONArray("data") ?: root.optJSONArray("list")
            ?: root.optJSONArray("result") ?: JSONArray()
    }

    private fun jsonText(row: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            if (row.has(key) && !row.isNull(key)) return row.opt(key)?.toString()?.trim().orEmpty()
        }
        return ""
    }

    private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    /**
     * The selected-course endpoint owns the placement.  The HTML timetable is
     * used solely to fill its precise week range: merged cells on that page can
     * report a shifted weekday, so requiring every field to match loses valid
     * data.
     */
    private fun findPersonalWeeks(course: RemoteCourse, personal: List<PersonalMeeting>): String {
        val related = personal.filter { item ->
            val sameCode = course.courseCode.isNotBlank() && item.code.isNotBlank() && item.code == course.courseCode
            sameCode || item.name == course.name
        }
        fun oneRange(items: List<PersonalMeeting>): String {
            val values = items.map { it.weeks.trim() }.filter { it.isNotBlank() }
            return if (values.isEmpty()) "" else normalizeWeekText(values.joinToString(","))
        }

        return oneRange(related.filter {
            it.day == course.day && it.startSlot == course.startSlot && it.slotCount == course.slotCount
        }).ifBlank {
            oneRange(related.filter {
                it.startSlot == course.startSlot && it.slotCount == course.slotCount
            })
        }.ifBlank {
            oneRange(related)
        }
    }

    private inline fun requestStage(stage: String, action: () -> String): String {
        return try {
            action()
        } catch (error: Exception) {
            throw IllegalStateException("${stage}失败：${error.message ?: "网络或教务系统响应异常"}", error)
        }
    }

    private fun isLoginPage(body: String): Boolean {
        return body.contains("请先登录系统") ||
            body.contains("欢迎登录教务系统") ||
            (body.contains("LoginToXk") && body.contains("userAccount"))
    }

    private fun ensurePersonalTimetableAvailable(html: String) {
        val timetableText = Jsoup.parse(html).text().replace(Regex("\\s+"), "")
        if (
            timetableText.contains("课表暂未公布") ||
            timetableText.contains("不能查看课表")
        ) {
            throw CourseScheduleNotPublishedException()
        }
    }

    private fun parseTrainingPlan(body: String): RemoteTrainingPlanResult {
        val parsedItems = mutableListOf<MutableTrainingPlanItem>()
        val categoryLookup = linkedMapOf<String, MutableTrainingPlanItem>()
        val unresolvedBkSubjects = mutableListOf<RemoteTrainingPlanSubject>()
        var summary: RemoteTrainingPlanSummary? = null
        var currentCategory: String? = null

        val document = Jsoup.parse(body)
        document.select(".list-tr").forEach { row ->
            val cells = row.select(".list-td .list-td-cell")
                .map { cleanTrainingPlanText(it.text()) }
            if (cells.size < 5) return@forEach

            val firstCell = cells.first().trim()
            if (row.hasClass("total-tr") || firstCell == "合计") {
                summary = RemoteTrainingPlanSummary(
                    requiredCredits = cells.getOrElse(1) { "0" },
                    completedCredits = cells.getOrElse(2) { "0" },
                    currentCredits = cells.getOrElse(3) { "0" },
                    remainingCredits = cells.getOrElse(4) { "0" }
                )
                return@forEach
            }

            val category = canonicalTrainingPlanCategory(
                cleanTrainingPlanText(row.selectFirst(".jClass-item")?.text().orEmpty())
            )
            if (category.isNotBlank() && !isTrainingPlanTerm(category)) {
                val item = MutableTrainingPlanItem(
                    category = category,
                    requiredCredits = cells.getOrElse(1) { "0" },
                    completedCredits = cells.getOrElse(2) { "0" },
                    currentCredits = cells.getOrElse(3) { "0" },
                    remainingCredits = cells.getOrElse(4) { "0" }
                )
                parsedItems += item
                categoryLookup[category] = item
                currentCategory = category
                return@forEach
            }

            if (isTrainingPlanTerm(firstCell) && cells.size >= 8) {
                val categoryCode = cells[5].trim().uppercase(Locale.ROOT)
                val subject = RemoteTrainingPlanSubject(
                    term = firstCell,
                    courseCode = cells[1],
                    courseName = cells[2],
                    credit = cells[3].filter { it.isDigit() || it == '.' }.ifEmpty { "-" },
                    courseType = cells[4],
                    categoryCode = categoryCode,
                    status = cells[6],
                    score = cells[7]
                )
                val inferredCategory = trainingPlanCategoryForCourse(
                    courseType = cells[4],
                    code = categoryCode,
                    courseName = cells[2]
                )
                val orderedBkCategory = currentCategory
                    ?.takeIf { it in BK_TRAINING_PLAN_CATEGORIES && categoryLookup.containsKey(it) }
                when {
                    categoryCode == "BK" && orderedBkCategory != null -> {
                        categoryLookup[orderedBkCategory]?.subjects?.let { subjects ->
                            if (subject !in subjects) subjects += subject
                        }
                    }
                    inferredCategory != null -> {
                        categoryLookup[inferredCategory]?.subjects?.let { subjects ->
                            if (subject !in subjects) subjects += subject
                        }
                    }
                    categoryCode == "BK" -> {
                        if (subject !in unresolvedBkSubjects) unresolvedBkSubjects += subject
                    }
                    currentCategory != null -> {
                        categoryLookup[currentCategory]?.subjects?.let { subjects ->
                            if (subject !in subjects) subjects += subject
                        }
                    }
                }
            }
        }

        if (parsedItems.isEmpty()) {
            throw IllegalStateException("未能解析培养方案，教务系统页面格式可能已变化")
        }
        assignUnresolvedBkTrainingPlanSubjects(categoryLookup, unresolvedBkSubjects)
        val immutableItems = parsedItems.map { item ->
            RemoteTrainingPlanItem(
                category = item.category,
                requiredCredits = item.requiredCredits,
                completedCredits = item.completedCredits,
                currentCredits = item.currentCredits,
                remainingCredits = item.remainingCredits,
                subjects = item.subjects.toList()
            )
        }
        val resolvedSummary = summary ?: RemoteTrainingPlanSummary(
            requiredCredits = formatTrainingPlanCredit(immutableItems.sumOf { it.requiredCredits.toDoubleOrNull() ?: 0.0 }),
            completedCredits = formatTrainingPlanCredit(immutableItems.sumOf { it.completedCredits.toDoubleOrNull() ?: 0.0 }),
            currentCredits = formatTrainingPlanCredit(immutableItems.sumOf { it.currentCredits.toDoubleOrNull() ?: 0.0 }),
            remainingCredits = formatTrainingPlanCredit(immutableItems.sumOf { it.remainingCredits.toDoubleOrNull() ?: 0.0 })
        )
        return RemoteTrainingPlanResult(immutableItems, resolvedSummary)
    }

    /**
     * 教务系统把学科基础、通识必修和专业核心的课程明细统一标记为 BK，
     * 正常情况下优先使用 HTML 中的类别边界直接归类。只有页面缺少类别边界时，
     * 才按照网页类别顺序、课程顺序和各状态学分约束进行兜底分配。
     */
    private fun assignUnresolvedBkTrainingPlanSubjects(
        categoryLookup: Map<String, MutableTrainingPlanItem>,
        unresolvedSubjects: List<RemoteTrainingPlanSubject>
    ) {
        if (unresolvedSubjects.isEmpty()) return
        val bkCategories = categoryLookup.values
            .filter { it.category in BK_TRAINING_PLAN_CATEGORIES }
        if (bkCategories.isEmpty()) return

        assignTrainingPlanSubjectsByCredit(
            categories = bkCategories,
            subjects = unresolvedSubjects.filter {
                trainingPlanSubjectBucket(it) == TrainingPlanSubjectBucket.COMPLETED
            },
            bucket = TrainingPlanSubjectBucket.COMPLETED,
            targetCredits = { it.completedCredits }
        )
        assignTrainingPlanSubjectsByCredit(
            categories = bkCategories,
            subjects = unresolvedSubjects.filter {
                trainingPlanSubjectBucket(it) == TrainingPlanSubjectBucket.CURRENT
            },
            bucket = TrainingPlanSubjectBucket.CURRENT,
            targetCredits = { it.currentCredits }
        )
        assignTrainingPlanSubjectsByCredit(
            categories = bkCategories,
            subjects = unresolvedSubjects.filter {
                trainingPlanSubjectBucket(it) == TrainingPlanSubjectBucket.UNCOMPLETED
            },
            bucket = TrainingPlanSubjectBucket.UNCOMPLETED,
            targetCredits = { it.remainingCredits }
        )
    }

    private fun assignTrainingPlanSubjectsByCredit(
        categories: List<MutableTrainingPlanItem>,
        subjects: List<RemoteTrainingPlanSubject>,
        bucket: TrainingPlanSubjectBucket,
        targetCredits: (MutableTrainingPlanItem) -> String
    ) {
        if (subjects.isEmpty()) return
        val pool = subjects.toMutableList()
        val targets = categories.mapNotNull { category ->
            val expected = trainingPlanCreditUnits(targetCredits(category))
            val assigned = category.subjects
                .filter { trainingPlanSubjectBucket(it) == bucket }
                .sumOf { trainingPlanCreditUnits(it.credit) }
            val remaining = (expected - assigned).coerceAtLeast(0)
            if (remaining > 0) category to remaining else null
        }

        targets.forEach { (category, targetUnits) ->
            val selected = findTrainingPlanCreditSubsetInPageOrder(pool, targetUnits)
            selected.forEach { subject ->
                if (subject !in category.subjects) category.subjects += subject
            }
            pool.removeAll(selected.toSet())
        }
    }

    private fun findTrainingPlanCreditSubsetInPageOrder(
        subjects: List<RemoteTrainingPlanSubject>,
        targetUnits: Int
    ): List<RemoteTrainingPlanSubject> {
        if (targetUnits <= 0 || subjects.isEmpty()) return emptyList()
        var accumulatedUnits = 0
        subjects.forEachIndexed { index, subject ->
            accumulatedUnits += trainingPlanCreditUnits(subject.credit)
            if (accumulatedUnits == targetUnits) {
                return subjects.subList(0, index + 1).toList()
            }
            if (accumulatedUnits > targetUnits) return@forEachIndexed
        }
        return findTrainingPlanCreditSubset(subjects, targetUnits)
    }

    private fun findTrainingPlanCreditSubset(
        subjects: List<RemoteTrainingPlanSubject>,
        targetUnits: Int
    ): List<RemoteTrainingPlanSubject> {
        if (targetUnits <= 0 || subjects.isEmpty()) return emptyList()
        val paths = arrayOfNulls<List<Int>>(targetUnits + 1)
        paths[0] = emptyList()
        subjects.forEachIndexed { index, subject ->
            val units = trainingPlanCreditUnits(subject.credit)
            if (units <= 0 || units > targetUnits) return@forEachIndexed
            for (sum in targetUnits downTo units) {
                if (paths[sum] == null && paths[sum - units] != null) {
                    paths[sum] = paths[sum - units].orEmpty() + index
                }
            }
        }
        return paths[targetUnits].orEmpty().map(subjects::get)
    }

    private fun trainingPlanCreditUnits(value: String): Int =
        ((value.toDoubleOrNull() ?: 0.0) * 10.0).roundToInt()

    private fun trainingPlanSubjectBucket(subject: RemoteTrainingPlanSubject): TrainingPlanSubjectBucket {
        val status = cleanTrainingPlanText(subject.status)
        val score = cleanTrainingPlanText(subject.score)
        return when {
            status.contains("正修") || status.contains("在修") ||
                status.contains("修读中") || status.contains("在读") -> TrainingPlanSubjectBucket.CURRENT
            status.contains("已修") || status.contains("已完成") ||
                status.contains("通过") || status.contains("及格") ||
                hasPublishedTrainingPlanScore(score) -> TrainingPlanSubjectBucket.COMPLETED
            else -> TrainingPlanSubjectBucket.UNCOMPLETED
        }
    }

    private fun hasPublishedTrainingPlanScore(score: String): Boolean =
        score.toDoubleOrNull() != null || score in setOf("优秀", "良好", "中等", "及格", "合格", "通过")

    private fun trainingPlanCategoryForCode(code: String): String? = when (code) {
        "XF" -> "专业方向课"
        "BS" -> "实践教学环节"
        "XY" -> "艺术审美类"
        "XZ", "XR", "XG" -> "耕读教育类"
        "XT" -> "体育健康类"
        "XD" -> "四史教育类"
        else -> null
    }

    private fun trainingPlanCategoryForCourse(
        courseType: String,
        code: String,
        courseName: String
    ): String? {
        val normalizedType = cleanTrainingPlanText(courseType)
        val normalizedName = cleanTrainingPlanText(courseName)
        return when {
            normalizedType.contains("学科基础") -> "学科基础课组"
            (normalizedType.contains("通识") || normalizedType.contains("公共")) &&
                normalizedType.contains("必修") -> "通识必修课"
            normalizedType.contains("实践教学") -> "实践教学环节"
            normalizedType.contains("专业方向") -> "专业方向课"
            normalizedType.contains("专业") &&
                (normalizedType.contains("核心") || normalizedType.contains("必修")) -> "专业核心课"
            normalizedType.contains("艺术审美") -> "艺术审美类"
            normalizedType.contains("耕读教育") -> "耕读教育类"
            normalizedType.contains("体育健康") -> "体育健康类"
            normalizedType.contains("四史教育") -> "四史教育类"
            code == "BK" && isGeneralMandatoryTrainingPlanCourse(normalizedName) -> "通识必修课"
            else -> trainingPlanCategoryForCode(code)
        }
    }

    private fun isGeneralMandatoryTrainingPlanCourse(courseName: String): Boolean {
        val keywords = listOf(
            "计算机导论",
            "思想道德", "中国近现代史", "马克思主义", "毛泽东思想", "习近平新时代",
            "形势与政策", "大学英语", "大学体育", "普通体育课", "军事理论", "军事技能",
            "心理健康", "职业生涯", "就业指导", "就业教育", "创新创业", "劳动教育",
            "国家安全", "安全教育", "通识"
        )
        return keywords.any(courseName::contains) || courseName.startsWith("体育")
    }

    private fun canonicalTrainingPlanCategory(value: String): String {
        val normalized = cleanTrainingPlanText(value)
        return when {
            normalized.contains("学科基础") -> "学科基础课组"
            normalized.contains("通识") && normalized.contains("必修") -> "通识必修课"
            normalized.contains("实践教学") -> "实践教学环节"
            normalized.contains("专业") &&
                (normalized.contains("核心") || normalized.contains("必修")) -> "专业核心课"
            normalized.contains("专业方向") -> "专业方向课"
            normalized.contains("艺术审美") -> "艺术审美类"
            normalized.contains("耕读教育") -> "耕读教育类"
            normalized.contains("体育健康") -> "体育健康类"
            normalized.contains("四史教育") -> "四史教育类"
            else -> normalized
        }
    }

    private fun isTrainingPlanTerm(value: String): Boolean =
        Regex("^\\d{4}-\\d{4}-[12]$").matches(value.trim())

    private fun formatTrainingPlanCredit(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)
    }

    private fun cleanTrainingPlanText(value: String): String {
        return value
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseCourses(body: String): List<RemoteCourse> {
        val data = JSONObject(body).optJSONArray("data")
            ?: throw IllegalStateException("教务系统课程数据格式变化")
        val result = mutableListOf<RemoteCourse>()
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val name = clean(item.optString("kc_mc"))
            if (name.isEmpty()) continue
            val teacher = clean(item.optString("xm"))
            val courseCode = clean(item.optString("kch"))
            val roomLines = splitLines(item.optString("skdd"))
            parseMeetings(item.optString("sksj")).forEachIndexed { meetingIndex, meeting ->
                val room = normalizeClassroomName(
                    roomLines.getOrNull(meetingIndex) ?: roomLines.firstOrNull().orEmpty()
                )
                result += RemoteCourse(meeting.day, meeting.startSlot, meeting.slotCount, name, room, teacher, meeting.weeks, courseCode)
            }
        }
        // Keep alternating/short-term classes that share a time and room but
        // differ in their week ranges.
        return result.distinct()
    }

    private fun streamPublicValue(
        reader: JsonReader,
        depth: Int,
        onCourse: (RemotePublicCourse) -> Unit
    ): Int {
        if (depth > 4) {
            reader.skipValue()
            return 0
        }
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                var count = 0
                reader.beginArray()
                while (reader.hasNext()) count += streamPublicValue(reader, depth + 1, onCourse)
                reader.endArray()
                count
            }
            JsonToken.BEGIN_OBJECT -> streamPublicObject(reader, depth, onCourse)
            else -> {
                reader.skipValue()
                0
            }
        }
    }

    private fun streamPublicObject(
        reader: JsonReader,
        depth: Int,
        onCourse: (RemotePublicCourse) -> Unit
    ): Int {
        val row = JSONObject()
        var nestedCount = 0
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            val token = reader.peek()
            if (name in PUBLIC_SCHEDULE_CONTAINER_KEYS &&
                (token == JsonToken.BEGIN_ARRAY || token == JsonToken.BEGIN_OBJECT)) {
                nestedCount += streamPublicValue(reader, depth + 1, onCourse)
                continue
            }
            when (token) {
                JsonToken.STRING, JsonToken.NUMBER -> row.put(name, reader.nextString())
                JsonToken.BOOLEAN -> row.put(name, reader.nextBoolean())
                JsonToken.NULL -> reader.nextNull()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val records = parsePublicCourseRow(row)
        records.forEach(onCourse)
        return nestedCount + records.size
    }

    private fun parsePublicCourseRow(row: JSONObject): List<RemotePublicCourse> {
        val name = clean(jsonText(row, "kcmc", "kc_mc", "courseName", "course", "name"))
        if (name.isBlank()) return emptyList()
        val meetings = parsePublicMeetings(row)
        if (meetings.isEmpty()) return emptyList()
        val college = clean(jsonText(row, "dwmc", "college", "yxmc", "skyx"))
        val grade = normalizeGrade(jsonText(row, "ksnd", "grade", "nj"))
        val major = clean(jsonText(row, "zymc", "major", "zy"))
        val className = clean(jsonText(row, "bj", "skbj", "className", "bjmc"))
        val room = normalizeClassroomName(
            cleanLocation(jsonText(row, "skdd", "jxdd", "room", "location", "jsmc"))
        )
        val teacher = clean(jsonText(row, "xm", "jsxm", "teacher", "js"))
            .replace(Regex("\\s*\\[[^]]*\\]"), "")
        val code = clean(jsonText(row, "kch", "courseCode", "kcdm"))
        return meetings.map { meeting ->
            RemotePublicCourse(
                college, grade, major, className,
                meeting.day, meeting.startSlot, meeting.slotCount,
                name, room, teacher, meeting.weeks, code
            )
        }
    }

    private fun parsePublicMeetings(row: JSONObject): List<RemoteMeeting> {
        val normalizedDay = jsonText(row, "day").toIntOrNull()
        val normalizedStart = jsonText(row, "startSlot").toIntOrNull()
        val normalizedCount = jsonText(row, "slotCount").toIntOrNull()
        if (normalizedDay != null && normalizedDay in 0..6 &&
            normalizedStart != null && normalizedStart in 0..9 &&
            normalizedCount != null && normalizedCount > 0 &&
            normalizedStart + normalizedCount <= 10) {
            return listOf(RemoteMeeting(
                normalizedDay,
                normalizedStart,
                normalizedCount,
                normalizeWeekText(jsonText(row, "weeks", "week", "weekRange"))
            ))
        }
        val rawMeeting = jsonText(row, "sksj", "courseTime", "time", "schedule")
        val parsed = parseMeetings(rawMeeting)
        if (parsed.isNotEmpty()) return parsed

        val dayText = jsonText(row, "zzdweek", "skxq", "xqj", "xq", "weekday", "weekDay")
        val day = when {
            dayText.contains("一") || dayText.contains("1") -> 0
            dayText.contains("二") || dayText.contains("2") -> 1
            dayText.contains("三") || dayText.contains("3") -> 2
            dayText.contains("四") || dayText.contains("4") -> 3
            dayText.contains("五") || dayText.contains("5") -> 4
            dayText.contains("六") || dayText.contains("6") -> 5
            dayText.contains("日") || dayText.contains("天") || dayText.contains("7") -> 6
            else -> -1
        }
        val sectionText = jsonText(row, "jc", "jcs", "section", "sections", "skjc")
        val sections = Regex("\\d+").findAll(sectionText).mapNotNull { it.value.toIntOrNull() }.toList()
        if (day !in 0..6 || sections.isEmpty()) return emptyList()
        val start = sections.first() - 1
        val end = sections.last() - 1
        if (start !in 0..9 || end !in start..9) return emptyList()
        return listOf(RemoteMeeting(day, start, end - start + 1,
            normalizeWeekText(jsonText(row, "kkzc", "zc", "zcmc", "weeks", "week", "weekRange"))))
    }

    private fun normalizeGrade(value: String): String = clean(value).removeSuffix("级")

    private fun parseStudentProfile(html: String, fallbackStudentId: String): RemoteStudentProfile? {
        val title = Regex(
            "<[^>]*class\\s*=\\s*[\\\"'][^\\\"']*\\binfoContentTitle\\b[^\\\"']*[\\\"'][^>]*>([\\s\\S]*?)</[^>]+>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)?.let(::clean).orEmpty()

        val titleMatch = Regex("(.+?)-([0-9]{6,})$").find(title)
        val nameFromTitle = titleMatch?.groupValues?.getOrNull(1).orEmpty().trim()
        val idFromTitle = titleMatch?.groupValues?.getOrNull(2).orEmpty().trim()

        val nameFromDetail = Regex(
            "(?:姓名|名字)\\s*[：:]\\s*([^<\\r\\n]+)",
            RegexOption.IGNORE_CASE
        ).find(clean(html))?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val name = nameFromTitle.ifBlank { nameFromDetail }
        val studentId = idFromTitle.ifBlank { fallbackStudentId.trim() }
        return if (name.isNotBlank() && studentId.isNotBlank()) {
            RemoteStudentProfile(name, studentId)
        } else {
            null
        }
    }

    private fun parsePersonalTimetable(html: String): List<PersonalMeeting> {
        val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val cellRegex = Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val detailRegex = Regex("时间\\s*:\\s*(.*?)\\s*\\[(\\d+)(?:-(\\d+))?节\\]\\s*;\\s*地点\\s*:\\s*([^;]+?)\\s*;\\s*课程编号\\s*:\\s*([A-Za-z0-9]+)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val courseRegex = Regex("([^;]+?)\\s+老师\\s*:\\s*[^;]*;\\s*时间\\s*:\\s*(.*?)\\s*\\[(\\d+)(?:-(\\d+))?节\\]\\s*;\\s*地点\\s*:\\s*([^;]+?)\\s*;\\s*课程编号\\s*:\\s*([A-Za-z0-9]+)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val itemRegex = Regex("<li[^>]*class=[\\\"'][^\\\"']*courselists-item[^\\\"']*[\\\"'][^>]*>(.*?)</li>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val titleRegex = Regex("qz-hasCourse-title[^>]*>(.*?)</div>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val result = mutableListOf<PersonalMeeting>()
        rowRegex.findAll(html).forEach { row ->
            val cells = cellRegex.findAll(row.groupValues[1]).map { it.groupValues[1] }.toList()
            for (index in 1 until minOf(cells.size, 8)) {
                val cell = cells[index]
                itemRegex.findAll(cell).forEach itemLoop@ { item ->
                    val nameMatch = titleRegex.find(item.groupValues[1]) ?: return@itemLoop
                    val name = clean(nameMatch.groupValues[1])
                    val match = detailRegex.find(clean(item.groupValues[1])) ?: return@itemLoop
                    val start = match.groupValues[2].toIntOrNull()?.minus(1) ?: return@itemLoop
                    val end = (match.groupValues[3].toIntOrNull()?.minus(1) ?: start)
                    if (start !in 0..9 || end !in start..9) return@itemLoop
                    result += PersonalMeeting(name, match.groupValues[5].trim(), index - 1, start, end - start + 1, normalizeWeekText(match.groupValues[1]), clean(match.groupValues[4]))
                }
                // Newer timetable pages can use plain text cells instead of
                // courselists-item nodes.
                courseRegex.findAll(clean(cell)).forEach courseLoop@ { match ->
                    val start = match.groupValues[3].toIntOrNull()?.minus(1) ?: return@courseLoop
                    val end = match.groupValues[4].toIntOrNull()?.minus(1) ?: start
                    if (start !in 0..9 || end !in start..9) return@courseLoop
                    result += PersonalMeeting(clean(match.groupValues[1]), match.groupValues[6].trim(), index - 1, start, end - start + 1, normalizeWeekText(match.groupValues[2]), clean(match.groupValues[5]))
                }
            }
        }
        return result.distinct()
    }

    private fun parseMeetings(raw: String): List<RemoteMeeting> {
        val timeRegex = Regex("周([一二三四五六日])\\s*([0-9][0-9\\s,，、-]*)\\s*节")
        val days = mapOf('一' to 0, '二' to 1, '三' to 2, '四' to 3, '五' to 4, '六' to 5, '日' to 6)
        return splitLines(raw).mapNotNull { line ->
            val normalized = line.replace("星期", "周").replace("第", "")
            val match = timeRegex.find(normalized) ?: return@mapNotNull null
            val day = days[match.groupValues[1][0]] ?: return@mapNotNull null
            val token = match.groupValues[2].replace(Regex("\\s+"), "")
            val numbers = if (token.matches(Regex("\\d{4,}")) && token.length % 2 == 0) {
                token.chunked(2).mapNotNull { it.toIntOrNull() }
            } else {
                Regex("\\d+").findAll(token).mapNotNull { it.value.toIntOrNull() }.toList()
            }
            if (numbers.isEmpty()) return@mapNotNull null
            val start = numbers.first() - 1
            val end = numbers.last() - 1
            if (start !in 0..9 || end !in start..9) return@mapNotNull null
            val weeks = normalizeWeekText(normalized)
            RemoteMeeting(day, start, end - start + 1, weeks)
        }.distinct()
    }

    /**
     * 支持 `9-10周，3-5周；8周`、`9-10,3-5;8周` 等混合格式。
     * 周次通常位于“周一/星期一”等上课星期之前；若星期在前，则只提取明确带“周”的区间，
     * 避免把 01-02 节误认为周次。
     */
    private fun normalizeWeekText(raw: String): String {
        val normalized = raw
            .replace("星期", "周")
            .replace("第", "")
            .replace("—", "-")
            .replace("至", "-")
        val weekday = Regex("周[一二三四五六日]").find(normalized)
        val beforeWeekday = weekday?.range?.first?.takeIf { it > 0 }?.let { normalized.substring(0, it) }.orEmpty()
        val ranges = if (beforeWeekday.any(Char::isDigit) || weekday == null) {
            val source = beforeWeekday.takeIf { it.any(Char::isDigit) } ?: normalized
            Regex("(\\d+)\\s*(?:-\\s*(\\d+))?").findAll(source)
        } else {
            Regex("(\\d+)\\s*(?:-\\s*(\\d+))?\\s*周").findAll(normalized)
        }
        return ranges.mapNotNull { match ->
            val start = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val end = match.groupValues[2].toIntOrNull()
            if (end != null) "$start-$end" else start.toString()
        }.distinct().joinToString(",")
    }

    private fun request(
        path: String,
        method: String,
        form: Map<String, String>?,
        referer: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        var lastError: IOException? = null
        repeat(REQUEST_ATTEMPTS) { attempt ->
            try {
                return synchronized(COOKIE_HANDLER_LOCK) {
                    CookieHandler.setDefault(cookies)
                    executeRequest(path, method, form, referer, extraHeaders)
                }
            } catch (error: IOException) {
                lastError = error
                if (attempt + 1 < REQUEST_ATTEMPTS) {
                    try {
                        Thread.sleep(250L * (attempt + 1))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw error
                    }
                }
            }
        }
        throw lastError ?: IOException("教务系统请求失败")
    }

    private fun executeRequest(
        path: String,
        method: String,
        form: Map<String, String>?,
        referer: String?,
        extraHeaders: Map<String, String>
    ): String {
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 30000
            requestMethod = method
            setRequestProperty("Accept", "text/html,application/json,*/*")
            setRequestProperty("User-Agent", "SDAU-ClassSchedule-Android/2.0")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Pragma", "no-cache")
            // Some mobile HTTP stacks transparently negotiate gzip.  The
            // school server occasionally truncates that response, so request
            // an uncompressed body and decode it explicitly below.
            setRequestProperty("Accept-Encoding", "identity")
            if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
            extraHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
            // The legacy login endpoint validates that the encrypted form was
            // submitted from its login page.  Without this it may return an
            // opaque server-side StringIndexOutOfBounds error.
            if (path == "/xk/LoginToXk") {
                setRequestProperty("Referer", "$BASE_URL/")
            }
        }
        try {
            if (form != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                val encoded = form.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
                }
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(encoded) }
            }
            val status = connection.responseCode
            val stream = (if (status >= 400) connection.errorStream else connection.inputStream)
                ?: throw IOException("教务系统无响应（HTTP $status）")
            val bytes = stream.use { it.readBytes() }
            val charset = responseCharset(connection.contentType)
            val body = bytes.toString(charset)
            if (status in RETRYABLE_HTTP_STATUSES || status in 500..599) {
                throw IOException("教务系统暂时不可用（HTTP $status）")
            }
            if (status !in 200..299) throw IllegalStateException("教务系统返回 HTTP $status")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun responseCharset(contentType: String?): Charset {
        val name = Regex("charset\\s*=\\s*[\\\"']?([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
            .find(contentType.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
        return name?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: StandardCharsets.UTF_8
    }

    private fun buildCredential(account: String, password: String, seed: String, sxh: String): String {
        val code = "${b64(account)}%%%${b64(password)}%%%${b64(" ")}"
        var remaining = seed
        val output = StringBuilder()
        for (index in code.indices) {
            if (index >= 55) { output.append(code.substring(index)); break }
            val take = sxh.getOrNull(index)?.digitToIntOrNull() ?: 0
            val actual = minOf(take, remaining.length)
            output.append(code[index])
            output.append(remaining.take(actual))
            remaining = remaining.drop(actual)
        }
        return output.toString()
    }

    private fun b64(value: String) = Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun capture(value: String, expression: String) = Regex(expression, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(value)?.groupValues?.getOrNull(1)?.trim().orEmpty()
    private fun clean(value: String) = value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    private fun cleanLocation(value: String) = value.replace(Regex("(?i)<br\\s*/?>"), " / ").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    private fun splitLines(value: String) = value.replace(Regex("(?i)<br\\s*/?>"), "\n").split('\n').map { clean(it) }.filter { it.isNotEmpty() }

    private data class MutableTrainingPlanItem(
        val category: String,
        val requiredCredits: String,
        val completedCredits: String,
        val currentCredits: String,
        val remainingCredits: String,
        val subjects: MutableList<RemoteTrainingPlanSubject> = mutableListOf()
    )

    private enum class TrainingPlanSubjectBucket { COMPLETED, CURRENT, UNCOMPLETED }

    companion object {
        private val BK_TRAINING_PLAN_CATEGORIES = setOf("学科基础课组", "通识必修课", "专业核心课")
        private val COOKIE_HANDLER_LOCK = Any()
        private const val REQUEST_ATTEMPTS = 3
        private val RETRYABLE_HTTP_STATUSES = setOf(408, 425, 429)
        private val PUBLIC_SCHEDULE_CONTAINER_KEYS = setOf("rows", "data", "list", "result", "records")
        private const val BASE_URL = "https://jw.sdau.edu.cn"
        private const val PUBLIC_SCHEDULE_MIRROR_BASE = "https://gitee.com/sleexy/onlinedata/raw/master"
    }
}

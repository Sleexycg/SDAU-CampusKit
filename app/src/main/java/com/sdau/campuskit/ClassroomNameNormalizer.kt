package com.sdau.campuskit

/**
 * Converts verbose classroom names returned by the academic system into the
 * compact names used throughout the app. Unknown names are preserved.
 */
internal fun normalizeClassroomName(rawName: String): String {
    if (rawName.isBlank()) return rawName.trim()
    return rawName
        .split(Regex("\\s*/\\s*"))
        .joinToString(" / ") { normalizeSingleClassroomName(it) }
}

private fun normalizeSingleClassroomName(rawName: String): String {
    var name = rawName.trim()
        .replace('＃', '#')

    name = Regex("^北校(\\d+)号楼(.+)$").replace(name) { match ->
        "${match.groupValues[1]}#${match.groupValues[2]}"
    }
    name = Regex("^北校文理大楼(.+)$").replace(name, "文理大楼$1")
    name = Regex("^北校学实楼(.+)$").replace(name, "学实楼$1")
    name = Regex("^北校音乐楼(.+)$").replace(name, "音乐楼$1")

    name = Regex("^南校区实验楼([ABC])楼\\1(.+)$", RegexOption.IGNORE_CASE)
        .replace(name) { match ->
            "实验楼${match.groupValues[1].uppercase()}${match.groupValues[2]}"
        }
    name = Regex("^(实验楼[ABC]\\d+)[春秋]$", RegexOption.IGNORE_CASE)
        .replace(name, "$1")

    name = when {
        name == "南校区标本园" || name == "南校区标本圆" -> "科技创新园"
        Regex("^西北区体育.*$", RegexOption.IGNORE_CASE).matches(name) -> "西北片区体育场"
        name == "南校区体育北足球场" -> "北操足球场"
        name == "南校区体育南足球场" -> "南操足球场"
        else -> name
    }
    name = Regex("^南校区体育排球场V(\\d+)$", RegexOption.IGNORE_CASE)
        .replace(name, "排球场-$1")
    name = Regex("^南校区体育篮球场M(\\d+)$", RegexOption.IGNORE_CASE)
        .replace(name, "篮球场-$1")
    name = Regex("^北校区体育体育场(\\d+)$").replace(name, "体育场-$1")
    name = Regex("^[北南]校区体育(.+)$").replace(name, "$1")

    name = Regex("^档案馆楼T(\\d+)([A-Z])$", RegexOption.IGNORE_CASE)
        .replace(name) { match ->
            "档案馆T${match.groupValues[1]}-${match.groupValues[2].uppercase()}"
        }
    name = Regex("^南校区菌物基地实验室(\\d+)$").replace(name, "菌物基地实验室-$1")
    name = Regex("^南校区林学综合实验站(\\d+)楼(.+)$").replace(name) { match ->
        "林学实验站${match.groupValues[1]}F-${match.groupValues[2]}"
    }
    name = Regex("^南校区动物保健医院(.+)$").replace(name, "动物保健医院$1")
    name = Regex("^[北南]校实践环节地点(.+?)[NS]?$", RegexOption.IGNORE_CASE)
        .replace(name) { match -> "实践环节${match.groupValues[1]}地" }

    return name
}

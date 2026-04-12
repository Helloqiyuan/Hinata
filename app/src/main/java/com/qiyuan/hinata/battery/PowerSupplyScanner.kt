package com.qiyuan.hinata.battery

import com.qiyuan.hinata.AppLogger

/**
 * 扫描 `/sys/class/power_supply` 下的条目，并对 bms/battery 等尝试读取常见节点。
 */
object PowerSupplyScanner {

    private val interestingFiles = listOf(
        "charge_full_design",
        "charge_full",
        // 循环次数：不同机型节点名不一，多路径尝试
        "cycle_count",
        "cycle_counts",
        "battery_cycle",
        "charge_cycle",
        "fg_cycle",
        "voltage_now",
        "voltage_max",
        "voltage_ocv",
        "temp",
        "model_name",
        "technology",
        "type",
        "capacity",
        "current_now",
        "status",
        "usb_type",
        "charge_type",
    )

    data class SysfsRead(
        val supplyName: String,
        val fileName: String,
        val path: String,
        val content: String?,
        val exitCode: Int,
    )

    /** 脚注用：自根目录起的 sysfs 绝对路径 */
    private fun pathSource(hit: SysfsRead): String =
        hit.path.ifEmpty { "/sys/class/power_supply/${hit.supplyName}/${hit.fileName}" }

    /** 解析 `ls` 输出得到 supply 名称列表 */
    fun parseLsOutput(output: String): List<String> {
        return output.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(' ') }
    }

    /**
     * 在 root 下读取多个 power_supply 的关键文件（调用方须已校验 Root）。
     */
    fun readSupplies(): Map<String, List<SysfsRead>> {
        val ls = RootShell.lsSafeDir("/sys/class/power_supply")
        if (ls.exitCode != 0) {
            AppLogger.append("ls power_supply 失败: ${ls.output.take(200)}")
            return emptyMap()
        }
        val names = parseLsOutput(ls.output)
        AppLogger.append("发现 power_supply: ${names.joinToString()}")
        val result = linkedMapOf<String, MutableList<SysfsRead>>()
        val priority = listOf("bms", "battery", "BAT0")
        val ordered = names.sortedBy { name ->
            val idx = priority.indexOf(name)
            if (idx >= 0) idx else 100
        }
        for (name in ordered) {
            val list = mutableListOf<SysfsRead>()
            for (file in interestingFiles) {
                val path = "/sys/class/power_supply/$name/$file"
                if (!RootShell.isSafePowerSupplyPath(path)) continue
                val r = RootShell.catSafePath(path)
                val content = if (r.exitCode == 0) r.output.trim() else null
                list.add(SysfsRead(name, file, path, content, r.exitCode))
            }
            result[name] = list
        }
        return result
    }

    /** 从扫描结果中取第一个非空数值（按 supply 优先级） */
    fun firstLongUah(map: Map<String, List<SysfsRead>>, fileName: String): Pair<Long?, String> {
        for ((supply, reads) in map) {
            val hit = reads.find { it.fileName == fileName && it.content != null } ?: continue
            val v = hit.content?.trim()?.toLongOrNull()
            if (v != null) return v to pathSource(hit)
        }
        return null to ""
    }

    fun firstMicrovolt(map: Map<String, List<SysfsRead>>, fileName: String): Pair<Long?, String> {
        for ((supply, reads) in map) {
            val hit = reads.find { it.fileName == fileName && it.content != null } ?: continue
            val v = hit.content?.trim()?.toLongOrNull()
            if (v != null) return v to pathSource(hit)
        }
        return null to ""
    }

    /** 有符号瞬时电流（µA），与内核 current_now 一致 */
    fun firstSignedMicroAmp(map: Map<String, List<SysfsRead>>, fileName: String): Pair<Long?, String> {
        for ((supply, reads) in map) {
            val hit = reads.find { it.fileName == fileName && it.content != null } ?: continue
            val raw = hit.content?.trim() ?: continue
            val v = parseSignedLong(raw) ?: continue
            return v to pathSource(hit)
        }
        return null to ""
    }

    /** 解析可能带符号与空白的整数 */
    private fun parseSignedLong(s: String): Long? {
        val t = s.trim().removePrefix("+")
        if (t.isEmpty()) return null
        val neg = t.startsWith('-')
        val digits = t.removePrefix("-").filter { it.isDigit() }
        if (digits.isEmpty()) return null
        val n = digits.toLongOrNull() ?: return null
        return if (neg) -n else n
    }

    /** 电量 0–100，优先 bms/battery/BAT0 */
    fun firstCapacityPercent(map: Map<String, List<SysfsRead>>): Pair<Int?, String> {
        val preferred = listOf("bms", "battery", "BAT0")
        for (name in preferred) {
            val reads = map[name] ?: continue
            val hit = reads.find { it.fileName == "capacity" && it.content != null } ?: continue
            val v = hit.content?.trim()?.toIntOrNull()
            if (v != null && v in 0..100) return v to pathSource(hit)
        }
        for ((supply, reads) in map) {
            if (supply in preferred) continue
            val hit = reads.find { it.fileName == "capacity" && it.content != null } ?: continue
            val v = hit.content?.trim()?.toIntOrNull()
            if (v != null && v in 0..100) return v to pathSource(hit)
        }
        return null to ""
    }

    /** 电池状态字符串，用于校正电流符号 */
    fun firstBatteryStatus(map: Map<String, List<SysfsRead>>): Pair<String?, String> {
        val preferred = listOf("bms", "battery", "BAT0")
        for (name in preferred) {
            val reads = map[name] ?: continue
            val hit = reads.find { it.fileName == "status" && it.content != null } ?: continue
            val s = hit.content?.trim()
            if (!s.isNullOrEmpty()) return s to pathSource(hit)
        }
        for ((supply, reads) in map) {
            if (supply in preferred) continue
            val hit = reads.find { it.fileName == "status" && it.content != null } ?: continue
            val s = hit.content?.trim()
            if (!s.isNullOrEmpty()) return s to pathSource(hit)
        }
        return null to ""
    }

    /** temp 多为 0.1℃ 整数 */
    fun firstTempTenths(map: Map<String, List<SysfsRead>>): Pair<Int?, String> {
        for ((supply, reads) in map) {
            val hit = reads.find { it.fileName == "temp" && it.content != null } ?: continue
            val v = hit.content?.trim()?.toIntOrNull()
            if (v != null) return v to pathSource(hit)
        }
        return null to ""
    }

    fun firstString(map: Map<String, List<SysfsRead>>, fileName: String): Pair<String?, String> {
        for ((supply, reads) in map) {
            val hit = reads.find { it.fileName == fileName && it.content != null } ?: continue
            val s = hit.content?.trim()
            if (!s.isNullOrEmpty()) return s to pathSource(hit)
        }
        return null to ""
    }

    /** 按优先级尝试多个「循环次数」节点，取第一个非负整数 */
    fun firstCycleCount(map: Map<String, List<SysfsRead>>): Pair<Int?, String> {
        val names = listOf(
            "cycle_count",
            "cycle_counts",
            "battery_cycle",
            "charge_cycle",
            "fg_cycle",
        )
        for (file in names) {
            val (s, src) = firstString(map, file)
            val n = s?.filter { it.isDigit() }?.toIntOrNull()
            if (n != null && n >= 0) return n to src
        }
        return null to ""
    }

    /**
     * 直接 cat [RootShell.QCOM_BATTERY_REAL_TYPE_PATH]（全量读时 map 中尚无 qcom-battery）。
     */
    private fun readQcomBatteryRealTypeDirect(): Pair<String?, String> {
        val path = RootShell.QCOM_BATTERY_REAL_TYPE_PATH
        val r = RootShell.catSafePath(path)
        val line = if (r.exitCode == 0) {
            r.output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        } else {
            null
        }
        return line to path
    }

    /**
     * 充电协议：仅使用 `qcom-battery/real_type`；内容为 `unknown`（忽略大小写）时主界面展示「未连接」。
     */
    fun pickChargingProtocolRealTypeOnly(map: Map<String, List<SysfsRead>>): Pair<String?, String> {
        val path = RootShell.QCOM_BATTERY_REAL_TYPE_PATH
        val fromMap = map["qcom-battery"]
            ?.find { it.fileName == "real_type" && it.content != null }
            ?.content
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
        val raw = fromMap ?: readQcomBatteryRealTypeDirect().first
        if (raw.isNullOrBlank()) {
            return null to path
        }
        val t = raw.trim()
        if (t.equals("unknown", ignoreCase = true)) {
            return "未连接" to path
        }
        return t to path
    }

    /**
     * 从 ls 结果中选电池 supply 名（与全量扫描优先级一致）。
     */
    fun pickPreferredBatterySupplyName(names: List<String>): String? {
        val set = names.toSet()
        for (pref in listOf("bms", "battery", "BAT0")) {
            if (pref in set) return pref
        }
        return names.firstOrNull()
    }

    /**
     * 定时刷新：电池动态节点 + [RootShell.QCOM_BATTERY_REAL_TYPE_PATH]（单次 su 批量读取）。
     */
    @Suppress("UNUSED_PARAMETER")
    fun buildLiveReadPaths(batName: String?, names: Set<String>): List<String> {
        val paths = mutableListOf<String>()
        if (RootShell.isSafeReadableSysfsPath(RootShell.QCOM_BATTERY_REAL_TYPE_PATH)) {
            paths.add(RootShell.QCOM_BATTERY_REAL_TYPE_PATH)
        }
        if (batName != null) {
            for (f in listOf(
                    "capacity",
                    "current_now",
                    "status",
                    "voltage_now",
                    "temp",
                    "charge_full",
                    "cycle_count",
                    "cycle_counts",
                    "battery_cycle",
                    "charge_cycle",
                    "fg_cycle",
                )) {
                val p = "/sys/class/power_supply/$batName/$f"
                if (RootShell.isSafePowerSupplyPath(p)) paths.add(p)
            }
        }
        return paths
    }
}

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
    )

    data class SysfsRead(
        val supplyName: String,
        val fileName: String,
        val path: String,
        val content: String?,
        val exitCode: Int,
    )

    /** 解析 `ls` 输出得到 supply 名称列表 */
    fun parseLsOutput(output: String): List<String> {
        return output.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(' ') }
    }

    /**
     * 在 root 下读取多个 power_supply 的关键文件。
     */
    fun readSupplies(rootOk: Boolean): Map<String, List<SysfsRead>> {
        if (!rootOk) return emptyMap()
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
            val hit = reads.find { it.fileName == fileName && it.content != null }
            val v = hit?.content?.trim()?.toLongOrNull()
            if (v != null) return v to "sysfs $supply/$fileName"
        }
        return null to ""
    }

    fun firstMicrovolt(map: Map<String, List<SysfsRead>>, fileName: String): Pair<Long?, String> {
        for ((supply, reads) in map) {
            val hit = reads.find { it.fileName == fileName && it.content != null }
            val v = hit?.content?.trim()?.toLongOrNull()
            if (v != null) return v to "sysfs $supply/$fileName"
        }
        return null to ""
    }

    /** temp 多为 0.1℃ 整数 */
    fun firstTempTenths(map: Map<String, List<SysfsRead>>): Pair<Int?, String> {
        for ((supply, reads) in map) {
            val hit = reads.find { it.fileName == "temp" && it.content != null }
            val v = hit?.content?.trim()?.toIntOrNull()
            if (v != null) return v to "sysfs $supply/temp"
        }
        return null to ""
    }

    fun firstString(map: Map<String, List<SysfsRead>>, fileName: String): Pair<String?, String> {
        for ((supply, reads) in map) {
            val hit = reads.find { it.fileName == fileName && it.content != null }
            val s = hit?.content?.trim()
            if (!s.isNullOrEmpty()) return s to "sysfs $supply/$fileName"
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
}

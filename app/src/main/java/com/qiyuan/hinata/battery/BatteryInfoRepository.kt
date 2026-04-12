package com.qiyuan.hinata.battery

import com.qiyuan.hinata.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聚合 sysfs（须 Root）+ dumpsys battery，均为只读。
 */
class BatteryInfoRepository {

    private companion object {
        /** dumpsys 脚注：可执行文件从根目录起的典型路径 */
        private const val DUMPSYS_BATTERY_CMD = "/system/bin/dumpsys battery"
    }

    /** 定时刷新不调用 dumpsys，解析占位 */
    private val emptyDumpsysHints =
        DumpsysParser.Hints(null, null, null, null, null, null, null, null)

    suspend fun load(): BatterySnapshot = withContext(Dispatchers.IO) {
        AppLogger.append("---------- 开始读取电池信息 ----------")
        if (!RootShell.checkRootAvailable()) {
            throw IllegalStateException("需要 Root 权限")
        }

        val sysfsMap = PowerSupplyScanner.readSupplies()
        val dumpsys = RootShell.dumpsysBattery()
        AppLogger.append("dumpsys battery 退出码=${dumpsys.exitCode}，长度=${dumpsys.output.length}")

        val hints = DumpsysParser.parse(dumpsys.output)

        val (designUah, designSrc) = pickDesignCapacity(sysfsMap, hints)
        val (fullUah, fullSrc) = pickFullCharge(sysfsMap, hints)
        val (cycle, cycleSrc) = pickCycleCount(sysfsMap, hints)
        val remainingLife = computeRemainingLifePercent(designUah, fullUah)

        val (tempC, tempSrc) = pickTemperature(sysfsMap)
        val (voltageV, voltSrc) = pickVoltage(sysfsMap)
        val (maxVV, maxSrc) = pickMaxVoltage(sysfsMap, hints)

        val (model, modelSrc) = pickModel(sysfsMap, hints)
        val (tech, _) = PowerSupplyScanner.firstString(sysfsMap, "technology")
        val (pct, pctSrc) = pickBatteryPercent(sysfsMap, hints)

        val (statusText, _) = PowerSupplyScanner.firstBatteryStatus(sysfsMap)
        val (rawUa, curSrc) = PowerSupplyScanner.firstSignedMicroAmp(sysfsMap, "current_now")
        val currentMicroA = rawUa?.let { normalizeCurrentSign(it, statusText) }
        val currentSource = curSrc.ifEmpty { "不可用" }

        val powerW = if (currentMicroA != null && voltageV != null) {
            (currentMicroA / 1_000_000.0) * voltageV
        } else {
            null
        }
        // 功率为计算值：脚注只写算式，不写 sysfs 路径
        val powerSource = when {
            powerW != null -> "功率 = 电流(A) × 电压(V)"
            else -> "需同时读到电流与电压"
        }

        val (proto, protoSrc) = PowerSupplyScanner.pickChargingProtocolRealTypeOnly(sysfsMap)
        val chargingProtocol = proto
        val chargingProtocolSource = when {
            proto != null -> protoSrc
            else -> "${RootShell.QCOM_BATTERY_REAL_TYPE_PATH} · 不可用"
        }

        BatterySnapshot(
            designCapacityUah = designUah,
            designCapacitySource = designSrc,
            fullChargeCapacityUah = fullUah,
            fullChargeCapacitySource = fullSrc,
            cycleCount = cycle,
            cycleCountSource = cycleSrc,
            remainingLifePercent = remainingLife,
            temperatureC = tempC,
            temperatureSource = tempSrc,
            voltageV = voltageV,
            voltageSource = voltSrc,
            maxVoltageV = maxVV,
            maxVoltageSource = maxSrc,
            modelName = model,
            modelSource = modelSrc,
            technology = tech ?: hints.technology,
            batteryPercent = pct,
            batteryPercentSource = pctSrc.ifEmpty { "不可用" },
            currentMicroA = currentMicroA,
            currentSource = currentSource,
            powerW = powerW,
            powerSource = powerSource,
            chargingProtocol = chargingProtocol,
            chargingProtocolSource = chargingProtocolSource,
        )
    }

    /**
     * 定时刷新：1 次 ls + 1 次批量 cat（无 dumpsys、不重复检测 root），用于更新动态字段。
     * @param designCapacityUahHint 全量读得到的设计容量，用于计算剩余寿命（定时读不再拉 charge_full_design）。
     */
    suspend fun loadLiveFields(designCapacityUahHint: Long?): BatteryLiveFields = withContext(Dispatchers.IO) {
        val ls = RootShell.lsSafeDir("/sys/class/power_supply", logCommand = false)
        if (ls.exitCode != 0) {
            throw IllegalStateException("ls power_supply 失败")
        }
        val names = PowerSupplyScanner.parseLsOutput(ls.output)
        if (names.isEmpty()) {
            throw IllegalStateException("power_supply 列表为空")
        }
        val bat = PowerSupplyScanner.pickPreferredBatterySupplyName(names)
        val paths = PowerSupplyScanner.buildLiveReadPaths(bat, names.toSet())
        if (paths.isEmpty()) {
            throw IllegalStateException("无可用 sysfs 路径")
        }
        val batch = RootShell.catSafePathsBatched(paths, logCommand = false)
        // 部分机型 shell 在批量命令下仍返回非 0，只要有可解析输出则继续
        if (batch.exitCode != 0) {
            AppLogger.append("定时刷新: 批量 cat exit=${batch.exitCode}，仍尝试解析")
        }
        val values = RootShell.parseBatchedCatOutput(batch.output, paths.size)
        val map = sysfsMapFromPathValues(paths, values)

        val (fullUah, fullSrcRaw) = pickFullCharge(map, emptyDumpsysHints)
        val remainingLife = computeRemainingLifePercent(designCapacityUahHint, fullUah)
        val (cycle, cycleSrcRaw) = pickCycleCount(map, emptyDumpsysHints)
        val (pct, pctSrcRaw) = pickBatteryPercent(map, emptyDumpsysHints)
        val (tempC, tempSrcRaw) = pickTemperature(map)
        val (voltageV, voltSrcRaw) = pickVoltage(map)
        val (statusText, _) = PowerSupplyScanner.firstBatteryStatus(map)
        val (rawUa, curSrcRaw) = PowerSupplyScanner.firstSignedMicroAmp(map, "current_now")
        val currentMicroA = rawUa?.let { normalizeCurrentSign(it, statusText) }
        val currentSource = curSrcRaw.ifEmpty { "不可用" }
        val voltTagged = voltSrcRaw.ifEmpty { "不可用" }

        val powerW = if (currentMicroA != null && voltageV != null) {
            (currentMicroA / 1_000_000.0) * voltageV
        } else {
            null
        }
        // 功率为计算值：脚注只写算式（「>」表示动态卡片，由界面层添加）
        val powerSource = when {
            powerW != null -> "功率 = 电流(A) × 电压(V)"
            else -> "需同时读到电流与电压"
        }

        val (proto, protoSrcRaw) = PowerSupplyScanner.pickChargingProtocolRealTypeOnly(map)
        val chargingProtocolSource = when {
            proto != null -> protoSrcRaw.ifEmpty { RootShell.QCOM_BATTERY_REAL_TYPE_PATH }
            else -> "${RootShell.QCOM_BATTERY_REAL_TYPE_PATH} · 不可用"
        }

        BatteryLiveFields(
            fullChargeCapacityUah = fullUah,
            fullChargeCapacitySource = fullSrcRaw.ifEmpty { "不可用" },
            remainingLifePercent = remainingLife,
            cycleCount = cycle,
            cycleCountSource = cycleSrcRaw.ifEmpty { "不可用" },
            batteryPercent = pct,
            batteryPercentSource = pctSrcRaw.ifEmpty { "不可用" },
            temperatureC = tempC,
            temperatureSource = tempSrcRaw.ifEmpty { "不可用" },
            voltageV = voltageV,
            voltageSource = voltTagged,
            currentMicroA = currentMicroA,
            currentSource = currentSource,
            powerW = powerW,
            powerSource = powerSource,
            chargingProtocol = proto,
            chargingProtocolSource = chargingProtocolSource,
        )
    }

    /** 将路径与 cat 结果还原为与全量扫描兼容的 map */
    private fun sysfsMapFromPathValues(
        paths: List<String>,
        values: List<String?>,
    ): Map<String, List<PowerSupplyScanner.SysfsRead>> {
        val m = linkedMapOf<String, MutableList<PowerSupplyScanner.SysfsRead>>()
        for (i in paths.indices) {
            val p = paths[i]
            val (supply, file) = when {
                p.startsWith("/sys/class/qcom-battery/") ->
                    "qcom-battery" to p.substringAfterLast('/')
                else ->
                    p.removePrefix("/sys/class/power_supply/").substringBefore('/') to p.substringAfterLast('/')
            }
            val content = values.getOrNull(i)
            val exit = if (content != null) 0 else 1
            m.getOrPut(supply) { mutableListOf() }
                .add(PowerSupplyScanner.SysfsRead(supply, file, p, content, exit))
        }
        return m
    }

    private fun pickDesignCapacity(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
    ): Pair<Long?, String> {
        val (a, s) = PowerSupplyScanner.firstLongUah(map, "charge_full_design")
        if (a != null) return a to s
        hints.chargeFullDesignUah?.let { return it to DUMPSYS_BATTERY_CMD }
        return null to "不可用"
    }

    private fun pickFullCharge(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
    ): Pair<Long?, String> {
        val (a, s) = PowerSupplyScanner.firstLongUah(map, "charge_full")
        if (a != null) return a to s
        hints.chargeFullUah?.let { return it to DUMPSYS_BATTERY_CMD }
        return null to "不可用"
    }

    /** 循环次数：优先 sysfs，其次 dumpsys 启发式 */
    private fun pickCycleCount(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
    ): Pair<Int?, String> {
        val (n, s) = PowerSupplyScanner.firstCycleCount(map)
        if (n != null) return n to s
        hints.cycleCount?.let { return it to DUMPSYS_BATTERY_CMD }
        return null to "不可用"
    }

    /** 满充/设计 估算剩余寿命百分比 */
    private fun computeRemainingLifePercent(
        designUah: Long?,
        fullUah: Long?,
    ): Double? {
        if (designUah == null || fullUah == null || designUah <= 0L) return null
        return (fullUah.toDouble() / designUah.toDouble() * 100.0).coerceIn(0.0, 100.0)
    }

    private fun pickTemperature(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
    ): Pair<Double?, String> {
        val (tenths, src) = PowerSupplyScanner.firstTempTenths(map)
        if (tenths != null) return (tenths / 10.0) to src
        return null to "不可用"
    }

    private fun pickVoltage(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
    ): Pair<Double?, String> {
        val (uv, src) = PowerSupplyScanner.firstMicrovolt(map, "voltage_now")
        if (uv != null) return (uv / 1_000_000.0) to src
        return null to "不可用"
    }

    private fun pickMaxVoltage(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
    ): Pair<Double?, String> {
        val (uv1, s1) = PowerSupplyScanner.firstMicrovolt(map, "voltage_max")
        if (uv1 != null) return (uv1 / 1_000_000.0) to s1
        val (uv2, s2) = PowerSupplyScanner.firstMicrovolt(map, "voltage_ocv")
        if (uv2 != null) return (uv2 / 1_000_000.0) to s2
        hints.maxVoltageV?.let { return it to DUMPSYS_BATTERY_CMD }
        return null to "不可用"
    }

    private fun pickModel(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
    ): Pair<String?, String> {
        val (m, s) = PowerSupplyScanner.firstString(map, "model_name")
        if (!m.isNullOrEmpty()) return m to s
        hints.model?.let { return it to DUMPSYS_BATTERY_CMD }
        return null to "不可用"
    }

    private fun pickBatteryPercent(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
    ): Pair<Int?, String> {
        val (c, s) = PowerSupplyScanner.firstCapacityPercent(map)
        if (c != null) return c to s
        hints.batteryPercent?.let { return it to DUMPSYS_BATTERY_CMD }
        return null to "不可用"
    }

    /**
     * 结合 status 将电流校正为：充电为正、放电为负。
     */
    private fun normalizeCurrentSign(rawUa: Long, status: String?): Long {
        if (status.isNullOrBlank()) return rawUa
        val sl = status.lowercase()
        val discharging = "discharging" in sl
        val charging = "charging" in sl && "not charging" !in sl
        if (discharging && rawUa > 0) return -rawUa
        if (charging && rawUa < 0) return -rawUa
        return rawUa
    }
}

/**
 * 从 dumpsys battery 文本中提取若干启发式字段（机型差异大，尽量宽松）。
 */
object DumpsysParser {

    data class Hints(
        val chargeFullDesignUah: Long?,
        val chargeFullUah: Long?,
        val maxVoltageV: Double?,
        val model: String?,
        val technology: String?,
        val cycleCount: Int?,
        val batteryPercent: Int?,
        val chargingType: String?,
    )

    fun parse(text: String): Hints {
        if (text.isBlank()) {
            return Hints(null, null, null, null, null, null, null, null)
        }
        var design: Long? = null
        var full: Long? = null
        var maxV: Double? = null
        var model: String? = null
        var tech: String? = null
        var cycles: Int? = null
        var level: Int? = null
        var scale: Int? = null
        var chargingType: String? = null

        text.lineSequence().forEach { line ->
            val l = line.trim()
            when {
                l.contains("charge full design", ignoreCase = true) ||
                    l.contains("Charge Full Design", ignoreCase = true) -> {
                    extractLongAfterColon(l)?.let { design = it }
                }
                l.contains("charge full", ignoreCase = true) && !l.contains("design", ignoreCase = true) -> {
                    extractLongAfterColon(l)?.let { full = it }
                }
                l.contains("POWER_SUPPLY_MODEL_NAME", ignoreCase = true) -> {
                    extractAfterEquals(l)?.let { model = it }
                }
                l.contains("technology", ignoreCase = true) && l.contains(":") -> {
                    extractAfterColon(l)?.let { tech = it }
                }
                l.contains("max", ignoreCase = true) && l.contains("voltage", ignoreCase = true) -> {
                    extractMilliOrMicroVolts(l)?.let { mv -> maxV = mv / 1000.0 }
                }
                l.contains("cycle", ignoreCase = true) && l.contains(":") -> {
                    extractLongAfterColon(l)?.toInt()?.let { c -> if (c >= 0) cycles = c }
                }
                l.contains("level", ignoreCase = true) && l.contains(":") -> {
                    Regex("""(?i)level\s*:\s*(\d+)""").find(l)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        if (it in 0..100) level = it
                    }
                }
                l.contains("scale", ignoreCase = true) && l.contains(":") -> {
                    Regex("""(?i)scale\s*:\s*(\d+)""").find(l)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        if (it > 0) scale = it
                    }
                }
                l.contains("usb type", ignoreCase = true) && l.contains(":") -> {
                    extractAfterColon(l)?.takeIf { it.isNotBlank() }?.let { chargingType = it }
                }
                (l.contains("charging policy", ignoreCase = true) || l.contains("charge type", ignoreCase = true)) &&
                    l.contains(":") -> {
                    extractAfterColon(l)?.takeIf { it.isNotBlank() }?.let { chargingType = it }
                }
            }
        }
        if (maxV == null) {
            Regex("""(?i)max.*?voltage[^\d]{0,20}(\d{3,5})""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
                maxV = it / 1000.0
            }
        }
        val pct = computePercentFromLevelScale(level, scale)
        if (chargingType == null) {
            chargingType = extractChargingTypeLoose(text)
        }
        return Hints(design, full, maxV, model, tech, cycles, pct, chargingType)
    }

    private fun computePercentFromLevelScale(level: Int?, scale: Int?): Int? {
        if (level == null) return null
        if (scale != null && scale > 0) {
            val p = (level * 100) / scale
            return p.coerceIn(0, 100)
        }
        if (level in 0..100) return level
        return null
    }

    /** 从全文宽松匹配 PD/DCP 等关键字行 */
    private fun extractChargingTypeLoose(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)(USB[_\s-]*PD[\w-]*)"""),
            Regex("""(?i)(USB[_\s-]*DCP)"""),
            Regex("""(?i)(USB[_\s-]*SDP)"""),
            Regex("""(?i)(USB[_\s-]*CDP)"""),
            Regex("""(?i)(Wireless)"""),
        )
        for (re in patterns) {
            re.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length in 3..64 }?.let { return it }
        }
        return null
    }

    private fun extractLongAfterColon(line: String): Long? {
        val idx = line.indexOf(':')
        if (idx < 0) return null
        val tail = line.substring(idx + 1).trim()
        val digits = tail.filter { it.isDigit() }
        return digits.toLongOrNull()
    }

    private fun extractAfterColon(line: String): String? {
        val idx = line.indexOf(':')
        if (idx < 0) return null
        return line.substring(idx + 1).trim().takeIf { it.isNotEmpty() }
    }

    private fun extractAfterEquals(line: String): String? {
        val idx = line.indexOf('=')
        if (idx < 0) return null
        return line.substring(idx + 1).trim().takeIf { it.isNotEmpty() }
    }

    private fun extractMilliOrMicroVolts(line: String): Int? {
        val m = Regex("""(\d{3,6})""").find(line) ?: return null
        val n = m.groupValues[1].toInt()
        return if (n > 100_000) n / 1000 else n // μV -> mV 近似
    }
}

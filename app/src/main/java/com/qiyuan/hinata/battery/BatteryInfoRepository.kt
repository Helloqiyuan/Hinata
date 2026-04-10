package com.qiyuan.hinata.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.qiyuan.hinata.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 聚合 sysfs（需 root）+ dumpsys battery + [BatteryManager] / 粘性广播，均为只读。
 */
class BatteryInfoRepository {

    suspend fun load(context: Context): BatterySnapshot = withContext(Dispatchers.IO) {
        AppLogger.append("---------- 开始读取电池信息 ----------")
        val rootOk = RootShell.checkRootAvailable()
        val sysfsMap = if (rootOk) PowerSupplyScanner.readSupplies(true) else emptyMap()

        val dumpsys = if (rootOk) RootShell.dumpsysBattery() else null
        if (dumpsys != null) {
            AppLogger.append("dumpsys battery 退出码=${dumpsys.exitCode}，长度=${dumpsys.output.length}")
        }

        val hints = DumpsysParser.parse(dumpsys?.output.orEmpty())

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val sticky = registerBatterySticky(context)

        val (designUah, designSrc) = pickDesignCapacity(sysfsMap, hints, bm)
        val (fullUah, fullSrc) = pickFullCharge(sysfsMap, hints, bm)
        val (cycle, cycleSrc) = pickCycleCount(sysfsMap, hints)
        val remainingLife = computeRemainingLifePercent(designUah, fullUah)

        val (tempC, tempSrc) = pickTemperature(sysfsMap, sticky)
        val (voltageV, voltSrc) = pickVoltage(sysfsMap, sticky, bm)
        val (maxVV, maxSrc) = pickMaxVoltage(sysfsMap, hints, sticky)

        val (model, modelSrc) = pickModel(sysfsMap, hints)
        val (tech, _) = PowerSupplyScanner.firstString(sysfsMap, "technology")
        val pct = readBatteryPercent(bm, sticky)

        BatterySnapshot(
            rootGranted = rootOk,
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
            batteryPercent = pct ?: sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 },
        )
    }

    private fun pickDesignCapacity(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
        bm: BatteryManager,
    ): Pair<Long?, String> {
        val (a, s) = PowerSupplyScanner.firstLongUah(map, "charge_full_design")
        if (a != null) return a to s
        hints.chargeFullDesignUah?.let { return it to "dumpsys" }
        return null to "不可用"
    }

    private fun pickFullCharge(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
        bm: BatteryManager,
    ): Pair<Long?, String> {
        val (a, s) = PowerSupplyScanner.firstLongUah(map, "charge_full")
        if (a != null) return a to s
        hints.chargeFullUah?.let { return it to "dumpsys" }
        if (Build.VERSION.SDK_INT >= 31) {
            // API 31+ 常量 BATTERY_PROPERTY_CHARGE_COUNTER_FULL = 6（部分编译环境未生成符号，故使用字面量）
            val v = bm.getLongProperty(6)
            if (v != Long.MIN_VALUE && v > 0) return v to "BatteryManager API 31+"
        }
        return null to "不可用"
    }

    /** 循环次数：优先 sysfs，其次 dumpsys 启发式 */
    private fun pickCycleCount(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
    ): Pair<Int?, String> {
        val (n, s) = PowerSupplyScanner.firstCycleCount(map)
        if (n != null) return n to s
        hints.cycleCount?.let { return it to "dumpsys" }
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
        sticky: Intent?,
    ): Pair<Double?, String> {
        val (tenths, src) = PowerSupplyScanner.firstTempTenths(map)
        if (tenths != null) return (tenths / 10.0) to src
        val raw = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (raw != null && raw != Int.MIN_VALUE) {
            return (raw / 10.0) to "广播 ACTION_BATTERY_CHANGED"
        }
        return null to "不可用"
    }

    private fun pickVoltage(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        sticky: Intent?,
        @Suppress("UNUSED_PARAMETER") bm: BatteryManager,
    ): Pair<Double?, String> {
        val (uv, src) = PowerSupplyScanner.firstMicrovolt(map, "voltage_now")
        if (uv != null) return (uv / 1_000_000.0) to src
        val mvLegacy = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        if (mvLegacy > 0) return (mvLegacy / 1000.0) to "广播 EXTRA_VOLTAGE (mV)"
        return null to "不可用"
    }

    private fun pickMaxVoltage(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
        sticky: Intent?,
    ): Pair<Double?, String> {
        val (uv1, s1) = PowerSupplyScanner.firstMicrovolt(map, "voltage_max")
        if (uv1 != null) return (uv1 / 1_000_000.0) to s1
        val (uv2, s2) = PowerSupplyScanner.firstMicrovolt(map, "voltage_ocv")
        if (uv2 != null) return (uv2 / 1_000_000.0) to s2
        hints.maxVoltageV?.let { return it to "dumpsys" }
        return null to "不可用"
    }

    private fun pickModel(
        map: Map<String, List<PowerSupplyScanner.SysfsRead>>,
        hints: DumpsysParser.Hints,
    ): Pair<String?, String> {
        val (m, s) = PowerSupplyScanner.firstString(map, "model_name")
        if (!m.isNullOrEmpty()) return m to s
        hints.model?.let { return it to "dumpsys" }
        return null to "不可用"
    }

    /** 粘性注册电池广播（只读系统广播），兼容 API 33+ 标志位 */
    private fun registerBatterySticky(context: Context): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(null, filter)
        }
    }

    private fun readBatteryPercent(bm: BatteryManager, sticky: Intent?): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (p in 0..100) return p
        }
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) return (level * 100) / scale
        return null
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
    )

    fun parse(text: String): Hints {
        if (text.isBlank()) return Hints(null, null, null, null, null, null)
        var design: Long? = null
        var full: Long? = null
        var maxV: Double? = null
        var model: String? = null
        var tech: String? = null
        var cycles: Int? = null

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
            }
        }
        // 兜底：常见 “voltage: 4201” 为 mV，非最大电压；仅当未找到 max 电压时尝试数字行
        if (maxV == null) {
            Regex("""(?i)max.*?voltage[^\d]{0,20}(\d{3,5})""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
                maxV = it / 1000.0
            }
        }
        return Hints(design, full, maxV, model, tech, cycles)
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

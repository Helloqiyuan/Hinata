package com.qiyuan.hinata.battery

/**
 * 聚合后的电池读取结果（展示层再格式化单位）。
 */
data class BatterySnapshot(
    val designCapacityUah: Long?,
    val designCapacitySource: String,
    val fullChargeCapacityUah: Long?,
    val fullChargeCapacitySource: String,
    /** 循环次数，优先 sysfs（如 cycle_count） */
    val cycleCount: Int?,
    val cycleCountSource: String,
    /**
     * 剩余寿命估算：满充容量 / 设计容量 × 100%，仅当两者皆有时有效。
     */
    val remainingLifePercent: Double?,
    /** 摄氏温度 */
    val temperatureC: Double?,
    val temperatureSource: String,
    /** 当前电压，单位伏特 */
    val voltageV: Double?,
    val voltageSource: String,
    /** 最大电压，单位伏特；缺失时为 null */
    val maxVoltageV: Double?,
    val maxVoltageSource: String,
    val modelName: String?,
    val modelSource: String,
    val technology: String?,
    val batteryPercent: Int?,
    /** 电量百分比数据来源路径或说明 */
    val batteryPercentSource: String,
    /** 瞬时电流（µA），充电为正、放电为负 */
    val currentMicroA: Long?,
    val currentSource: String,
    /** 功率（W），由电流×电压计算 */
    val powerW: Double?,
    val powerSource: String,
    /** 当前充电协议/充电器类型（内核可见字符串） */
    val chargingProtocol: String?,
    val chargingProtocolSource: String,
)

/**
 * 定时 sysfs 批量读取（无 dumpsys）得到的动态字段，用于覆盖 [BatterySnapshot] 中对应项。
 * 不含设计容量（仅全量读）；剩余寿命用全量读得到的设计容量 + 本次满充计算。
 */
data class BatteryLiveFields(
    val fullChargeCapacityUah: Long?,
    val fullChargeCapacitySource: String,
    val remainingLifePercent: Double?,
    val cycleCount: Int?,
    val cycleCountSource: String,
    val batteryPercent: Int?,
    val batteryPercentSource: String,
    val temperatureC: Double?,
    val temperatureSource: String,
    val voltageV: Double?,
    val voltageSource: String,
    val currentMicroA: Long?,
    val currentSource: String,
    val powerW: Double?,
    val powerSource: String,
    val chargingProtocol: String?,
    val chargingProtocolSource: String,
)

/** 将定时刷新得到的动态字段合并进完整快照（设计容量等仅全量字段保持不变）。 */
fun BatterySnapshot.applyLive(live: BatteryLiveFields): BatterySnapshot = copy(
    fullChargeCapacityUah = live.fullChargeCapacityUah ?: fullChargeCapacityUah,
    fullChargeCapacitySource = if (live.fullChargeCapacityUah != null) {
        live.fullChargeCapacitySource
    } else {
        fullChargeCapacitySource
    },
    remainingLifePercent = live.remainingLifePercent ?: remainingLifePercent,
    cycleCount = live.cycleCount ?: cycleCount,
    cycleCountSource = if (live.cycleCount != null) live.cycleCountSource else cycleCountSource,
    batteryPercent = live.batteryPercent ?: batteryPercent,
    batteryPercentSource = if (live.batteryPercent != null) live.batteryPercentSource else batteryPercentSource,
    temperatureC = live.temperatureC ?: temperatureC,
    temperatureSource = if (live.temperatureC != null) live.temperatureSource else temperatureSource,
    voltageV = live.voltageV ?: voltageV,
    voltageSource = if (live.voltageV != null) live.voltageSource else voltageSource,
    currentMicroA = live.currentMicroA ?: currentMicroA,
    currentSource = if (live.currentMicroA != null) live.currentSource else currentSource,
    powerW = live.powerW ?: powerW,
    powerSource = if (live.powerW != null) live.powerSource else powerSource,
    chargingProtocol = if (!live.chargingProtocol.isNullOrBlank()) {
        live.chargingProtocol
    } else {
        chargingProtocol
    },
    chargingProtocolSource = if (!live.chargingProtocol.isNullOrBlank()) {
        live.chargingProtocolSource
    } else {
        chargingProtocolSource
    },
)

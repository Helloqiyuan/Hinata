package com.qiyuan.hinata.battery

/**
 * 聚合后的电池读取结果（展示层再格式化单位）。
 */
data class BatterySnapshot(
    val rootGranted: Boolean,
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
    /** 摄氏温度，可能来自 sysfs 或广播 */
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
)

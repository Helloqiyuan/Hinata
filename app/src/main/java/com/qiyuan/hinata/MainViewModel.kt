package com.qiyuan.hinata

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiyuan.hinata.battery.BatteryInfoRepository
import com.qiyuan.hinata.battery.BatterySnapshot
import com.qiyuan.hinata.battery.applyLive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 在后台线程拉取电池信息，主线程更新 [uiState]；与 [AppLogger] 共享日志。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BatteryInfoRepository()

    private val _uiState = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _uiState.asStateFlow()

    /** 最近一次完整快照（含静态字段），供定时刷新在其上覆盖动态字段 */
    private var lastSnapshot: BatterySnapshot? = null

    /** 电量/电流/功率/温度/电压/协议 的定时刷新协程 */
    private var liveUpdateJob: Job? = null

    init {
        refresh()
    }

    override fun onCleared() {
        liveUpdateJob?.cancel()
        super.onCleared()
    }

    /** 重新读取电池数据（只读） */
    fun refresh() {
        liveUpdateJob?.cancel()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    repository.load()
                }
                lastSnapshot = snapshot
                _uiState.value = BatteryUiState(
                    isLoading = false,
                    rows = snapshot.toRows(markDynamicFooters = false),
                    error = null,
                )
                startPeriodicLiveUpdates()
            } catch (e: Exception) {
                AppLogger.append("读取失败: ${e.message}")
                lastSnapshot = null
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "未知错误",
                )
            }
        }
    }

    /**
     * 每 [LIVE_UPDATE_INTERVAL_MS] 仅批量读取 sysfs 更新动态行，不跑 dumpsys。
     */
    private fun startPeriodicLiveUpdates() {
        liveUpdateJob?.cancel()
        liveUpdateJob = viewModelScope.launch {
            while (isActive) {
                val base = lastSnapshot ?: break
                val live = try {
                    withContext(Dispatchers.IO) {
                        repository.loadLiveFields(designCapacityUahHint = base.designCapacityUah)
                    }
                } catch (_: Exception) {
                    delay(LIVE_UPDATE_INTERVAL_MS)
                    continue
                }
                val merged = base.applyLive(live)
                lastSnapshot = merged
                // 仅当行内容相对上次有变化时才发射，避免同值重复 submitList 导致整表闪烁
                if (_uiState.value.error == null) {
                    val newRows = merged.toRows(markDynamicFooters = true)
                    if (newRows != _uiState.value.rows) {
                        _uiState.value = BatteryUiState(
                            isLoading = false,
                            rows = newRows,
                            error = null,
                        )
                    }
                }
                delay(LIVE_UPDATE_INTERVAL_MS)
            }
        }
    }

    private companion object {
        /** 动态字段自动刷新间隔 */
        const val LIVE_UPDATE_INTERVAL_MS = 1000L
    }

    data class BatteryUiState(
        val isLoading: Boolean = false,
        val rows: List<DataRow> = emptyList(),
        val error: String? = null,
    )

    /**
     * [value] 为主要展示的大号加粗内容；[footer] 为路径或说明（可空，「>」前缀表示动态更新卡片）。
     * [cardTintIndex] 轮换卡片底；[valueAccentColorRes] 预留主数值强调色（默认与其它项一致）。
     */
    data class DataRow(
        val title: String,
        val value: String,
        val footer: String? = null,
        val cardTintIndex: Int = 0,
        val valueAccentColorRes: Int? = null,
    )
}

/**
 * 将快照格式化为界面行。
 * @param markDynamicFooters 为 true 时，参与定时刷新的卡片在脚注前加「>」（不表示路径，仅表示动态更新）。
 */
private fun BatterySnapshot.toRows(markDynamicFooters: Boolean): List<MainViewModel.DataRow> {
    val locale = Locale.getDefault()
    var tintSeq = 0
    fun nextTint(): Int = (tintSeq++ % 6)

    /** µAh → mAh，主数值仅显示整数 */
    fun uahToMAhInteger(uah: Long?): String =
        if (uah != null) String.format(locale, "%d mAh", uah / 1000) else "—"

    /** 脚注文案；[dynamicCard] 为 true 且已同步过定时数据时加「>」前缀 */
    fun foot(text: String, dynamicCard: Boolean): String =
        if (markDynamicFooters && dynamicCard) "> $text" else text

    return buildList {
        add(
            MainViewModel.DataRow(
                title = "设计容量",
                value = uahToMAhInteger(designCapacityUah),
                footer = foot(designCapacitySource.ifEmpty { "不可用" }, dynamicCard = false),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "当前满充容量",
                value = uahToMAhInteger(fullChargeCapacityUah),
                footer = foot(fullChargeCapacitySource.ifEmpty { "不可用" }, dynamicCard = true),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "剩余寿命估算",
                value = if (remainingLifePercent != null) {
                    String.format(locale, "%.1f %%", remainingLifePercent)
                } else {
                    "—"
                },
                // 剩余寿命为计算值：脚注只写算式
                footer = foot(
                    if (remainingLifePercent != null) {
                        "剩余寿命(%) = 满充容量 ÷ 设计容量 × 100"
                    } else {
                        "需同时有效的设计容量与满充容量"
                    },
                    dynamicCard = true,
                ),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "循环次数",
                value = if (cycleCount != null) cycleCount.toString() else "—",
                footer = foot(cycleCountSource.ifEmpty { "不可用" }, dynamicCard = true),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "充电协议",
                value = if (chargingProtocol.isNullOrBlank()) "—" else chargingProtocol.trim(),
                footer = foot(chargingProtocolSource.ifEmpty { "不可用" }, dynamicCard = true),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "电量百分比",
                value = if (batteryPercent != null) "$batteryPercent%" else "—",
                footer = foot(batteryPercentSource.ifEmpty { "不可用" }, dynamicCard = true),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "电流",
                value = if (currentMicroA != null) {
                    String.format(locale, "%+.1f mA", currentMicroA / 1000.0)
                } else {
                    "—"
                },
                footer = foot(currentSource.ifEmpty { "不可用" }, dynamicCard = true),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "功率",
                value = if (powerW != null) String.format(locale, "%+.3f W", powerW) else "—",
                footer = foot(powerSource.ifEmpty { "不可用" }, dynamicCard = true),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "温度",
                value = if (temperatureC != null) String.format(locale, "%.1f ℃", temperatureC) else "—",
                footer = foot(temperatureSource.ifEmpty { "不可用" }, dynamicCard = true),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "当前电压",
                value = if (voltageV != null) String.format(locale, "%.3f V", voltageV) else "—",
                footer = foot(voltageSource.ifEmpty { "不可用" }, dynamicCard = true),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "最大电压",
                value = if (maxVoltageV != null) String.format(locale, "%.3f V", maxVoltageV) else "—",
                footer = foot(maxVoltageSource.ifEmpty { "不可用" }, dynamicCard = false),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "电池型号",
                value = if (modelName.isNullOrBlank()) "—" else modelName.trim(),
                footer = foot(modelSource.ifEmpty { "不可用" }, dynamicCard = false),
                cardTintIndex = nextTint(),
            ),
        )
        add(
            MainViewModel.DataRow(
                title = "技术类型",
                value = technology ?: "—",
                footer = null,
                cardTintIndex = nextTint(),
            ),
        )
    }
}

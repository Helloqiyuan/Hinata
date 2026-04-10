package com.qiyuan.hinata

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiyuan.hinata.battery.BatteryInfoRepository
import com.qiyuan.hinata.battery.BatterySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
        refresh()
    }

    /** 重新读取电池数据（只读） */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    repository.load(getApplication())
                }
                _uiState.value = BatteryUiState(
                    isLoading = false,
                    rows = snapshot.toRows(),
                    error = null,
                )
            } catch (e: Exception) {
                AppLogger.append("读取失败: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "未知错误",
                )
            }
        }
    }

    data class BatteryUiState(
        val isLoading: Boolean = false,
        val rows: List<DataRow> = emptyList(),
        val error: String? = null,
    )

    /**
     * [value] 为主要展示的大号加粗内容；[footer] 为来源、原始数值等次要说明（可空）。
     */
    data class DataRow(
        val title: String,
        val value: String,
        val footer: String? = null,
    )
}

/** 将快照格式化为界面行（主数值加大加粗，来源等放入 footer） */
private fun BatterySnapshot.toRows(): List<MainViewModel.DataRow> {
    val locale = Locale.getDefault()
    fun uahToMAhNumber(uah: Long?): String =
        if (uah != null) String.format(locale, "%.1f mAh", uah / 1000.0) else "—"

    fun uahFooter(uah: Long?, source: String): String? =
        if (uah != null) "原始 ${uah} µAh · 来源 $source" else "来源 $source"

    return listOf(
        MainViewModel.DataRow(
            title = "Root 授权",
            value = if (rootGranted) "已授予" else "未授予",
            // 已授予 root 时不显示管理器类脚注；未授予时保留降级说明
            footer = if (rootGranted) null else "将尽量使用系统 API，容量类可能缺失",
        ),
        MainViewModel.DataRow(
            title = "设计容量",
            value = uahToMAhNumber(designCapacityUah),
            footer = uahFooter(designCapacityUah, designCapacitySource),
        ),
        MainViewModel.DataRow(
            title = "当前满充容量",
            value = uahToMAhNumber(fullChargeCapacityUah),
            footer = uahFooter(fullChargeCapacityUah, fullChargeCapacitySource),
        ),
        MainViewModel.DataRow(
            title = "剩余寿命估算",
            value = if (remainingLifePercent != null) {
                String.format(locale, "%.1f %%", remainingLifePercent)
            } else {
                "—"
            },
            footer = if (remainingLifePercent != null) {
                "满充容量 ÷ 设计容量 · 仅作参考"
            } else {
                "需同时读到设计容量与满充容量"
            },
        ),
        MainViewModel.DataRow(
            title = "循环次数",
            value = if (cycleCount != null) cycleCount.toString() else "—",
            footer = if (cycleCount != null) "来源 $cycleCountSource" else cycleCountSource,
        ),
        MainViewModel.DataRow(
            title = "电量百分比",
            value = if (batteryPercent != null) "$batteryPercent%" else "—",
            footer = null,
        ),
        MainViewModel.DataRow(
            title = "温度",
            value = if (temperatureC != null) String.format(locale, "%.1f ℃", temperatureC) else "—",
            footer = if (temperatureC != null) "来源 $temperatureSource" else temperatureSource,
        ),
        MainViewModel.DataRow(
            title = "当前电压",
            value = if (voltageV != null) String.format(locale, "%.3f V", voltageV) else "—",
            footer = if (voltageV != null) "来源 $voltageSource" else voltageSource,
        ),
        MainViewModel.DataRow(
            title = "最大电压",
            value = if (maxVoltageV != null) String.format(locale, "%.3f V", maxVoltageV) else "—",
            footer = if (maxVoltageV != null) "来源 $maxVoltageSource" else maxVoltageSource,
        ),
        MainViewModel.DataRow(
            title = "电池型号",
            value = if (modelName.isNullOrBlank()) "—" else modelName.trim(),
            footer = if (!modelName.isNullOrBlank()) "来源 $modelSource" else modelSource,
        ),
        MainViewModel.DataRow(
            title = "技术类型",
            value = technology ?: "—",
            footer = null,
        ),
    )
}

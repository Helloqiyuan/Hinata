package com.qiyuan.hinata

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 内存环形日志：供「日志」Tab 展示，不写系统、不写文件。
 */
object AppLogger {

    private const val MAX_LINES = 300

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val buffer = ArrayDeque<String>(MAX_LINES + 1)

    private val _lines = MutableStateFlow<List<String>>(emptyList())

    /** 当前日志行列表（只读） */
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    /** 追加一行日志（带时间戳） */
    @Synchronized
    fun append(message: String) {
        val line = "[${timeFormat.format(Date())}] $message"
        buffer.addLast(line)
        while (buffer.size > MAX_LINES) {
            buffer.removeFirst()
        }
        _lines.value = buffer.toList()
    }

    /** 仅清空内存中的日志展示缓冲 */
    @Synchronized
    fun clear() {
        buffer.clear()
        _lines.value = emptyList()
    }
}

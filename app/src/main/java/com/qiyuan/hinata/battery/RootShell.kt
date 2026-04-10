package com.qiyuan.hinata.battery

import com.qiyuan.hinata.AppLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * 通过 `su -c` 执行只读命令（如 cat、dumpsys、ls），不做任何写操作。
 */
object RootShell {

    private const val DEFAULT_TIMEOUT_MS = 20_000L

    data class ShellResult(
        val exitCode: Int,
        val output: String,
    )

    /**
     * 验证 sysfs 等路径：仅允许字母数字、下划线、点、横线、斜杠，且必须位于 power_supply 下。
     */
    fun isSafePowerSupplyPath(path: String): Boolean {
        if (!path.startsWith("/sys/class/power_supply/")) return false
        if (path.length > 512) return false
        return path.all { it.isLetterOrDigit() || it == '/' || it == '_' || it == '.' || it == '-' }
    }

    /**
     * 执行 `su -c "<singleCommand>"`，带超时与输出合并。
     */
    fun runSuCommand(singleCommand: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult {
        AppLogger.append("执行: su -c \"$singleCommand\"")
        val pb = ProcessBuilder(listOf("su", "-c", singleCommand))
        pb.redirectErrorStream(true)
        return runProcess(pb, timeoutMs)
    }

    /** 测试 root 是否可用（只读 `id`） */
    fun checkRootAvailable(): Boolean {
        val r = runSuCommand("id", timeoutMs = 8_000L)
        val ok = r.exitCode == 0 && r.output.contains("uid=0", ignoreCase = true)
        AppLogger.append(if (ok) "Root 可用" else "Root 不可用或拒绝授权 (exit=${r.exitCode})")
        return ok
    }

    /** 使用已校验路径读取文件：`cat <path>` */
    fun catSafePath(path: String): ShellResult {
        require(isSafePowerSupplyPath(path)) { "拒绝不安全路径: $path" }
        return runSuCommand("cat $path")
    }

    /** 列出目录：`ls <dir>`（dir 固定为白名单路径） */
    fun lsSafeDir(dir: String): ShellResult {
        require(dir == "/sys/class/power_supply") { "仅允许列出 /sys/class/power_supply" }
        return runSuCommand("ls $dir")
    }

    /** 只读 dumpsys battery */
    fun dumpsysBattery(): ShellResult {
        return runSuCommand("dumpsys battery")
    }

    private fun runProcess(pb: ProcessBuilder, timeoutMs: Long): ShellResult {
        return try {
            val process = pb.start()
            val out = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    out.append(line).append('\n')
                }
            }
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                AppLogger.append("命令超时 (${timeoutMs}ms)，已终止进程")
                ShellResult(-1, out.toString())
            } else {
                ShellResult(process.exitValue(), out.toString().trimEnd())
            }
        } catch (e: Exception) {
            AppLogger.append("执行异常: ${e.message}")
            ShellResult(-1, e.message ?: "")
        }
    }
}

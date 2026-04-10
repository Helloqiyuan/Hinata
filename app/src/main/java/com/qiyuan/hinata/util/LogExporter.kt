package com.qiyuan.hinata.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 将日志文本写入公共「下载/AHinata/」目录（MediaStore，无需整卡读写权限）。
 */
object LogExporter {

    private const val SUB_DIR = "AHinata"

    sealed class Outcome {
        data class Success(
            val displayName: String,
            /** 供用户理解的相对路径说明 */
            val relativePathHint: String,
            /** 常见完整路径（与文件管理器「内部存储」一致，依厂商可能略有差异） */
            val absolutePath: String,
            val contentUri: Uri,
        ) : Outcome()

        data class Failure(val message: String) : Outcome()
    }

    /**
     * 文件名：`Hinata yyyy-MM-dd HH-mm-ss.txt`（时间与日期之间为空格；时间用 `-` 代替 `:`，避免部分文件系统不允许冒号）
     */
    fun exportUtf8Log(context: Context, text: String): Outcome {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Outcome.Failure("需要 Android 10 及以上以写入下载目录")
        }
        return exportApi29(context, text)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportApi29(context: Context, text: String): Outcome {
        // 形如：Hinata 2026-04-10 14-30-00.txt（冒号改为 -，与常见文件系统兼容）
        val sdf = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault())
        val fileName = "Hinata ${sdf.format(Date())}.txt"
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$SUB_DIR"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            return Outcome.Failure(e.message ?: "无法创建文件")
        } ?: return Outcome.Failure("无法创建文件")

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val absolutePath = File(downloadsDir, "$SUB_DIR/$fileName").absolutePath

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } ?: return Outcome.Failure("无法写入文件")

            val pendingOff = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, pendingOff, null, null)

            val hint = "Download/$SUB_DIR/$fileName"
            Outcome.Success(
                displayName = fileName,
                relativePathHint = hint,
                absolutePath = absolutePath,
                contentUri = uri,
            )
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) { }
            Outcome.Failure(e.message ?: "写入失败")
        }
    }
}

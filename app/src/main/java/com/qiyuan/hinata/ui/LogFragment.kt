package com.qiyuan.hinata.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.qiyuan.hinata.AppLogger
import com.qiyuan.hinata.R
import com.qiyuan.hinata.databinding.FragmentLogBinding
import com.qiyuan.hinata.util.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「日志」Tab：展示内存日志，可清空或导出到「下载/AHinata/」并分享/复制路径。
 */
class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonClear.setOnClickListener {
            AppLogger.clear()
            Toast.makeText(requireContext(), R.string.toast_log_cleared, Toast.LENGTH_SHORT).show()
        }
        binding.buttonExport.setOnClickListener { exportLog() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppLogger.lines.collect { lines ->
                    binding.textLog.text = lines.joinToString(separator = "\n")
                    // 无内容时不允许导出（与 getSnapshotText 一致：无行即视为空）
                    binding.buttonExport.isEnabled = lines.isNotEmpty()
                }
            }
        }
    }

    // 将当前内存日志写入「下载/AHinata」目录下带时间戳的 .txt 文件，并提示分享或复制路径
    private fun exportLog() {
        val text = AppLogger.getSnapshotText()
        // 双重校验：按钮已禁用仍被调用时直接提示（如将来改成交互方式）
        if (text.isBlank()) {
            Toast.makeText(requireContext(), R.string.toast_log_export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                LogExporter.exportUtf8Log(requireContext().applicationContext, text)
            }
            when (outcome) {
                is LogExporter.Outcome.Success -> showExportSuccessDialog(outcome)
                is LogExporter.Outcome.Failure -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.log_export_fail_title)
                        .setMessage(outcome.message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun showExportSuccessDialog(s: LogExporter.Outcome.Success) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.log_export_ok_title)
            .setMessage(
                getString(
                    R.string.log_export_ok_message,
                    s.relativePathHint,
                    s.absolutePath,
                ),
            )
            .setPositiveButton(R.string.log_export_share) { _, _ ->
                shareLogFile(s.contentUri)
            }
            .setNegativeButton(R.string.log_export_view_path) { _, _ ->
                copyPathToClipboard(s.absolutePath)
            }
            .setNeutralButton(android.R.string.ok, null)
            .show()
    }

    private fun shareLogFile(uri: android.net.Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(requireContext().contentResolver, "log", uri)
        }
        startActivity(
            Intent.createChooser(intent, getString(R.string.log_export_share)),
        )
    }

    private fun copyPathToClipboard(path: String) {
        val cm = requireContext().getSystemService<ClipboardManager>()!!
        cm.setPrimaryClip(ClipData.newPlainText("log_path", path))
        Toast.makeText(requireContext(), R.string.log_export_copy_ok, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

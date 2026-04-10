package com.qiyuan.hinata

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.qiyuan.hinata.databinding.ActivityMainBinding
import com.qiyuan.hinata.ui.MainPagerAdapter

/**
 * 主界面：关于入口 + 底部 TabLayout + ViewPager2（数据 / 日志）；下拉刷新在数据页。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边绘制后由根布局应用系统栏内边距，避免标题栏与状态栏重叠
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootMain) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // 状态栏/导航栏图标对比度随深浅色切换（与 M3 表面工具栏一致）
        val night =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setTitle(R.string.app_name)

        val pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_data)
                1 -> getString(R.string.tab_log)
                else -> ""
            }
        }.attach()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** 展示版本与构建期注入的 Git 仓库链接（可点击打开浏览器） */
    private fun showAboutDialog() {
        val url = BuildConfig.GIT_REPO_URL
        // 关于对话框仅展示 versionName（文案见 about_version_line）
        val versionLine = getString(R.string.about_version_line, BuildConfig.VERSION_NAME)
        val hint = getString(R.string.about_repo_hint)
        val body = "$versionLine\n\n$hint\n$url"
        val spannable = SpannableString(body)
        val urlStart = body.indexOf(url)
        if (urlStart >= 0) {
            spannable.setSpan(
                URLSpan(url),
                urlStart,
                urlStart + url.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_dialog_title)
            .setMessage(spannable)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.about_open_in_browser) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .show()
        (dialog.findViewById(android.R.id.message) as? TextView)?.movementMethod =
            LinkMovementMethod.getInstance()
    }
}

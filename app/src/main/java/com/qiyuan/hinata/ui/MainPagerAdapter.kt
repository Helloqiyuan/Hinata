package com.qiyuan.hinata.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * 主界面 ViewPager2：数据 / 日志。
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> DataFragment()
        1 -> LogFragment()
        else -> throw IllegalArgumentException("position=$position")
    }
}

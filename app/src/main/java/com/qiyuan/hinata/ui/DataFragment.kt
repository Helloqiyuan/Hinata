package com.qiyuan.hinata.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.qiyuan.hinata.MainViewModel
import com.qiyuan.hinata.R
import com.qiyuan.hinata.databinding.FragmentDataBinding
import kotlinx.coroutines.launch

/**
 * 「数据」Tab：展示只读读取结果，支持下拉刷新与工具栏刷新联动。
 */
class DataFragment : Fragment() {

    private var _binding: FragmentDataBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private val adapter = DataRowsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerData.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerData.adapter = adapter
        // 下拉刷新指示器与主题主色一致
        binding.swipeRefresh.setColorSchemeResources(R.color.hinata_primary)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    adapter.submitList(state.rows)
                    val hasErr = !state.error.isNullOrBlank()
                    binding.cardError.visibility = if (hasErr) View.VISIBLE else View.GONE
                    binding.textError.text = state.error.orEmpty()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

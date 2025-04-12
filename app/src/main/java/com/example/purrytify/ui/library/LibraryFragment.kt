package com.example.purrytify.ui.library

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.purrytify.databinding.FragmentLibraryBinding
import com.google.android.material.tabs.TabLayoutMediator
import android.widget.TextView
import com.example.purrytify.R
import com.example.purrytify.ui.addsong.AddSongDialogFragment

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private var currentSearchQuery: String = ""
    private var allSongsFragment: AllSongsFragment? = null
    private var likedSongsFragment: LikedSongsFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupSearchListener()

        binding.buttonFavorite.setOnClickListener {
            AddSongDialogFragment().show(childFragmentManager, "add_song_dialog")
        }
    }

    private fun setupSearchListener() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s.toString().trim()
                updateSearchResults()
            }
        })
    }

    private fun updateSearchResults() {
        allSongsFragment?.updateSearch(currentSearchQuery)
        likedSongsFragment?.updateSearch(currentSearchQuery)
    }

    private fun setupViewPager() {
        val adapter = LibraryPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            // Get the custom view
            val customView = LayoutInflater.from(requireContext())
                .inflate(R.layout.custom_tab_item, null) as TextView

            // Set the text
            customView.text = when (position) {
                0 -> "All"
                1 -> "Liked"
                else -> ""
            }

            // Set the view to the tab
            tab.customView = customView
        }.attach()

        binding.tabLayout.post {
            val allTab = binding.tabLayout.getTabAt(0)?.view

            val layoutParams = allTab?.layoutParams as? ViewGroup.MarginLayoutParams
            layoutParams?.marginEnd = 30
            allTab?.layoutParams = layoutParams
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class LibraryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> {
                    allSongsFragment = AllSongsFragment().apply { 
                        if (currentSearchQuery.isNotEmpty()) {
                            this.updateSearch(currentSearchQuery)
                        }
                    }
                    allSongsFragment!!
                }
                1 -> {
                    likedSongsFragment = LikedSongsFragment().apply {
                        if (currentSearchQuery.isNotEmpty()) {
                            this.updateSearch(currentSearchQuery)
                        }
                    }
                    likedSongsFragment!!
                }
                else -> throw IllegalArgumentException("Invalid position")
            }
        }
    }
}
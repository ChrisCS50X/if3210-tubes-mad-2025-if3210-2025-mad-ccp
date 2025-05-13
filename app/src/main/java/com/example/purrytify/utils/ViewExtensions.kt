package com.example.purrytify.utils

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.findFragment

/**
 * Finds the parent Fragment for a View
 */
fun <T : Fragment> View.findFragment(): T {
    try {
        @Suppress("UNCHECKED_CAST")
        return findFragment<Fragment>() as T
    } catch (e: Exception) {
        throw IllegalStateException("View $this is not attached to a fragment", e)
    }
}
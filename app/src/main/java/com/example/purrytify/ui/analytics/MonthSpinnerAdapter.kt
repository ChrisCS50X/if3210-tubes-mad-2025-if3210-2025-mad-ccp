package com.example.purrytify.ui.analytics

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.example.purrytify.R

class MonthSpinnerAdapter(
    context: Context,
    private val months: List<MonthYear>
) : ArrayAdapter<MonthYear>(context, R.layout.item_month_spinner, months) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getCustomView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getCustomView(position, convertView, parent)
    }

    private fun getCustomView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_month_spinner, parent, false)
        
        val monthYear = getItem(position)
        val textView = view.findViewById<TextView>(R.id.tvMonthYear)
        textView.text = monthYear.toString()
        
        return view
    }
}

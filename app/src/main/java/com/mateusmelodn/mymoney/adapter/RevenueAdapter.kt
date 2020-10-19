package com.mateusmelodn.mymoney.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.mateusmelodn.mymoney.R
import com.mateusmelodn.mymoney.model.Revenue
import com.mateusmelodn.mymoney.databinding.RevenueCardBinding
import java.text.SimpleDateFormat
import java.util.*

open class RevenueAdapter(query: Query, private val listener: OnRevenueSelectedListener?) : FirestoreAdapter<RevenueAdapter.ViewHolder>(query) {
    // Interface for handling item selection (click)
    interface OnRevenueSelectedListener {
        fun onRevenueSelected(revenue: Revenue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(RevenueCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getSnapshot(position).toObject<Revenue>(), listener)
    }

    class ViewHolder(val binding: RevenueCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(revenue: Revenue?, listener: OnRevenueSelectedListener?) {
            if (revenue == null) {
                return
            }

            val context = binding.root.context
            val valueFormatted = "%.2f".format(revenue.value)

            binding.revenueDescriptionTextView.text = revenue.description
            binding.valueTextView.text = context.getString(R.string.value_formatted, valueFormatted)
            binding.paidTextView.text = context.getString(if (revenue.paid) R.string.paid else R.string.pending)

            if (revenue.dateTime != null) {
                binding.dateTextView.text = FORMAT.format(revenue.dateTime!!)
            }

            // Click listener
            binding.root.setOnClickListener {
                listener?.onRevenueSelected(revenue)
            }
        }
    }

    companion object {
        private val FORMAT = SimpleDateFormat(
                "MM/dd/yyyy", Locale.US)
    }
}
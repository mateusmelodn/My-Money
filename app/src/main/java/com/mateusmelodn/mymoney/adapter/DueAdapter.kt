package com.mateusmelodn.mymoney.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.mateusmelodn.mymoney.model.Due
import com.mateusmelodn.mymoney.databinding.DueCardBinding
import java.text.SimpleDateFormat
import java.util.*

open class DueAdapter(query: Query, private val listener: OnDueSelectedListener?) : FirestoreAdapter<DueAdapter.ViewHolder>(query) {
    interface OnDueSelectedListener {
        fun onDueSelected(due: Due)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(DueCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getSnapshot(position).toObject<Due>(), listener)
    }

    class ViewHolder(val binding: DueCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(due: Due?, listener: OnDueSelectedListener?) {
            if (due == null) {
                return
            }

            val valueFormatted = "%.2f".format(due.value)
            binding.dueDescriptionTextView.text = due.description
            binding.valueTextView.text = "R$ $valueFormatted"
            binding.paidTextView.text = if (due.paid) "Pago" else "Pendente"

            if (due.dateTime != null) {
                binding.dateTextView.text = FORMAT.format(due.dateTime!!)
            }

            // Click listener
            binding.root.setOnClickListener {
                listener?.onDueSelected(due)
            }
        }
    }

    companion object {
        private val FORMAT = SimpleDateFormat(
            "MM/dd/yyyy", Locale.US)
    }
}
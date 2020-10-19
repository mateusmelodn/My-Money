package com.mateusmelodn.mymoney.model

import android.content.Context
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.ServerTimestamp
import com.mateusmelodn.mymoney.R
import java.util.*

data class Due(
    var id: String = "", // id will be set at due creation
    var value: Double = 0.0,
    var description: String = "",
    @ServerTimestamp var dateTime: Date? = null,
    var paid: Boolean = false,
) {
    companion object {
        public fun areDueFieldsValid(value: TextInputEditText, description: TextInputEditText, context: Context): Boolean {
            return if (value.text.toString().isEmpty()) {
                value.error = context.getString(R.string.field_required)
                false
            } else if (description.text.toString().isEmpty()) {
                description.error = context.getString(R.string.field_required)
                false
            } else {
                true
            }
        }
    }
}
package com.mateusmelodn.mymoney.activity

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.REVENUE_COLLECTION
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.REVENUE_COLLECTION_ORDER_BY
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.REVENUE_FIELD_DESCRIPTION
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.REVENUE_FIELD_PAID
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.REVENUE_FIELD_VALUE
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.LIMIT_ITEM_PER_QUERY
import com.mateusmelodn.mymoney.R
import com.mateusmelodn.mymoney.adapter.RevenueAdapter
import com.mateusmelodn.mymoney.databinding.ActivityRevenueBinding
import com.mateusmelodn.mymoney.model.Revenue
import kotlinx.android.synthetic.main.dialog_add_delete_update_revenue.view.*
import java.util.*

// Revenue activity for revenue CRUD operations
class RevenueActivity : BaseActivity(), View.OnClickListener, RevenueAdapter.OnRevenueSelectedListener {
    // Reference for views
    private lateinit var binding: ActivityRevenueBinding
    // Reference for Firestore
    private lateinit var firestore: FirebaseFirestore
    // Reference for revenue apadter
    private lateinit var revenueAdapter: RevenueAdapter

    companion object {
        private const val TAG = "RevenueActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRevenueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set revenueFab listener
        binding.addRevenueFab.setOnClickListener(this)

        // Initialize Firestore
        firestore = Firebase.firestore

        // Query for getting revenues
        val revenuesQuery = firestore
                .collection(REVENUE_COLLECTION)
                .orderBy(REVENUE_COLLECTION_ORDER_BY, Query.Direction.DESCENDING)
                .limit(LIMIT_ITEM_PER_QUERY)

        // Create revenue adapter
        revenueAdapter = object : RevenueAdapter(revenuesQuery, this@RevenueActivity) {
            override fun onDataChanged() {
                if (itemCount == 0) {
                    // User must know when there's no revenue for best practice
                    binding.revenueInfoTextView.visibility = View.VISIBLE
                    binding.revenuesRecyclerView.visibility = View.GONE
                } else {
                    // Displey revenues
                    binding.revenueInfoTextView.visibility = View.GONE
                    binding.revenuesRecyclerView.visibility = View.VISIBLE
                }
            }
        }
        // Set adapter to revenuesRecyclerView
        binding.revenuesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.revenuesRecyclerView.adapter = revenueAdapter
    }

    override fun onStart() {
        super.onStart()
        revenueAdapter.startListening()
    }

    override fun onStop() {
        super.onStop()
        revenueAdapter.stopListening()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_from_left, R.anim.slide_out_to_right)
    }

    private fun onAddRevenueClick(revenue: Revenue) {
        addRevenue(revenue)
                .addOnSuccessListener(this) {
                    Log.d(TAG, "Revenue added")

                    // Hide keyboard and scroll to top
                    hideKeyboard()
                    binding.revenuesRecyclerView.smoothScrollToPosition(0)
                }
                .addOnFailureListener(this) { e ->
                    Log.w(TAG, "Add revenue failed", e)

                    // Show failure message and hide keyboard
                    hideKeyboard()
                    Snackbar.make(findViewById(android.R.id.content), R.string.failed_to_add,
                            Snackbar.LENGTH_SHORT).show()
                }
    }

    private fun addRevenue(revenue: Revenue): Task<Void> {
        // Get revenues collection reference
        val revenueRef = firestore.collection(REVENUE_COLLECTION).document()

        // Save id for future uses
        revenue.id = revenueRef.id

        return firestore.runTransaction { transaction ->
            // Commit to Firestore
            transaction.set(revenueRef, revenue)

            null
        }
    }

    private fun onDeleteRevenueClick(revenue: Revenue) {
        deleteRevenue(revenue)
                .addOnSuccessListener(this) {
                    Log.d(TAG, "Revenue deleted")

                    // Hide keyboard and scroll to top
                    hideKeyboard()
                    binding.revenuesRecyclerView.smoothScrollToPosition(0)
                }
                .addOnFailureListener(this) { e ->
                    Log.w(TAG, "Delete revenue failed", e)

                    // Show failure message and hide keyboard
                    hideKeyboard()
                    Snackbar.make(findViewById(android.R.id.content), R.string.failed_to_delete,
                            Snackbar.LENGTH_SHORT).show()
                }
    }

    private fun deleteRevenue(revenue: Revenue): Task<Void> {
        // Get revenue document reference based on id which was previously saved
        val revenueRef = firestore.collection(REVENUE_COLLECTION).document(revenue.id)

        return firestore.runTransaction { transaction ->
            // Commit to Firestore
            transaction.delete(revenueRef)

            null
        }
    }

    private fun onUpdateRevenueClick(revenue: Revenue) {
        updateRevenue(revenue)
                .addOnSuccessListener(this) {
                    Log.d(TAG, "Revenue updated")

                    // Hide keyboard and scroll to top
                    hideKeyboard()
                    binding.revenuesRecyclerView.smoothScrollToPosition(0)
                }
                .addOnFailureListener(this) { e ->
                    Log.w(TAG, "Update revenue failed", e)

                    // Show failure message and hide keyboard
                    hideKeyboard()
                    Snackbar.make(findViewById(android.R.id.content), R.string.failed_to_update,
                            Snackbar.LENGTH_SHORT).show()
                }
    }

    private fun updateRevenue(revenue: Revenue): Task<Void> {
        // Get revenue document reference based on id which was previously saved
        val revenueRef = firestore.collection(REVENUE_COLLECTION).document(revenue.id)

        return firestore.runTransaction { transaction ->
            // Commit to Firestore
            transaction.update(revenueRef,
                    REVENUE_FIELD_VALUE, revenue.value,
                    REVENUE_FIELD_DESCRIPTION, revenue.description,
                    REVENUE_FIELD_PAID, revenue.paid
            )

            null
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.addRevenueFab -> showAddDeleteUpdateRevenueDialog(null)
        }
    }

    var addDeleteUpdateRevenueDialog: AlertDialog? = null
    private fun showAddDeleteUpdateRevenueDialog(revenue: Revenue?) {
        // When revenue is null, it means that is an addition operation
        // Otherwise is an update or deletion

        if (addDeleteUpdateRevenueDialog == null) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            val root: View = layoutInflater.inflate(R.layout.dialog_add_delete_update_revenue, null)
            builder.setView(root)

            addDeleteUpdateRevenueDialog = builder.create()
            addDeleteUpdateRevenueDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Dialog won't be cancelable at all
            addDeleteUpdateRevenueDialog?.setCancelable(false)
            addDeleteUpdateRevenueDialog?.setCanceledOnTouchOutside(false)

            if (revenue != null) {
                // revenue is not null, it's an update or deletion!
                root.cancelButton.visibility = View.GONE
                root.deleteButton.visibility = View.VISIBLE
                root.addRevenueButton.visibility = View.GONE
                root.updateRevenueButton.visibility = View.VISIBLE

                root.dialogRevenueTitle.setText(R.string.delete_update_revenue)
                root.descriptionTextInputField.setText(revenue.description)
                root.valueTextInputField.setText(revenue.value.toString())
                root.paidCheckBox.isChecked = revenue.paid
            }

            root.cancelButton.setOnClickListener {
                dismissAddDeleteUpdateRevenueDialog()
            }

            root.deleteButton.setOnClickListener {
                onDeleteRevenueClick(revenue!!)
                dismissAddDeleteUpdateRevenueDialog()
            }

            root.addRevenueButton.setOnClickListener {
                // Verify if fields are valid
                if (Revenue.areRevenueFieldsValid(root.descriptionTextInputField, root.valueTextInputField, this)) {
                    val description = root.descriptionTextInputField.text.toString()
                    val value = root.valueTextInputField.text.toString()
                    val paid = root.paidCheckBox.isChecked
                    val newRevenue = Revenue("", value.toDouble(), description, Date(), paid)
                    onAddRevenueClick(newRevenue)

                    // Dismiss the current dialog
                    dismissAddDeleteUpdateRevenueDialog()
                }
            }

            root.updateRevenueButton.setOnClickListener {
                if (Revenue.areRevenueFieldsValid(root.descriptionTextInputField, root.valueTextInputField, this)) {
                    revenue!!.description = root.descriptionTextInputField.text.toString()
                    revenue.value = root.valueTextInputField.text.toString().toDouble()
                    revenue.paid = root.paidCheckBox.isChecked
                    onUpdateRevenueClick(revenue)

                    // Dismiss the current dialog
                    dismissAddDeleteUpdateRevenueDialog()
                }
            }
            addDeleteUpdateRevenueDialog?.show()
        }
    }

    private fun dismissAddDeleteUpdateRevenueDialog() {
        if (addDeleteUpdateRevenueDialog != null) {
            addDeleteUpdateRevenueDialog?.dismiss()
            addDeleteUpdateRevenueDialog = null
        }
    }

    override fun onRevenueSelected(revenue: Revenue) {
        showAddDeleteUpdateRevenueDialog(revenue)
    }
}
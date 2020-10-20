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
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.DUE_COLLECTION
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.DUE_COLLECTION_ORDER_BY
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.DUE_FIELD_DESCRIPTION
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.DUE_FIELD_PAID
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.DUE_FIELD_VALUE
import com.mateusmelodn.mymoney.FirestoreUtil.Companion.LIMIT_ITEM_PER_QUERY
import com.mateusmelodn.mymoney.R
import com.mateusmelodn.mymoney.adapter.DueAdapter
import com.mateusmelodn.mymoney.databinding.ActivityDueBinding
import com.mateusmelodn.mymoney.model.Due
import kotlinx.android.synthetic.main.dialog_add_delete_update_due.view.*
import java.util.*

// Due activity for due CRUD operations
class DueActivity : BaseActivity(), View.OnClickListener, DueAdapter.OnDueSelectedListener {
    // Reference for views
    private lateinit var binding: ActivityDueBinding
    // Reference for Firestore
    private lateinit var firestore: FirebaseFirestore
    // Reference for due apadter
    private lateinit var dueAdapter: DueAdapter

    companion object {
        private const val TAG = "DueActivity"
    }

    override fun onBackPressed() {
        onSupportNavigateUp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDueBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set dueFab listener
        binding.addDueFab.setOnClickListener(this)

        // Initialize Firestore
        firestore = Firebase.firestore

        // Query for getting dues
        val duesQuery = firestore
            .collection(DUE_COLLECTION)
            .orderBy(DUE_COLLECTION_ORDER_BY, Query.Direction.DESCENDING)
            .limit(LIMIT_ITEM_PER_QUERY)

        // Create due adapter
        dueAdapter = object : DueAdapter(duesQuery, this@DueActivity) {
            override fun onDataChanged() {
                if (itemCount == 0) {
                    // User must know when there's no due for best practice
                    binding.dueInfoTextView.visibility = View.VISIBLE
                    binding.duesRecyclerView.visibility = View.GONE
                } else {
                    // Displey dues
                    binding.dueInfoTextView.visibility = View.GONE
                    binding.duesRecyclerView.visibility = View.VISIBLE
                }
            }
        }
        // Set adapter to duesRecyclerView
        binding.duesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.duesRecyclerView.adapter = dueAdapter
    }

    override fun onStart() {
        super.onStart()
        dueAdapter.startListening()
    }

    override fun onStop() {
        super.onStop()
        dueAdapter.stopListening()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_from_left, R.anim.slide_out_to_right)
    }

    private fun onAddDueClick(due: Due) {
        addDue(due)
            .addOnSuccessListener(this) {
                Log.d(TAG, "Due added")

                // Hide keyboard and scroll to top
                hideKeyboard()
                binding.duesRecyclerView.smoothScrollToPosition(0)
            }
            .addOnFailureListener(this) { e ->
                Log.w(TAG, "Add due failed", e)

                // Show failure message and hide keyboard
                hideKeyboard()
                Snackbar.make(findViewById(android.R.id.content), R.string.failed_to_add,
                    Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun addDue(due: Due): Task<Void> {
        // Get dues collection reference
        val dueRef = firestore.collection(DUE_COLLECTION).document()

        // Save id for future uses
        due.id = dueRef.id

        return firestore.runTransaction { transaction ->
            // Commit to Firestore
            transaction.set(dueRef, due)

            null
        }
    }

    private fun onDeleteDueClick(due: Due) {
        deleteDue(due)
            .addOnSuccessListener(this) {
                Log.d(TAG, "Due deleted")

                // Hide keyboard and scroll to top
                hideKeyboard()
                binding.duesRecyclerView.smoothScrollToPosition(0)
            }
            .addOnFailureListener(this) { e ->
                Log.w(TAG, "Delete due failed", e)

                // Show failure message and hide keyboard
                hideKeyboard()
                Snackbar.make(findViewById(android.R.id.content), R.string.failed_to_delete,
                    Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun deleteDue(due: Due): Task<Void> {
        // Get due document reference based on id which was previously saved
        val dueRef = firestore.collection(DUE_COLLECTION).document(due.id)

        return firestore.runTransaction { transaction ->
            // Commit to Firestore
            transaction.delete(dueRef)

            null
        }
    }

    private fun onUpdateDueClick(due: Due) {
        updateDue(due)
            .addOnSuccessListener(this) {
                Log.d(TAG, "Due updated")

                // Hide keyboard and scroll to top
                hideKeyboard()
                binding.duesRecyclerView.smoothScrollToPosition(0)
            }
            .addOnFailureListener(this) { e ->
                Log.w(TAG, "Update due failed", e)

                // Show failure message and hide keyboard
                hideKeyboard()
                Snackbar.make(findViewById(android.R.id.content), R.string.failed_to_update,
                    Snackbar.LENGTH_SHORT).show()
            }
    }

    private fun updateDue(due: Due): Task<Void> {
        // Get due document reference based on id which was previously saved
        val dueRef = firestore.collection(DUE_COLLECTION).document(due.id)

        return firestore.runTransaction { transaction ->
            // Commit to Firestore
            transaction.update(dueRef,
                DUE_FIELD_VALUE, due.value,
                DUE_FIELD_DESCRIPTION, due.description,
                DUE_FIELD_PAID, due.paid
            )

            null
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.addDueFab -> showAddDeleteUpdateDueDialog(null)
        }
    }

    var addDeleteUpdateDueDialog: AlertDialog? = null
    private fun showAddDeleteUpdateDueDialog(due: Due?) {
        // When due is null, it means that is an addition operation
        // Otherwise is an update or deletion

        if (addDeleteUpdateDueDialog == null) {
            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            val root: View = layoutInflater.inflate(R.layout.dialog_add_delete_update_due, null)
            builder.setView(root)

            addDeleteUpdateDueDialog = builder.create()
            addDeleteUpdateDueDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Dialog won't be cancelable at all
            addDeleteUpdateDueDialog?.setCancelable(false)
            addDeleteUpdateDueDialog?.setCanceledOnTouchOutside(false)

            if (due != null) {
                // due is not null, it's an update or deletion!
                root.cancelButton.visibility = View.GONE
                root.deleteButton.visibility = View.VISIBLE
                root.addDueButton.visibility = View.GONE
                root.updateDueButton.visibility = View.VISIBLE

                root.dialogDueTitle.setText(R.string.delete_update_due)
                root.descriptionTextInputField.setText(due.description)
                root.valueTextInputField.setText(due.value.toString())
                root.paidCheckBox.isChecked = due.paid
            }

            root.cancelButton.setOnClickListener {
                dismissAddDeleteUpdateDueDialog()
            }

            root.deleteButton.setOnClickListener {
                onDeleteDueClick(due!!)
                dismissAddDeleteUpdateDueDialog()
            }

            root.addDueButton.setOnClickListener {
                // Verify if fields are valid
                if (Due.areDueFieldsValid(root.descriptionTextInputField, root.valueTextInputField, this)) {
                    val description = root.descriptionTextInputField.text.toString()
                    val value = root.valueTextInputField.text.toString()
                    val paid = root.paidCheckBox.isChecked
                    val newDue = Due("", value.toDouble(), description, Date(), paid)
                    onAddDueClick(newDue)

                    // Dismiss the current dialog
                    dismissAddDeleteUpdateDueDialog()
                }
            }

            root.updateDueButton.setOnClickListener {
                if (Due.areDueFieldsValid(root.descriptionTextInputField, root.valueTextInputField, this)) {
                    due!!.description = root.descriptionTextInputField.text.toString()
                    due.value = root.valueTextInputField.text.toString().toDouble()
                    due.paid = root.paidCheckBox.isChecked
                    onUpdateDueClick(due)

                    // Dismiss the current dialog
                    dismissAddDeleteUpdateDueDialog()
                }
            }
            addDeleteUpdateDueDialog?.show()
        }
    }

    private fun dismissAddDeleteUpdateDueDialog() {
        if (addDeleteUpdateDueDialog != null) {
            addDeleteUpdateDueDialog?.dismiss()
            addDeleteUpdateDueDialog = null
        }
    }

    override fun onDueSelected(due: Due) {
        showAddDeleteUpdateDueDialog(due)
    }
}
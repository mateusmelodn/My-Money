package com.mateusmelodn.mymoney.activity

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mateusmelodn.mymoney.FirestoreUtil
import com.mateusmelodn.mymoney.R
import com.mateusmelodn.mymoney.adapter.DueAdapter
import com.mateusmelodn.mymoney.adapter.RevenueAdapter
import com.mateusmelodn.mymoney.databinding.ActivitySummaryBinding
import com.mateusmelodn.mymoney.model.Due
import com.mateusmelodn.mymoney.model.Revenue
import kotlinx.android.synthetic.main.nav_header.view.*
import java.util.ArrayList

class SummaryActivity : BaseActivity(), View.OnClickListener, NavigationView.OnNavigationItemSelectedListener {
    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivitySummaryBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var dueAdapter: DueAdapter
    private lateinit var revenueAdapter: RevenueAdapter

    // For animation use
    private lateinit var mFrontAnim: AnimatorSet
    private lateinit var mBackAnim: AnimatorSet
    private var mIsFrontDueCardShowing = true
    private var mIsFrontRevenueCardShowing = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setProgressBar(binding.progressBar)

        // Button listener
        binding.dueFrontCardView.setOnClickListener(this)
        binding.dueBackCardView.setOnClickListener(this)
        binding.revenueFrontCardView.setOnClickListener(this)
        binding.revenueBackCardView.setOnClickListener(this)

        addDrawerLayout()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        addDueAndRevenueListeners()

        // Set up animation for flip card when user clicks buttons
        setUpDueCardAnimation()
        setUpRevenueCardAnimation()
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        updateUI(currentUser)
        dueAdapter.startListening()
        revenueAdapter.startListening()
    }

    override fun onStop() {
        super.onStop()
        dueAdapter.stopListening()
        revenueAdapter.stopListening()
    }

    private fun updateUI(user: FirebaseUser?) {
        hideProgressBar()
        if (user != null) {
            val root = binding.navView.getHeaderView(0)

            root.userNameTextView.text = user.displayName
            root.userEmailTextView.text = user.email
            val userImageView = root.userImageView

            // Background image
            Glide.with(userImageView.context)
                .load(user.photoUrl.toString())
                .circleCrop()
                .into(userImageView)
        } else {
            launchLoginActivity()
        }
    }

    private fun launchLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        this.finish()
    }

    private fun addDrawerLayout() {
        setSupportActionBar(binding.toolbar)

        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, 0, 0)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Main
            R.id.navDue -> {
                openDueActivity()
            }
            R.id.navRevenue -> {
                openRevenueActivity()
            }
            //Settings
            R.id.navSignOut -> {
                signOut()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun openDueActivity() {
        val intent = Intent(this, DueActivity::class.java)
        startActivity(intent)
    }

    private fun openRevenueActivity() {
        val intent = Intent(this, RevenueActivity::class.java)
        startActivity(intent)
    }

    private fun signOut() {
        showProgressBar()

        // Firebase sign out
        auth.signOut()

        // Google sign out
        googleSignInClient.signOut().addOnCompleteListener(this) {
            updateUI(null)
        }
    }

    private var clickedTwice = false
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        if (clickedTwice) {
            super.onBackPressed()
            return
        }

        clickedTwice = true
        Toast.makeText(this,
            R.string.click_again_to_exit, Toast.LENGTH_SHORT).show()
        Handler().postDelayed({clickedTwice = false},2000)
    }

    private fun addDueAndRevenueListeners() {
        // Initialize Firestore
        val firestore = Firebase.firestore

        // Get dues
        val duesQuery = firestore
            .collection(FirestoreUtil.DUE_COLLECTION)
            .orderBy(FirestoreUtil.DUE_COLLECTION_ORDER_BY, Query.Direction.DESCENDING)
            .limit(FirestoreUtil.LIMIT_ITEM_PER_QUERY)

        dueAdapter = object : DueAdapter(duesQuery, null) {
            override fun onDataChanged() {
                updateDuesUI(snapshots)
            }
        }

        // Get revenues
        val revenuesQuery = firestore
            .collection(FirestoreUtil.REVENUE_COLLECTION)
            .orderBy(FirestoreUtil.REVENUE_COLLECTION_ORDER_BY, Query.Direction.DESCENDING)
            .limit(FirestoreUtil.LIMIT_ITEM_PER_QUERY)

        revenueAdapter = object : RevenueAdapter(revenuesQuery, null) {
            override fun onDataChanged() {
                updateRevenuesUI(snapshots)
            }
        }
    }

    private fun updateDuesUI(snapshots: ArrayList<DocumentSnapshot>) {
        var totalDuesValue = 0.0
        for (snapshot in snapshots) {
            if (snapshot.exists()) {
                val due: Due? = snapshot.toObject(Due::class.java)
                due?.value?.let {
                    totalDuesValue += it
                }
            }
        }

        val totalDuesValueFormatted = "%.2f".format(totalDuesValue)
        binding.totalDues.text = "Você tem ${snapshots.size} dívidas"
        binding.totalDuesValue.text = "Você deve um total de R$ $totalDuesValueFormatted"
    }

    private fun updateRevenuesUI(snapshots: ArrayList<DocumentSnapshot>) {
        var totalRevenuesValue = 0.0

        for (snapshot in snapshots) {
            if (snapshot.exists()) {
                val revenue: Revenue? = snapshot.toObject(Revenue::class.java)
                revenue?.value?.let {
                    totalRevenuesValue += it
                }
            }
        }

        val totalRevenuesValueFormatted = "%.2f".format(totalRevenuesValue)
        binding.totalRevenues.text = "Você tem ${snapshots.size} receitas"
        binding.totalRevenuesValue.text = "Você obtém um total de R$ $totalRevenuesValueFormatted"
    }

    private fun setUpDueCardAnimation() {
        // Modify the camera scale
        val scale = applicationContext.resources.displayMetrics.density
        binding.dueFrontCardView.cameraDistance = 8000 * scale
        binding.dueBackCardView.cameraDistance = 8000 * scale

        // Set animations
        mFrontAnim = AnimatorInflater.loadAnimator(
            applicationContext,
            R.animator.front_due_card_view
        ) as AnimatorSet
        mBackAnim = AnimatorInflater.loadAnimator(
            applicationContext,
            R.animator.back_due_card_view
        ) as AnimatorSet
    }

    private fun flipDueCard() {
        if (mIsFrontDueCardShowing) {
            mFrontAnim.setTarget(binding.dueFrontCardView)
            mBackAnim.setTarget(binding.dueBackCardView)

            mFrontAnim.start()
            mBackAnim.start()

            mIsFrontDueCardShowing = false
        } else {
            mFrontAnim.setTarget(binding.dueBackCardView)
            mBackAnim.setTarget(binding.dueFrontCardView)

            mBackAnim.start()
            mFrontAnim.start()

            mIsFrontDueCardShowing = true
        }
    }

    private fun setUpRevenueCardAnimation() {
        // Modify the camera scale
        val scale = applicationContext.resources.displayMetrics.density
        binding.revenueFrontCardView.cameraDistance = 8000 * scale
        binding.revenueBackCardView.cameraDistance = 8000 * scale

        // Set animations
        mFrontAnim = AnimatorInflater.loadAnimator(
            applicationContext,
            R.animator.front_revenue_card_view
        ) as AnimatorSet
        mBackAnim = AnimatorInflater.loadAnimator(
            applicationContext,
            R.animator.back_revenue_card_view
        ) as AnimatorSet
    }

    private fun flipRevenueCard() {
        if (mIsFrontRevenueCardShowing) {
            mFrontAnim.setTarget(binding.revenueFrontCardView)
            mBackAnim.setTarget(binding.revenueBackCardView)

            mFrontAnim.start()
            mBackAnim.start()

            mIsFrontRevenueCardShowing = false
        } else {
            mFrontAnim.setTarget(binding.revenueBackCardView)
            mBackAnim.setTarget(binding.revenueFrontCardView)

            mBackAnim.start()
            mFrontAnim.start()

            mIsFrontRevenueCardShowing = true
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.dueFrontCardView -> flipDueCard()
            R.id.dueBackCardView -> flipDueCard()
            R.id.revenueFrontCardView -> flipRevenueCard()
            R.id.revenueBackCardView -> flipRevenueCard()
        }
    }
}
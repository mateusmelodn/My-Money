package com.mateusmelodn.mymoney.activity

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import com.bumptech.glide.Glide
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
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

class SummaryActivity : BaseActivity(), View.OnClickListener, NavigationView.OnNavigationItemSelectedListener {
    // Reference for FirebaseAuth
    private lateinit var auth: FirebaseAuth
    // Reference for views
    private lateinit var binding: ActivitySummaryBinding
    // Reference for GoogleSignInClient
    private lateinit var googleSignInClient: GoogleSignInClient

    // Reference for due and revenue apadter
    private lateinit var dueAdapter: DueAdapter
    private lateinit var revenueAdapter: RevenueAdapter

    // Uses in summary chart
    private var totalDuesValue = 0F
    private var totalRevenuesValue = 0F

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

        // Set cards listeners
        // They'll be flipped when clicked
        binding.dueFrontCardView.setOnClickListener(this)
        binding.dueBackCardView.setOnClickListener(this)
        binding.revenueFrontCardView.setOnClickListener(this)
        binding.revenueBackCardView.setOnClickListener(this)

        // Add menu drawer
        addDrawerLayout()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Set due and revenue listeners for updates
        addDueAndRevenueListeners()

        // Set up animation for flip card when user clicks the cards
        setUpDueCardAnimation()
        setUpRevenueCardAnimation()

        // Set no dues data text first of all
        binding.duesLineChart.setNoDataText(getString(R.string.there_is_no_data_available))
        binding.duesLineChart.setNoDataTextColor(R.color.colorAccent)

        // Set no revenues data text first of all
        binding.revenuesLineChart.setNoDataText(getString(R.string.there_is_no_data_available))
        binding.revenuesLineChart.setNoDataTextColor(R.color.colorAccent)

        // Set no summary data text first of all
        binding.summaryPieChart.setNoDataText(getString(R.string.there_is_no_data_available))
        binding.summaryPieChart.setNoDataTextColor(R.color.colorAccent)
    }

    override fun onStart() {
        super.onStart()
        updateUI(auth.currentUser)
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
            // Get nav view in order to find children views
            val root = binding.navView.getHeaderView(0)

            // Update nav view UI
            root.userNameTextView.text = user.displayName
            root.userEmailTextView.text = user.email
            val userImageView = root.userImageView

            // Set user image view
            Glide.with(userImageView.context)
                .load(user.photoUrl.toString())
                .circleCrop()
                .into(userImageView)
        } else {
            // There's no user anymore, then logout
            launchLoginActivity()
        }
    }

    private fun launchLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        this.finish()
    }

    private fun addDrawerLayout() {
        // Add toolbar
        setSupportActionBar(binding.toolbar)

        // Set up navigation drawer menu
        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, 0, 0)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.navDue -> openDueActivity()
            R.id.navRevenue -> openRevenueActivity()
            R.id.navSignOut -> signOut()
        }

        // This line is needed, otherwise menu won't close automatically
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
        // Show progress in order to indicate that there's an operation running
        showProgressBar()

        // Firebase sign out
        auth.signOut()

        // Google sign out
        googleSignInClient.signOut().addOnCompleteListener(this) {
            updateUI(null)
        }
    }

    private var clickedTwice = false
    // Override onBackPressed in order to perform a better use of the app
    // when the user wants to close it
    override fun onBackPressed() {
        // If menu is open, then close it first
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        // If user has clicked twice, then close the app
        if (clickedTwice) {
            super.onBackPressed()
            return
        }

        clickedTwice = true
        Snackbar.make(
            findViewById(android.R.id.content), R.string.click_again_to_exit,
            Snackbar.LENGTH_SHORT
        ).show()

        // Launch post delayed action
        // Deprecated, should be replaced
        Handler().postDelayed({ clickedTwice = false }, 2000)
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
        val duesTemp: ArrayList<Due> = ArrayList()
        totalDuesValue = 0F

        for (snapshot in snapshots.reversed()) {
            if (snapshot.exists()) {
                val due: Due? = snapshot.toObject(Due::class.java)
                due?.let { duesTemp.add(it) }
                due?.value?.let {
                    totalDuesValue += it.toFloat()
                }
            }
        }

        val totalDuesValueFormatted = "%.2f".format(totalDuesValue)
        binding.totalDues.text = getString(R.string.you_have_x_dues, snapshots.size.toString())
        binding.totalDuesValue.text = getString(
            R.string.you_owe_a_total_of,
            totalDuesValueFormatted
        )

        setDuesChart(duesTemp)
    }

    private fun updateRevenuesUI(snapshots: ArrayList<DocumentSnapshot>) {
        val revenuesTemp: ArrayList<Revenue> = ArrayList()
        totalRevenuesValue = 0F

        for (snapshot in snapshots.reversed()) {
            if (snapshot.exists()) {
                val revenue: Revenue? = snapshot.toObject(Revenue::class.java)
                revenue?.let { revenuesTemp.add(it) }
                revenue?.value?.let {
                    totalRevenuesValue += it.toFloat()
                }
            }
        }

        val totalRevenuesValueFormatted = "%.2f".format(totalRevenuesValue)
        binding.totalRevenues.text = getString(
            R.string.you_have_x_revenues,
            snapshots.size.toString()
        )
        binding.totalRevenuesValue.text = getString(
            R.string.you_get_a_total_of,
            totalRevenuesValueFormatted
        )

        setRevenuesChart(revenuesTemp)
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

    // Add due chart
    private fun setDuesChart(dues: ArrayList<Due>) {
        // Invalidate data chart
        binding.duesLineChart.invalidate()

        if (dues.size > 0) {
            binding.duesLineChart.data = generateDuesDataLine(dues)
            binding.duesLineChart.description = Description().apply { text = "R$" }
            binding.duesLineChart.axisLeft.setDrawLabels(false)
            binding.duesLineChart.xAxis.setDrawLabels(false)
        } else {
            binding.duesLineChart.setNoDataText(getString(R.string.there_is_no_data_available))
            binding.duesLineChart.setNoDataTextColor(R.color.colorAccent)
        }

        // Refresh data chart
        binding.duesLineChart.notifyDataSetChanged()

        // When dues or revenues are updated, summary chart needs to be too
        setSummaryChart()
    }

    // Generate due chart data
    private fun generateDuesDataLine(dues: ArrayList<Due>): LineData? {
        val values: ArrayList<Entry> = ArrayList()
        for (i in dues.indices) {
            values.add(Entry(i.toFloat(), dues[i].value.toFloat()))
        }
        val lineDataSet = LineDataSet(values, getString(R.string.dues))
        lineDataSet.lineWidth = 2.5f
        lineDataSet.circleRadius = 4.5f
        lineDataSet.highLightColor = Color.rgb(0, 0, 0)
        lineDataSet.color = R.color.red
        lineDataSet.setCircleColor(R.color.red)
        lineDataSet.setDrawValues(true)
        return LineData(lineDataSet)
    }

    // Add revenue chart
    private fun setRevenuesChart(revenues: ArrayList<Revenue>) {
        // Invalidate data chart
        binding.revenuesLineChart.invalidate()

        if (revenues.size > 0) {
            binding.revenuesLineChart.data = generateRevenuesDataLine(revenues)
            binding.revenuesLineChart.description = Description().apply { text = "R$" }
            binding.revenuesLineChart.axisLeft.setDrawLabels(false)
            binding.revenuesLineChart.xAxis.setDrawLabels(false)
        } else {
            binding.revenuesLineChart.setNoDataText(getString(R.string.there_is_no_data_available))
            binding.revenuesLineChart.setNoDataTextColor(R.color.colorAccent)
        }

        // Refresh data chart
        binding.revenuesLineChart.notifyDataSetChanged()

        // When dues or revenues are updated, summary chart needs to be too
        setSummaryChart()
    }

    // Generate revenue chart data
    private fun generateRevenuesDataLine(revenues: ArrayList<Revenue>): LineData? {
        val values: ArrayList<Entry> = ArrayList()
        for (i in revenues.indices) {
            values.add(Entry(i.toFloat(), revenues[i].value.toFloat()))
        }
        val lineDataSet = LineDataSet(values, getString(R.string.revenues))
        lineDataSet.lineWidth = 2.5f
        lineDataSet.circleRadius = 4.5f
        lineDataSet.highLightColor = Color.rgb(0, 0, 0)
        lineDataSet.color = R.color.green
        lineDataSet.setCircleColor(R.color.green)
        lineDataSet.setDrawValues(true)
        return LineData(lineDataSet)
    }

    // Add summary chart
    private fun setSummaryChart() {
        // Invalidate data chart
        binding.summaryPieChart.invalidate()

        binding.summaryPieChart.setBackgroundColor(Color.WHITE)
        binding.summaryPieChart.setUsePercentValues(true)
        binding.summaryPieChart.description.isEnabled = false
        binding.summaryPieChart.isDrawHoleEnabled = true
        binding.summaryPieChart.setHoleColor(Color.WHITE)
        binding.summaryPieChart.setTransparentCircleColor(Color.WHITE)
        binding.summaryPieChart.setTransparentCircleAlpha(110)
        binding.summaryPieChart.holeRadius = 58f
        binding.summaryPieChart.transparentCircleRadius = 61f
        binding.summaryPieChart.setDrawCenterText(true)
        binding.summaryPieChart.isRotationEnabled = false
        binding.summaryPieChart.isHighlightPerTapEnabled = true
        binding.summaryPieChart.rotationAngle = 180f
        binding.summaryPieChart.setCenterTextOffset(0f, -20f)

        val data = generateSummaryPieData()
        binding.summaryPieChart.setData(data)
        binding.summaryPieChart.invalidate()

        binding.summaryPieChart.animateY(1400, Easing.EaseInOutQuad)

        val legend = binding.summaryPieChart.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.xEntrySpace = 7f
        legend.yEntrySpace = 0f
        legend.yOffset = 0f

        binding.summaryPieChart.setEntryLabelColor(Color.WHITE)
        binding.summaryPieChart.setEntryLabelTextSize(12f)

        // Refresh data chart
        binding.summaryPieChart.notifyDataSetChanged()
    }

    // Generate summary chart data
    private fun generateSummaryPieData(): PieData {
        val values = java.util.ArrayList<PieEntry>()
        values.add(PieEntry(totalRevenuesValue, getString(R.string.revenues)))
        values.add(PieEntry(totalDuesValue, getString(R.string.dues)))

        val dataSet = PieDataSet(values, "")
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        dataSet.setColors(*ColorTemplate.MATERIAL_COLORS)
        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter())
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.WHITE)
        return data
    }
}
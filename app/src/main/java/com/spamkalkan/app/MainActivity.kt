package com.spamkalkan.app

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS
    )
    private val PERMISSION_REQUEST = 100
    private val ROLE_REQUEST = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SpamManager.isSubscribed(this)) {
            setContentView(R.layout.activity_paywall)
            setupPaywall()
            return
        }

        setContentView(R.layout.activity_main)
        requestPermissionsIfNeeded()
        setupTabs()
        showTab("panel")
        updateFirebaseInBackground()
    }

    private fun setupPaywall() {
        // Plan seçimi
        var selectedPlan = "annual"
        val plans = listOf(
            Triple("monthly", "1 Aylık", "₺49"),
            Triple("biannual", "6 Aylık", "₺199"),
            Triple("annual", "12 Aylık", "₺299")
        )

        val planContainer = findViewById<LinearLayout>(R.id.planContainer)
        val btnSubscribe = findViewById<Button>(R.id.btnSubscribe)

        plans.forEach { (id, title, price) ->
            val btn = RadioButton(this).apply {
                text = "$title — $price"
                textSize = 16f
                setTextColor(resources.getColor(R.color.text, null))
                isChecked = id == "annual"
                setOnClickListener { selectedPlan = id }
            }
            planContainer.addView(btn)
        }

        btnSubscribe.setOnClickListener {
            // Test modu - gerçek ödeme Google Play'den gelecek
            SpamManager.setSubscribed(this, true)
            recreate()
        }

        // Test butonu
        findViewById<TextView>(R.id.tvTestMode)?.setOnClickListener {
            SpamManager.setSubscribed(this, true)
            recreate()
        }
    }

    private fun setupTabs() {
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showTab("panel")
                    1 -> showTab("settings")
                    2 -> showTab("history")
                    3 -> showTab("account")
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showTab(tab: String) {
        val panelView = findViewById<View>(R.id.panelView)
        val settingsView = findViewById<View>(R.id.settingsView)
        val historyView = findViewById<View>(R.id.historyView)
        val accountView = findViewById<View>(R.id.accountView)

        panelView.visibility = if (tab == "panel") View.VISIBLE else View.GONE
        settingsView.visibility = if (tab == "settings") View.VISIBLE else View.GONE
        historyView.visibility = if (tab == "history") View.VISIBLE else View.GONE
        accountView.visibility = if (tab == "account") View.VISIBLE else View.GONE

        when (tab) {
            "panel" -> setupPanel()
            "settings" -> setupSettings()
            "history" -> setupHistory()
            "account" -> setupAccount()
        }
    }

    private fun setupPanel() {
        val stats = LogManager.getStats(this)
        val isOn = SpamManager.isProtectionOn(this)

        findViewById<TextView>(R.id.tvProtectionStatus)?.text =
            if (isOn) "🛡 KORUMA AKTİF" else "⚠️ KORUMA KAPALI"
        findViewById<TextView>(R.id.tvBlockedTotal)?.text = "${stats["total"] ?: 0}"
        findViewById<TextView>(R.id.tvBlockedSpam)?.text = "${stats["spam"] ?: 0}"
        findViewById<TextView>(R.id.tvBlockedBank)?.text = "${stats["bank"] ?: 0}"
        findViewById<TextView>(R.id.tvBlockedSms)?.text = "${stats["sms"] ?: 0}"

        // Rol kontrolü
        val roleManager = getSystemService(RoleManager::class.java)
        val hasRole = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        findViewById<TextView>(R.id.tvRoleStatus)?.text =
            if (hasRole) "✅ Arama filtreleme aktif" else "⚠️ Arama filtreleme için izin gerekli"

        findViewById<Button>(R.id.btnRequestRole)?.apply {
            visibility = if (hasRole) View.GONE else View.VISIBLE
            setOnClickListener { requestCallScreeningRole() }
        }

        findViewById<Button>(R.id.btnRefreshList)?.setOnClickListener {
            updateFirebaseInBackground()
            Toast.makeText(this, "Liste güncelleniyor...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSettings() {
        val switches = mapOf(
            R.id.switchProtection to Pair(
                { SpamManager.isProtectionOn(this) },
                { v: Boolean -> SpamManager.setProtection(this, v) }
            ),
            R.id.switchBlockUnknown to Pair(
                { SpamManager.isBlockUnknown(this) },
                { v: Boolean -> SpamManager.setBlockUnknown(this, v) }
            ),
            R.id.switchSilenceBanks to Pair(
                { SpamManager.isSilenceBanks(this) },
                { v: Boolean -> SpamManager.setSilenceBanks(this, v) }
            ),
            R.id.switchBlockPrefixes to Pair(
                { SpamManager.isBlockPrefixes(this) },
                { v: Boolean -> SpamManager.setBlockPrefixes(this, v) }
            ),
            R.id.switchBlockKeywords to Pair(
                { SpamManager.isBlockKeywords(this) },
                { v: Boolean -> SpamManager.setBlockKeywords(this, v) }
            ),
            R.id.switchBlockInternational to Pair(
                { SpamManager.isBlockInternational(this) },
                { v: Boolean -> SpamManager.setBlockInternational(this, v) }
            )
        )

        switches.forEach { (id, pair) ->
            try {
                val switch = findViewById<Switch>(id)
                switch?.isChecked = pair.first()
                switch?.setOnCheckedChangeListener { _, v -> pair.second(v) }
            } catch (e: Exception) {}
        }

        // Ön ek seçici
        setupPrefixSelector()
    }

    private fun setupPrefixSelector() {
        val prefixOptions = listOf(
            Pair("+90850", "0850 — Çağrı Merkezi"),
            Pair("+90444", "0444 — Kısa Numara"),
            Pair("+90212", "0212 — İstanbul Avrupa"),
            Pair("+90216", "0216 — İstanbul Anadolu"),
            Pair("+90312", "0312 — Ankara")
        )

        val container = findViewById<LinearLayout>(R.id.prefixContainer) ?: return
        container.removeAllViews()

        val selected = SpamManager.getSelectedPrefixes(this).toMutableList()

        prefixOptions.forEach { (prefix, label) ->
            val cb = CheckBox(this).apply {
                text = label
                isChecked = selected.contains(prefix)
                setTextColor(resources.getColor(R.color.text, null))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) { if (!selected.contains(prefix)) selected.add(prefix) }
                    else selected.remove(prefix)
                    SpamManager.setSelectedPrefixes(this@MainActivity, selected)
                }
            }
            container.addView(cb)
        }
    }

    private fun setupHistory() {
        val logs = LogManager.getLogs(this)
        val container = findViewById<LinearLayout>(R.id.historyContainer) ?: return
        container.removeAllViews()

        if (logs.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Henüz engellenen arama veya mesaj yok"
                setTextColor(resources.getColor(R.color.muted, null))
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(0, 64, 0, 0)
            }
            container.addView(tv)
            return
        }

        logs.forEach { log ->
            val number = log.getString("number")
            val type = log.getString("type")
            val time = log.getString("time")

            val color = when (type) {
                "spam", "prefix" -> resources.getColor(R.color.danger, null)
                "bank" -> resources.getColor(R.color.bank, null)
                "sms_spam" -> resources.getColor(R.color.accent, null)
                "international" -> resources.getColor(R.color.warning, null)
                else -> resources.getColor(R.color.muted, null)
            }

            val icon = when (type) {
                "spam", "prefix" -> "🚫"
                "bank" -> "🏦"
                "sms_spam" -> "✉️"
                "international" -> "🌍"
                else -> "❓"
            }

            val label = when (type) {
                "spam" -> "Spam Engellendi"
                "prefix" -> "Önek Engellendi"
                "bank" -> "Banka Sessize Alındı"
                "sms_spam" -> "Spam SMS"
                "international" -> "Yurtdışı Sessize Alındı"
                "unknown" -> "Bilinmeyen Sessize Alındı"
                else -> type
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
            }

            val iconTv = TextView(this).apply {
                text = icon
                textSize = 20f
                setPadding(0, 0, 16, 0)
            }

            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val numberTv = TextView(this).apply {
                text = number.ifEmpty { "Bilinmeyen" }
                setTextColor(resources.getColor(R.color.text, null))
                textSize = 14f
            }

            val labelTv = TextView(this).apply {
                text = label
                setTextColor(color)
                textSize = 12f
            }

            val timeTv = TextView(this).apply {
                text = time
                setTextColor(resources.getColor(R.color.muted, null))
                textSize = 11f
            }

            infoLayout.addView(numberTv)
            infoLayout.addView(labelTv)
            row.addView(iconTv)
            row.addView(infoLayout)
            row.addView(timeTv)
            container.addView(row)

            // Ayırıcı çizgi
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(resources.getColor(R.color.border, null))
            }
            container.addView(divider)
        }
    }

    private fun setupAccount() {
        findViewById<Button>(R.id.btnCancelSubscription)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Aboneliği İptal Et")
                .setMessage("Google Play → Abonelikler → Spam Kalkan yolunu izleyin.")
                .setPositiveButton("Tamam", null)
                .show()
        }
    }

    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            startActivityForResult(intent, ROLE_REQUEST)
        }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    private fun updateFirebaseInBackground() {
        lifecycleScope.launch {
            SpamManager.updateFromFirebase(applicationContext)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ROLE_REQUEST) {
            setupPanel()
        }
    }
}

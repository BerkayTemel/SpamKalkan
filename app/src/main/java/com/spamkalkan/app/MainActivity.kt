package com.spamkalkan.app

import android.Manifest
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

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
    private val SMS_DEFAULT_REQUEST = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SpamManager.isSubscribed(this)) {
            setContentView(R.layout.activity_paywall)
            setupPaywall()
            return
        }

        setContentView(R.layout.activity_main)
        requestAllPermissions()
        setupTabs()
        showTab("panel")
        updateFirebaseInBackground()
    }

    private fun requestAllPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST)
        } else {
            requestCallScreeningRoleIfNeeded()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            requestCallScreeningRoleIfNeeded()
        }
    }

    private fun requestCallScreeningRoleIfNeeded() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            AlertDialog.Builder(this)
                .setTitle("Arama Filtreleme İzni")
                .setMessage("Spam aramaları engelleyebilmek için Spam Kalkan'ın arama filtreleme uygulaması olarak ayarlanması gerekiyor.")
                .setPositiveButton("İzin Ver") { _, _ ->
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    startActivityForResult(intent, ROLE_REQUEST)
                }
                .setNegativeButton("Daha Sonra", null)
                .show()
        } else {
            requestSmsDefaultIfNeeded()
        }
    }

    private fun requestSmsDefaultIfNeeded() {
        val isDefault = Telephony.Sms.getDefaultSmsPackage(this) == packageName
        if (!isDefault) {
            AlertDialog.Builder(this)
                .setTitle("SMS Filtreleme İzni")
                .setMessage("Spam SMS'leri filtrelemek için Spam Kalkan'ın varsayılan SMS uygulaması olarak ayarlanması gerekiyor. Endişelenmeyin, SMS'leriniz silinmez.")
                .setPositiveButton("İzin Ver") { _, _ ->
                    val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                        putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                    }
                    startActivityForResult(intent, SMS_DEFAULT_REQUEST)
                }
                .setNegativeButton("Daha Sonra", null)
                .show()
        }
    }

    private fun setupPaywall() {
        var selectedPlan = "annual"
        val planContainer = findViewById<LinearLayout>(R.id.planContainer)
        val btnSubscribe = findViewById<Button>(R.id.btnSubscribe)

        data class Plan(val id: String, val title: String, val price: String, val perMonth: String, val saving: String?, val badge: String?)
        val plans = listOf(
            Plan("monthly", "1 Aylık", "₺49", "₺49/ay", null, null),
            Plan("biannual", "6 Aylık", "₺199", "₺33/ay", "₺95 tasarruf", "🔥 %32 İndirim"),
            Plan("annual", "12 Aylık", "₺299", "₺25/ay", "₺289 tasarruf", "⭐ En İyi Değer")
        )

        plans.forEach { plan ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = if (plan.id == "annual")
                    ContextCompat.getDrawable(context, R.drawable.card_selected_bg)
                else ContextCompat.getDrawable(context, R.drawable.card_bg)
                setPadding(40, 32, 40, 32)
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 0, 24)
                layoutParams = p
                setOnClickListener {
                    selectedPlan = plan.id
                    for (i in 0 until planContainer.childCount) {
                        planContainer.getChildAt(i).background = ContextCompat.getDrawable(context, R.drawable.card_bg)
                    }
                    background = ContextCompat.getDrawable(context, R.drawable.card_selected_bg)
                }
            }

            plan.badge?.let {
                card.addView(TextView(this).apply {
                    text = it
                    setTextColor(if (plan.id == "annual") getColor(R.color.accent) else getColor(R.color.warning))
                    textSize = 11f
                    setPadding(0, 0, 0, 8)
                })
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoLayout.addView(TextView(this).apply {
                text = plan.title
                setTextColor(getColor(R.color.text))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            infoLayout.addView(TextView(this).apply {
                text = "${plan.perMonth}${plan.saving?.let { s -> " · $s" } ?: ""}"
                setTextColor(getColor(R.color.muted))
                textSize = 12f
            })
            row.addView(infoLayout)
            row.addView(TextView(this).apply {
                text = plan.price
                setTextColor(if (plan.id == "annual") getColor(R.color.accent) else getColor(R.color.text))
                textSize = 24f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            card.addView(row)
            planContainer.addView(card)
        }

        btnSubscribe.setOnClickListener {
            SpamManager.setSubscribed(this, true)
            recreate()
        }
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
        mapOf(
            "panel" to R.id.panelView,
            "settings" to R.id.settingsView,
            "history" to R.id.historyView,
            "account" to R.id.accountView
        ).forEach { (name, id) ->
            findViewById<View>(id)?.visibility = if (name == tab) View.VISIBLE else View.GONE
        }
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
        val roleManager = getSystemService(RoleManager::class.java)
        val hasRole = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        val isDefaultSms = Telephony.Sms.getDefaultSmsPackage(this) == packageName

        findViewById<TextView>(R.id.tvProtectionStatus)?.apply {
            text = if (isOn) "🛡 KORUMA AKTİF" else "⚠️ KORUMA KAPALI"
            setTextColor(getColor(if (isOn) R.color.success else R.color.warning))
        }
        findViewById<TextView>(R.id.tvBlockedTotal)?.text = "${stats["total"] ?: 0}"
        findViewById<TextView>(R.id.tvBlockedSpam)?.text = "${stats["spam"] ?: 0}"
        findViewById<TextView>(R.id.tvBlockedBank)?.text = "${stats["bank"] ?: 0}"
        findViewById<TextView>(R.id.tvBlockedSms)?.text = "${stats["sms"] ?: 0}"

        findViewById<TextView>(R.id.tvRoleStatus)?.apply {
            text = if (hasRole) "✅ Arama filtreleme aktif" else "⚠️ Arama izni gerekli"
            setTextColor(getColor(if (hasRole) R.color.success else R.color.warning))
        }
        findViewById<Button>(R.id.btnRequestRole)?.apply {
            visibility = if (hasRole) View.GONE else View.VISIBLE
            setOnClickListener { requestCallScreeningRoleIfNeeded() }
        }

        findViewById<TextView>(R.id.tvSmsStatus)?.apply {
            text = if (isDefaultSms) "✅ SMS filtreleme aktif" else "⚠️ SMS izni gerekli"
            setTextColor(getColor(if (isDefaultSms) R.color.success else R.color.warning))
        }
        findViewById<Button>(R.id.btnRequestSmsDefault)?.apply {
            visibility = if (isDefaultSms) View.GONE else View.VISIBLE
            setOnClickListener { requestSmsDefaultIfNeeded() }
        }

        findViewById<Button>(R.id.btnRefreshList)?.setOnClickListener {
            updateFirebaseInBackground()
            Toast.makeText(this, "🔄 Liste güncelleniyor...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSettings() {
        mapOf(
            R.id.switchProtection to Pair({ SpamManager.isProtectionOn(this) }, { v: Boolean -> SpamManager.setProtection(this, v) }),
            R.id.switchBlockUnknown to Pair({ SpamManager.isBlockUnknown(this) }, { v: Boolean -> SpamManager.setBlockUnknown(this, v) }),
            R.id.switchSilenceBanks to Pair({ SpamManager.isSilenceBanks(this) }, { v: Boolean -> SpamManager.setSilenceBanks(this, v) }),
            R.id.switchBlockPrefixes to Pair({ SpamManager.isBlockPrefixes(this) }, { v: Boolean -> SpamManager.setBlockPrefixes(this, v) }),
            R.id.switchBlockKeywords to Pair({ SpamManager.isBlockKeywords(this) }, { v: Boolean -> SpamManager.setBlockKeywords(this, v) }),
            R.id.switchBlockInternational to Pair({ SpamManager.isBlockInternational(this) }, { v: Boolean -> SpamManager.setBlockInternational(this, v) })
        ).forEach { (id, pair) ->
            try {
                val sw = findViewById<Switch>(id)
                sw?.isChecked = pair.first()
                sw?.setOnCheckedChangeListener { _, v -> pair.second(v) }
            } catch (e: Exception) {}
        }
        setupPrefixSelector()
    }

    private fun setupPrefixSelector() {
        val prefixOptions = listOf(
            "+90850" to "0850 — Çağrı Merkezi",
            "+90444" to "0444 — Kısa Numara",
            "+90212" to "0212 — İstanbul Avrupa",
            "+90216" to "0216 — İstanbul Anadolu",
            "+90312" to "0312 — Ankara"
        )
        val container = findViewById<LinearLayout>(R.id.prefixContainer) ?: return
        container.removeAllViews()
        val selected = SpamManager.getSelectedPrefixes(this).toMutableList()
        prefixOptions.forEach { (prefix, label) ->
            container.addView(CheckBox(this).apply {
                text = label
                isChecked = selected.contains(prefix)
                setTextColor(getColor(R.color.text))
                textSize = 14f
                setPadding(0, 8, 0, 8)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) { if (!selected.contains(prefix)) selected.add(prefix) }
                    else selected.remove(prefix)
                    SpamManager.setSelectedPrefixes(this@MainActivity, selected)
                }
            })
        }
    }

    private fun setupHistory() {
        val logs = LogManager.getLogs(this)
        val container = findViewById<LinearLayout>(R.id.historyContainer) ?: return
        container.removeAllViews()

        if (logs.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Henüz engellenen arama veya mesaj yok 🎉"
                setTextColor(getColor(R.color.muted))
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                textSize = 14f
                setPadding(0, 64, 0, 0)
            })
            return
        }

        logs.forEach { log ->
            val number = log.getString("number")
            val type = log.getString("type")
            val time = log.getString("time")
            val (color, icon, label) = when (type) {
                "spam" -> Triple(getColor(R.color.danger), "🚫", "Spam Engellendi")
                "prefix" -> Triple(getColor(R.color.danger), "🚫", "Önek Engellendi")
                "bank" -> Triple(getColor(R.color.bank), "🏦", "Banka Sessize Alındı")
                "sms_spam" -> Triple(getColor(R.color.accent), "✉️", "Spam SMS Engellendi")
                "international" -> Triple(getColor(R.color.warning), "🌍", "Yurtdışı Sessize Alındı")
                else -> Triple(getColor(R.color.muted), "❓", "Bilinmeyen Sessize Alındı")
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val iconTv = TextView(this).apply {
                text = icon
                textSize = 22f
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.setMargins(0, 0, 24, 0)
                layoutParams = p
            }
            val infoLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoLayout.addView(TextView(this).apply {
                text = number.ifEmpty { "Bilinmeyen" }
                setTextColor(getColor(R.color.text))
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            infoLayout.addView(TextView(this).apply {
                text = label
                setTextColor(color)
                textSize = 12f
            })
            row.addView(iconTv)
            row.addView(infoLayout)
            row.addView(TextView(this).apply {
                text = time
                setTextColor(getColor(R.color.muted))
                textSize = 11f
            })
            container.addView(row)
            container.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(getColor(R.color.border))
            })
        }
    }

    private fun setupAccount() {
        findViewById<Button>(R.id.btnCancelSubscription)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Aboneliği İptal Et")
                .setMessage("Google Play → Abonelikler → Spam Kalkan yolunu izleyin.")
                .setPositiveButton("Tamam", null).show()
        }
    }

    private fun updateFirebaseInBackground() {
        lifecycleScope.launch {
            SpamManager.updateFromFirebase(applicationContext)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ROLE_REQUEST -> {
                setupPanel()
                requestSmsDefaultIfNeeded()
            }
            SMS_DEFAULT_REQUEST -> setupPanel()
        }
    }

    override fun onResume() {
        super.onResume()
        if (SpamManager.isSubscribed(this)) setupPanel()
    }
}

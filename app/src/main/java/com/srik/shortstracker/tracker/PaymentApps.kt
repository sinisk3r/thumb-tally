package com.srik.shortstracker.tracker

/**
 * UPI, wallet and bank apps that refuse to work (often on the pay screen) while any third-party accessibility
 * service is switched on. When one of these comes to the front, the tracker switches itself off and posts a
 * notification, so payments go through. Android doesn't let an app switch its own service back on, so the
 * notification opens Accessibility settings.
 *
 * Every package here must also be listed in res/xml/accessibility_service_config.xml, or the tracker never
 * hears about it. Package names were collected by hand; check one on a real phone before relying on it.
 */
object PaymentApps {
    val labels: Map<String, String> = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
        "com.mobikwik_new" to "MobiKwik",
        "com.sbi.lotusintouch" to "YONO SBI",
        "com.csam.icici.bank.imobile" to "iMobile",
        "com.snapwork.hdfc" to "HDFC Bank",
        "com.hdfcbank.android.now" to "HDFC Bank",
        "com.axis.mobile" to "Axis Mobile",
        "com.msf.kbank.mobile" to "Kotak",
        "com.kotak.bank.mobile" to "Kotak",
        "com.ge.capital.konysbiapp" to "SBI Card",
        "com.bankofbaroda.mconnect" to "bob World",
    )

    fun labelFor(pkg: String?): String? = pkg?.let(labels::get)
}

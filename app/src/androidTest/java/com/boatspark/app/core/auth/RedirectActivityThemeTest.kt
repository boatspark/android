package com.boatspark.app.core.auth

import android.content.ComponentName
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import net.openid.appauth.RedirectUriReceiverActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectActivityThemeTest {
    @Test
    fun redirectActivityUsesAppCompatTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, RedirectUriReceiverActivity::class.java),
            0,
        )
        val attributes = ContextThemeWrapper(context, info.themeResource)
            .obtainStyledAttributes(intArrayOf(androidx.appcompat.R.attr.windowActionBar))
        try {
            assertTrue(
                "RedirectUriReceiverActivity is an AppCompatActivity and crashes without an AppCompat theme",
                attributes.hasValue(0),
            )
        } finally {
            attributes.recycle()
        }
    }
}

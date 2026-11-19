package com.chencohensolutions.knesset

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chencohensolutions.knesset.core.platform.AppInfo
import com.chencohensolutions.knesset.core.platform.Sharer
import com.chencohensolutions.knesset.core.platform.UrlOpener

class AndroidUrlOpener(private val context: Context) : UrlOpener {
    override fun open(url: String): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

class AndroidSharer(private val context: Context) : Sharer {
    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class AndroidAppInfo : AppInfo {
    override val versionName: String = BuildConfig.VERSION_NAME
}

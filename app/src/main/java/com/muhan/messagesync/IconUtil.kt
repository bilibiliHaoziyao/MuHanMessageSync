package com.muhan.messagesync

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream

/**
 * 应用图标工具：Drawable/Bitmap 与 base64 互转，便于随邮件传输与本地历史展示。
 */
object IconUtil {

    /** Drawable -> 正方形 Bitmap */
    fun drawableToBitmap(d: Drawable, sizePx: Int = 128): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(c)
        return bmp
    }

    /** 通过包名取应用图标（失败返回 null） */
    fun appIconBitmap(ctx: Context, pkg: String, sizePx: Int = 128): Bitmap? = try {
        val d = ctx.packageManager.getApplicationIcon(pkg)
        drawableToBitmap(d, sizePx)
    } catch (e: Exception) {
        null
    }

    /** 通过系统通知的 smallIcon（Icon）取图标（API 23+），失败返回 null */
    fun iconToBitmap(ic: Icon?, sizePx: Int = 128): Bitmap? {
        if (ic == null || Build.VERSION.SDK_INT < 23) return null
        return try {
            val d = ic.loadDrawable(null) ?: return null
            drawableToBitmap(d, sizePx)
        } catch (e: Exception) {
            null
        }
    }

    /** Bitmap -> base64 PNG */
    fun bitmapToBase64(bmp: Bitmap, sizePx: Int = 128): String? = try {
        val scaled = if (bmp.width != sizePx || bmp.height != sizePx)
            Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true) else bmp
        val os = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, os)
        Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    fun base64ToBitmap(b64: String?): Bitmap? {
        if (b64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /** 判断包名是否为系统应用 */
    fun isSystemApp(ctx: Context, pkg: String): Boolean = try {
        val ai = ctx.packageManager.getApplicationInfo(pkg, 0)
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

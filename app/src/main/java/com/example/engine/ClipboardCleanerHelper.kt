package com.example.engine

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.example.model.ClipboardStatus

object ClipboardCleanerHelper {

    fun readStatus(context: Context): ClipboardStatus {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ClipboardStatus()

        if (!manager.hasPrimaryClip()) {
            return ClipboardStatus(
                hasContent = false,
                itemCount = 0,
                previewSummary = "الحافظة فارغة تماماً",
                characterCount = 0
            )
        }

        val desc = manager.primaryClipDescription
        val mime = if (desc != null && desc.mimeTypeCount > 0) desc.getMimeType(0) else ""
        val typeLabel = when {
            mime == ClipDescription.MIMETYPE_TEXT_PLAIN -> "نص محفوظ في الحافظة"
            mime == ClipDescription.MIMETYPE_TEXT_HTML -> "نص منسق في الحافظة"
            mime == ClipDescription.MIMETYPE_TEXT_URILIST -> "رابط محفوظ في الحافظة"
            mime.startsWith("image/") -> "صورة محفوظة في الحافظة"
            else -> "بيانات منسوخة في الحافظة"
        }

        // Avoid triggering Android 13+ clipboard paste toast during passive dashboard reads
        val approxCount = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runCatching { manager.primaryClip?.getItemAt(0)?.text?.length ?: 0 }.getOrDefault(0)
        } else {
            0
        }

        return ClipboardStatus(
            hasContent = true,
            itemCount = 1,
            previewSummary = if (approxCount > 0) "$typeLabel ($approxCount حرف)" else typeLabel,
            characterCount = approxCount
        )
    }

    fun clearClipboard(context: Context): Boolean {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            true
        }.getOrDefault(false)
    }
}

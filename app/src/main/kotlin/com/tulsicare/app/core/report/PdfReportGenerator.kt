package com.tulsicare.app.core.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tulsicare.app.core.model.DoseEvent
import com.tulsicare.app.core.model.DoseStatus
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    fun generateReport(
        context: Context,
        patientName: String,
        groupName: String,
        startDate: Long,
        endDate: Long,
        adherencePercent: Int,
        streakDays: Int,
        events: List<DoseEvent>
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        // 1. Teal Header Banner
        paint.color = Color.rgb(15, 157, 138) // PrimaryTeal #0F9D8A
        canvas.drawRect(0f, 0f, 595f, 80f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Tulsi Adherence Report", 32f, 42f, paint)

        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Medication Adherence Record for Healthcare Providers & Family", 32f, 62f, paint)

        // 2. Patient & Period Information
        paint.color = Color.rgb(18, 48, 74) // DeepNavy #12304A
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Patient: $patientName", 32f, 115f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.rgb(74, 98, 119)
        canvas.drawText("Family Circle: $groupName", 32f, 132f, paint)
        canvas.drawText("Reporting Period: ${dateFormat.format(Date(startDate))} - ${dateFormat.format(Date(endDate))}", 32f, 148f, paint)

        // 3. KPI Metrics Summary Box
        paint.color = Color.rgb(246, 250, 249) // SoftBackground #F6FAF9
        canvas.drawRoundRect(32f, 165f, 563f, 225f, 12f, 12f, paint)

        paint.color = Color.rgb(15, 157, 138)
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("$adherencePercent%", 52f, 204f, paint)

        paint.textSize = 9f
        paint.color = Color.rgb(74, 98, 119)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("ADHERENCE RATE", 52f, 217f, paint)

        val total = events.size
        val taken = events.count { it.status == DoseStatus.TAKEN }
        val skipped = events.count { it.status == DoseStatus.SKIPPED }
        val missed = events.count { it.status == DoseStatus.MISSED || it.status == DoseStatus.PENDING }

        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(18, 48, 74)
        canvas.drawText("$streakDays Days", 180f, 202f, paint)
        paint.textSize = 9f
        paint.color = Color.rgb(74, 98, 119)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("CURRENT STREAK", 180f, 217f, paint)

        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(46, 158, 91) // StatusTakenGreen
        canvas.drawText("$taken / $total", 310f, 202f, paint)
        paint.textSize = 9f
        paint.color = Color.rgb(74, 98, 119)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("DOSES TAKEN", 310f, 217f, paint)

        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.rgb(242, 107, 91) // StatusMissedCoral
        canvas.drawText("$skipped / $missed", 440f, 202f, paint)
        paint.textSize = 9f
        paint.color = Color.rgb(74, 98, 119)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("SKIPPED / MISSED", 440f, 217f, paint)

        // 4. Dose Log Table
        paint.color = Color.rgb(18, 48, 74)
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Dose Activity Log", 32f, 255f, paint)

        // Table Header
        paint.color = Color.rgb(235, 240, 242)
        canvas.drawRect(32f, 268f, 563f, 288f, paint)

        paint.color = Color.rgb(18, 48, 74)
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("SCHEDULED TIME", 42f, 282f, paint)
        canvas.drawText("MEDICINE", 180f, 282f, paint)
        canvas.drawText("DOSAGE", 350f, 282f, paint)
        canvas.drawText("STATUS", 460f, 282f, paint)

        // Rows (up to 22 entries)
        var currentY = 305f
        paint.typeface = Typeface.DEFAULT
        val displayEvents = events.sortedByDescending { it.scheduledAt }.take(22)

        for (event in displayEvents) {
            paint.color = Color.rgb(50, 60, 70)
            paint.textSize = 9.5f
            canvas.drawText(timeFormat.format(Date(event.scheduledAt)), 42f, currentY, paint)

            val displayMedName = if (event.medicineName.length > 25) {
                event.medicineName.take(22) + "..."
            } else {
                event.medicineName
            }
            canvas.drawText(displayMedName, 180f, currentY, paint)

            val displayDosage = if (event.dosage.length > 18) {
                event.dosage.take(15) + "..."
            } else {
                event.dosage.ifBlank { "1 dose" }
            }
            canvas.drawText(displayDosage, 350f, currentY, paint)

            when (event.status) {
                DoseStatus.TAKEN -> {
                    paint.color = Color.rgb(46, 158, 91)
                    canvas.drawText("Taken", 460f, currentY, paint)
                }
                DoseStatus.SKIPPED -> {
                    paint.color = Color.rgb(138, 151, 163)
                    canvas.drawText("Skipped", 460f, currentY, paint)
                }
                DoseStatus.SNOOZED -> {
                    paint.color = Color.rgb(242, 169, 59)
                    canvas.drawText("Snoozed", 460f, currentY, paint)
                }
                else -> {
                    paint.color = Color.rgb(242, 107, 91)
                    canvas.drawText("Missed", 460f, currentY, paint)
                }
            }

            // Divider line
            paint.color = Color.rgb(240, 242, 244)
            canvas.drawLine(32f, currentY + 6f, 563f, currentY + 6f, paint)
            currentY += 20f
        }

        // 5. Medical Advice Disclaimer Footer
        paint.color = Color.rgb(245, 247, 248)
        canvas.drawRect(32f, 780f, 563f, 816f, paint)

        paint.color = Color.rgb(100, 115, 130)
        paint.textSize = 7.5f
        paint.typeface = Typeface.DEFAULT
        val disclaimer = "DISCLAIMER: Tulsi is an adherence self-tracking tool. This record is provided for informational and doctor discussion purposes only and does not constitute medical advice, diagnosis, or treatment. Always follow your prescribing physician's directions."
        canvas.drawText(disclaimer.take(130), 40f, 796f, paint)
        canvas.drawText(disclaimer.drop(130), 40f, 808f, paint)

        document.finishPage(page)

        // Save file to reports cache directory
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, "Tulsi_Adherence_Report.pdf")
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return file
    }
}

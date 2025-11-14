package com.example.prodtracker

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBPanel
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import javax.swing.Timer

class TrackerPanel(
    private val tracker: ActivityTracker
) : JBPanel<TrackerPanel>(), Disposable {

    private val repaintTimer: Timer

    init {
        preferredSize = Dimension(600, 140)
        repaintTimer = Timer(1000) { repaint() }
        repaintTimer.start()
    }

    // вывод статистики в консоль
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val snap = tracker.snapshot()

        // числовая статистика
        g.font = Font("Dialog", Font.PLAIN, 13)
        g.drawString("Edits: ${snap.edits}", 12, 20)
        g.drawString("Typing (s): ${snap.typingSeconds}", 160, 20)
        g.drawString("Viewed (s): ${snap.totalViewSeconds}", 340, 20)

        // гистограмма активности
        val list = snap.recentTypingSeconds
        if (list.isNotEmpty()) {
            val gx = 12
            val gy = 34
            val w = width - 24
            val h = 80
            val bucketW = if (list.size > 0) maxOf(1, w / list.size) else w
            val maxVal = list.maxOrNull() ?: 1L
            g.drawRect(gx, gy, w, h)
            for ((i, v) in list.withIndex()) {
                val x = gx + i * bucketW
                val barH = if (maxVal > 0) (v * h / maxVal).toInt() else 0
                g.fillRect(x, gy + (h - barH), bucketW - 2, barH)
            }
        } else {
            g.drawString("No typing data yet", 12, 60)
        }
    }

    // остановка работы
    override fun dispose() {
        repaintTimer.stop()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                tracker.stop()
            } catch (_: Throwable) {}
        }
    }
}
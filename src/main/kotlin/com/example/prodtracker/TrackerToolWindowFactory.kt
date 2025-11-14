package com.example.prodtracker

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.util.Disposer

class TrackerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // трекер
        val tracker = ActivityTracker(project)
        tracker.start()

        // вывод статистики
        val panel = TrackerPanel(tracker)

        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "Productivity Tracker", false)
        toolWindow.contentManager.addContent(content)

        Disposer.register(toolWindow.disposable, panel)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
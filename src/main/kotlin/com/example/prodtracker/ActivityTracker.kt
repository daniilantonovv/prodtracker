package com.example.prodtracker

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ActivityTracker(private val project: Project) {

    // подключение к шине сообщений проекта для подписки на события
    private val connection: MessageBusConnection = project.messageBus.connect()
    // получаем объект multiclasser для подписки на события редактора
    private val editorEventMulticaster = EditorFactory.getInstance().eventMulticaster

    // статистика
    // количество редактирований
    @Volatile
    var edits: Long = 0
    // секунды, проведённые за набором текста
    @Volatile
    var typingSeconds: Long = 0
    // время, проведённое в редакторе
    @Volatile
    var totalViewSeconds: Long = 0
    // момент последнего изменения текста
    private var lastEditTime: Instant? = null
    // момент последнего переключения между редакторами
    private var lastEditorSwitchTime: Instant? = null
    // очередь для хранения секунд набора текста за последние минуты
    private val recentTyping = ConcurrentLinkedDeque<Long>()
    // сколько интервалов мы хотим хранить
    private val BUCKET_COUNT = 60
    // максимальная пауза между редактированиями внутри одной сессии
    private val SESSION_GAP_SECONDS = 60L

    // лисенер изменений документе
    private val docListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            onDocumentChanged()
        }
    }
    // лисенер переключений файлов
    private val fileEditorListener = object : FileEditorManagerListener {
        override fun selectionChanged(event: FileEditorManagerEvent) {
            onEditorSwitched()
        }
    }
    // планировщик задач
    private var scheduler: ScheduledExecutorService? = null

    // запуск трекера активности
    fun start() {
        // подписываемся на изменения файла
        editorEventMulticaster.addDocumentListener(docListener, connection)
        // подписываемся на события смены файлов
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorListener)

        // время последнего переключения - текущий момент
        lastEditorSwitchTime = Instant.now()

        // однопоточный планировщик, который сохраняет количество секунд набора текста в очередь
        scheduler = ScheduledThreadPoolExecutor(1).apply {
            scheduleAtFixedRate({
                try {
                    flushMinuteBucket()
                } catch (t: Throwable) {

                }
            }, 60, 60, TimeUnit.SECONDS)
        }
    }

    // остановка трекера активности
    fun stop() {
        // отписываемся от событий, останавливаем планировщик и обнуляем ссылку на него
        try {
            connection.dispose()
        } finally {
            scheduler?.shutdownNow()
            scheduler = null
        }
    }

    // обработка событий изменения документа
    private fun onDocumentChanged() {
        val now = Instant.now()
        edits++
        synchronized(this) {
            val prev = lastEditTime
            if (prev == null) {
                lastEditTime = now
                typingSeconds += 1
            } else {
                val gap = Duration.between(prev, now).seconds
                if (gap <= SESSION_GAP_SECONDS) {
                    typingSeconds += max(1, gap)
                } else {
                    typingSeconds += 1
                }
                lastEditTime = now
            }
        }
    }

    // обработка переключений редактора
    private fun onEditorSwitched() {
        val now = Instant.now()
        val prev = lastEditorSwitchTime
        if (prev != null) {
            val spent = Duration.between(prev, now).seconds
            if (spent > 0) totalViewSeconds += spent
        }
        lastEditorSwitchTime = now
    }

    // сброс данных набора текста за минуту в очередь и удаление старых записей
    private fun flushMinuteBucket() {
        val sec = synchronized(this) {
            typingSeconds % 60
        }
        recentTyping.addLast(sec)
        while (recentTyping.size > BUCKET_COUNT) recentTyping.pollFirst()
    }

    // снепшот текущей статистики
    fun snapshot(): Snapshot {
        val recent = recentTyping.toList()
        return Snapshot(edits, typingSeconds, totalViewSeconds, recent)
    }

    // класс снепшота для статистики
    data class Snapshot(
        val edits: Long,
        val typingSeconds: Long,
        val totalViewSeconds: Long,
        val recentTypingSeconds: List<Long>
    )
}
package com.mindosc.freenux

import java.util.concurrent.CopyOnWriteArrayList

/** Κοινό log + κατάσταση ανάμεσα σε service και UI. */
object LogBus {
    private val lines = ArrayDeque<String>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile var state: String = "Σταματημένο"
        private set

    fun log(s: String) {
        synchronized(lines) {
            lines.addLast(s)
            while (lines.size > 400) lines.removeFirst()
        }
        fire()
    }

    fun setState(s: String) { state = s; fire() }

    fun text(): String = synchronized(lines) { lines.joinToString("\n") }

    fun add(l: () -> Unit) = listeners.add(l)
    fun remove(l: () -> Unit) = listeners.remove(l)
    private fun fire() = listeners.forEach { it() }
}

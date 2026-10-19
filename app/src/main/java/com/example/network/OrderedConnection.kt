package com.example.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** A single writer per TCP stream preserves the order of accepted game moves. */
internal class OrderedConnection(
    private val socket: Socket,
    private val scope: CoroutineScope,
    private val onMessage: suspend (String) -> Unit,
    private val onDisconnected: suspend () -> Unit
) {
    private val outgoing = Channel<String>(Channel.UNLIMITED)
    private val closed = AtomicBoolean(false)

    fun start() {
        socket.tcpNoDelay = true
        scope.launch(Dispatchers.IO) {
            try {
                val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                for (message in outgoing) {
                    writer.write(message)
                    writer.newLine()
                    writer.flush()
                }
            } catch (_: Exception) {
                disconnected()
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                while (!closed.get()) {
                    val message = reader.readLine() ?: break
                    onMessage(message)
                }
            } catch (_: Exception) {
                // Closing a socket interrupts readLine with an IOException.
            } finally {
                disconnected()
            }
        }
    }

    fun send(message: String): Boolean = !closed.get() && outgoing.trySend(message).isSuccess

    fun close() {
        closed.set(true)
        outgoing.cancel()
        // Close the socket first: closing a reader during readLine can deadlock.
        runCatching { socket.close() }
    }

    private suspend fun disconnected() {
        if (closed.compareAndSet(false, true)) {
            outgoing.cancel()
            runCatching { socket.close() }
            onDisconnected()
        }
    }
}

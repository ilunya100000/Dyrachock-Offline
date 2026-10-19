package com.example.network

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OrderedConnectionTest {
    @Test fun `five clients receive ordered private updates without cross delivery`(): Unit = runBlocking {
        val server = ServerSocket(0)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val connections = mutableListOf<OrderedConnection>()
        val clients = mutableListOf<Socket>()
        try {
            repeat(5) {
                val socket = Socket("127.0.0.1", server.localPort).apply { soTimeout = 10000 }
                clients += socket
                connections += OrderedConnection(server.accept(), scope, {}, {}).also { it.start() }
            }
            val reads = clients.mapIndexed { id, socket -> async(Dispatchers.IO) {
                val input = socket.getInputStream().bufferedReader()
                (0 until 200).map { input.readLine() }.also {
                    assertEquals((0 until 200).map { "private-$id-state-$it-Игрок" }, it)
                }
            } }
            repeat(200) { sequence -> connections.forEachIndexed { id, connection ->
                assertTrue(connection.send("private-$id-state-$sequence-Игрок"))
            } }
            reads.awaitAll()
        } finally {
            connections.forEach { it.close() }
            clients.forEach { it.close() }
            server.close()
            scope.cancel()
        }
    }

    @Test fun `peer EOF notifies exactly once and local close never blocks reader`() {
        val server = ServerSocket(0)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val client = Socket("127.0.0.1", server.localPort)
        val notified = CountDownLatch(1)
        val connection = OrderedConnection(server.accept(), scope, {}, { notified.countDown() })
        try {
            connection.start()
            client.close()
            assertTrue(notified.await(5, TimeUnit.SECONDS))
            connection.close()
            assertFalse(connection.send("after close"))
        } finally {
            connection.close()
            client.close()
            server.close()
            scope.cancel()
        }
    }
}

package actor.proto.tests

import actor.proto.*
import actor.proto.fixture.EmptyReceive
import actor.proto.fixture.TestMailbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ActorTests {
    private fun spawnActorFromFunc(receive: suspend Context.(msg: Any) -> Unit): PID = spawn(fromFunc(receive))
    @Test
    fun requestActorAsync(): Unit {
        val pid: PID = spawnActorFromFunc { msg ->
            when (msg) {
                is String -> respond("hey")
            }
        }

        runBlocking {
            val reply: Any = requestAwait(pid, "hello", Duration.ofMillis(200))
            assertEquals("hey", reply)
        }
    }

    @Test
    fun `request actor async should raise timeout exception when timeout is reached`(): Unit {
        val pid: PID = spawnActorFromFunc(EmptyReceive)
        assertFailsWith<CancellationException> {
            runBlocking {
                requestAwait<Any>(pid, "", Duration.ofMillis(10))
            }
        }
    }

    @Test
    fun `request actor async should not raise timeout exception when result is first`(): Unit {
        val pid: PID = spawnActorFromFunc { msg ->
            when (msg) {
                is String -> respond("hey")
            }
        }

        runBlocking {
            val reply: Any = requestAwait(pid, "hello", Duration.ofMillis(100))
            assertEquals("hey", reply)
        }
    }

    @Test
    fun actorLifeCycle(): Unit {
        val messages: MutableList<Any> = mutableListOf()
        val prop = fromFunc { msg ->
            messages.add(msg)
        }.withMailbox { TestMailbox() }
        val pid: PID = spawn(prop)
        send(pid, "hello")
        stop(pid)
        assertEquals(4, messages.count())

        assertSame(messages[0], Started)
        assertEquals(messages[1], "hello")
        assertSame(messages[2], Stopping)
        assertSame(messages[3], Stopped)
    }

    @Test
    fun actorStartedException() {
        val exceptionCount = CountDownLatch(2)
        val messageCount = CountDownLatch(8)
        val messages: MutableList<Any> = mutableListOf()
        val prop = fromFunc { msg ->
            messages.add(msg)
            messageCount.countDown()
            when (msg) {
                is Started -> {
                    val shouldThrow = exceptionCount.count > 0
                    exceptionCount.countDown()
                    if (shouldThrow) throw Exception()
                }
                else -> {

                }
            }
        }
        val pid: PID = spawn(prop)
        send(pid, "hello")
        exceptionCount.await()
        stop(pid)
        messageCount.await()
        assertEquals(8, messages.count())

        assertSame(messages[0], Started)
        assertSame(messages[1], Restarting)
        assertSame(messages[2], Started)
        assertSame(messages[3], Restarting)
        assertSame(messages[4], Started)
        assertEquals(messages[5], "hello")
        assertSame(messages[6], Stopping)
        assertSame(messages[7], Stopped)
    }
}


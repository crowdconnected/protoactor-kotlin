package actor.proto.router.tests

import actor.proto.*
import actor.proto.router.fixture.TestMailbox
import actor.proto.router.*
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BroadcastGroupTests {
    private val MyActorProps: Props = fromProducer { MyTestActor() }
    private val _timeout: Duration = Duration.ofMillis(1000)

    @Test fun `broadcast group router, all routees receive messages`() {
        runBlocking {
            val (router, routee1, routee2, routee3) = createBroadcastGroupRouterWith3Routees()
            router.send("hello")
            assertEquals("hello", routee1.requestAwait("received?", _timeout))
            assertEquals("hello", routee2.requestAwait("received?", _timeout))
            assertEquals("hello", routee3.requestAwait("received?", _timeout))
        }
    }

    @Test fun `broadcast group router, when one routee is stopped, all other routees receive messages`() {
        runBlocking {
            val (router, routee1, routee2, routee3) = createBroadcastGroupRouterWith3Routees()
            routee2.stop()
            router.send("hello")
            assertEquals("hello", routee1.requestAwait("received?", _timeout))
            assertEquals("hello", routee3.requestAwait("received?", _timeout))
        }
    }

    @Test fun `broadcast group router, when one routee is slow, all other routees receive messages`() {
        runBlocking {
            val (router, routee1, routee2, routee3) = createBroadcastGroupRouterWith3Routees()
            routee2.send("go slow")
            router.send("hello")
            assertEquals("hello", routee1.requestAwait("received?", _timeout))
            assertEquals("hello", routee3.requestAwait("received?", _timeout))
        }
    }

    @Test fun `broadcast group router, routees can be removed`() {
        runBlocking {
            val (router, routee1, routee2, routee3) = createBroadcastGroupRouterWith3Routees()
            router.send(RouterRemoveRoutee(routee1))
            val routees = router.requestAwait<Routees>(RouterGetRoutees, _timeout)
            assertFalse(routees.pids.contains(routee1))
            assertTrue(routees.pids.contains(routee2))
            assertTrue(routees.pids.contains(routee3))
        }
    }

    @Test fun `broadcast group router, routees can be added`() {
        runBlocking {
            val (router, routee1, routee2, routee3) = createBroadcastGroupRouterWith3Routees()
            val routee4 = spawn(MyActorProps)
            router.send(RouterAddRoutee(routee4))
            val routees = router.requestAwait<Routees>(RouterGetRoutees, _timeout)
            assertTrue(routees.pids.contains(routee1))
            assertTrue(routees.pids.contains(routee2))
            assertTrue(routees.pids.contains(routee3))
            assertTrue(routees.pids.contains(routee4))
        }
    }

    @Test fun `broadcast group router, removed routees no longer receive messages`() {
        runBlocking {
            val (router, routee1, routee2, routee3) = createBroadcastGroupRouterWith3Routees()
            router.send("first message")
            router.send(RouterRemoveRoutee(routee1))
            router.send("second message")
            assertEquals("first message", routee1.requestAwait("received?", _timeout))
            assertEquals("second message", routee2.requestAwait("received?", _timeout))
            assertEquals("second message", routee3.requestAwait("received?", _timeout))
        }
    }

    @Test fun `broadcast group router, added routees receive messages`() {
        runBlocking {
            val (router, routee1, routee2, routee3) = createBroadcastGroupRouterWith3Routees()
            val routee4 = spawn(MyActorProps)
            router.send(RouterAddRoutee(routee4))
            router.send("a message")
            assertEquals("a message", routee1.requestAwait("received?", _timeout))
            assertEquals("a message", routee2.requestAwait("received?", _timeout))
            assertEquals("a message", routee3.requestAwait("received?", _timeout))
            assertEquals("a message", routee4.requestAwait("received?", _timeout))
        }
    }

    @Test fun `broadcast group router, all routees receive router broadcast messages`() {
        runBlocking {
            val (router, routee1, routee2, routee3) = createBroadcastGroupRouterWith3Routees()
            router.send(RouterBroadcastMessage("hello"))
            delay(100, TimeUnit.MILLISECONDS)
            assertEquals("hello", routee1.requestAwait("received?", _timeout))
            assertEquals("hello", routee2.requestAwait("received?", _timeout))
            assertEquals("hello", routee3.requestAwait("received?", _timeout))
        }
    }

    private fun createBroadcastGroupRouterWith3Routees(): Array<PID> {
        val routee1 = spawn(MyActorProps)
        val routee2 = spawn(MyActorProps)
        val routee3 = spawn(MyActorProps)
        val props = newBroadcastGroup(setOf(routee1, routee2, routee3)).withMailbox { TestMailbox() }
        val router = spawn(props)
        return arrayOf(router, routee1, routee2, routee3)
    }

    open internal class MyTestActor : Actor {
        private lateinit var _received: String
        suspend override fun receive(context: Context) {
            val tmp = context.message
            when (tmp) {
                "received?" -> context.respond(_received)
                "go slow" -> delay(5000)
                is String -> _received = tmp
            }
        }
    }
}
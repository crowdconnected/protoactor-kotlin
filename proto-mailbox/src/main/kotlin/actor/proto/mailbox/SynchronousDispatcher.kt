package actor.proto.mailbox

import kotlinx.coroutines.runBlocking

class SynchronousDispatcher(override var throughput: Int = 300) : Dispatcher {
    override fun schedule(mailbox:Mailbox) = runBlocking { mailbox.run() }
}
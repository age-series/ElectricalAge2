import org.eln2.mc.common.events.EventManager
import org.eln2.mc.common.events.Event
import org.eln2.mc.common.events.registerHandler
import org.eln2.mc.common.events.unregisterHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.util.concurrent.ConcurrentLinkedQueue

class EventManagerTests {
    private class MyEvent1 : Event
    private class MyEvent2 : Event

    @Test
    fun testEvents(){
        var eventsRegistered = true

        var receiveCount1 = 0
        var receiveCount2 = 0

        fun handler1(event: MyEvent1){
            if(!eventsRegistered){
                fail("Received event after handler was removed")
            }

            receiveCount1++
        }

        fun handler2(event: MyEvent2){
            if(!eventsRegistered){
                fail("Received event after handler was removed")
            }

            receiveCount2++
        }

        val manager = EventManager()

        manager.registerHandler(::handler1)
        manager.registerHandler(::handler2)

        repeat(100){
            manager.send(MyEvent1())
        }

        assert(receiveCount1 == 100 && receiveCount2 == 0)

        repeat(50){
            manager.send(MyEvent2())
        }

        assert(receiveCount1 == 100 && receiveCount2 == 50)

        manager.unregisterHandler(::handler1)
        manager.unregisterHandler(::handler2)

        eventsRegistered = false

        repeat(50){
            manager.send(MyEvent1())
            manager.send(MyEvent2())
        }

        assert(receiveCount1 == 100 && receiveCount2 == 50)
    }

    class MyTestEventParallel(val x: Int): Event

    @Test
    fun testParallel(){
        val manager = EventManager()

        val toSend = (1..1000).map { MyTestEventParallel(it) }
        val received = ConcurrentLinkedQueue<MyTestEventParallel>()

        manager.registerHandler<MyTestEventParallel>{ received.add(it) }

        val sendQueue = ConcurrentLinkedQueue(toSend)
        val threads = (1..10).map{
            return@map Thread {
                while (true){
                    val event = sendQueue.poll()
                        ?: return@Thread

                    manager.send(event)

                    Thread.sleep(1)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assert(received.sortedBy { it.x } == toSend)
    }
}

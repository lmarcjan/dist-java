import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.japi.pf.ReceiveBuilder
import akka.pattern.Patterns.ask
import akka.util.Timeout
import scala.concurrent.Await
import java.util.concurrent.TimeUnit

fun main() {

    data class InitActor(val neighbourProcs: List<ActorRef>)
    class InitActorCompleted
    class Start

    class KnotDetectActor : AbstractLoggingActor() {

        private var neighbours: List<ActorRef> = emptyList()

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(Start::class.java) { handleStart(it) }
                        .build()

        fun handleInitActor(init: InitActor) {
            log().debug("Received {}", init)
            this.neighbours = init.neighbourProcs
            sender.tell(InitActorCompleted(), self())
        }

        fun handleStart(start: Start) {
            log().debug("Received {}", start)
            log().debug("Neighbours are {}", neighbours)
        }
    }

    val system = ActorSystem.create("KnotDetectSystem")
    val a1 = system.actorOf(Props.create(KnotDetectActor::class.java), "1")
    val a2 = system.actorOf(Props.create(KnotDetectActor::class.java), "2")
    val a3 = system.actorOf(Props.create(KnotDetectActor::class.java), "3")
    val a4 = system.actorOf(Props.create(KnotDetectActor::class.java), "4")
    val a5 = system.actorOf(Props.create(KnotDetectActor::class.java), "5")
    val a6 = system.actorOf(Props.create(KnotDetectActor::class.java), "6")
    val a7 = system.actorOf(Props.create(KnotDetectActor::class.java), "7")
    val a8 = system.actorOf(Props.create(KnotDetectActor::class.java), "8")
    val a9 = system.actorOf(Props.create(KnotDetectActor::class.java), "9")
    val a10 = system.actorOf(Props.create(KnotDetectActor::class.java), "10")
    val a11 = system.actorOf(Props.create(KnotDetectActor::class.java), "11")

    val timeout = Timeout(5, TimeUnit.SECONDS)

    val graph = mapOf(
            a1 to listOf(a6),
            a2 to listOf(a1, a3),
            a3 to listOf(a5),
            a4 to listOf(a2, a3),
            a5 to listOf(a8),
            a6 to listOf(a3, a5, a9),
            a7 to listOf(a4, a8),
            a8 to listOf(a6),
            a9 to listOf(a5, a8),
            a10 to listOf(a7, a8),
            a11 to listOf(a6, a10)
    )

    graph.forEach { node, nbs ->
        val future = ask(node, InitActor(nbs), timeout.duration().toMillis())
        Await.result(future, timeout.duration())
    }

    a6.tell(Start(), ActorRef.noSender())

    Thread.sleep(timeout.duration().toMillis())

    system.terminate()

}
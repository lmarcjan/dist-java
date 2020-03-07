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

    class InitActor(val neighbourProcs: List<ActorRef>)
    class InitActorCompleted
    class Start
    class GoDetect
    class CycleBack
    class ParentBack
    class SeenBack

    class KnotDetectActor : AbstractLoggingActor() {

        private var waiting_from: List<ActorRef> = emptyList()
        private var in_cycle: List<ActorRef> = emptyList()
        private var parent: ActorRef? = null

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(Start::class.java) { handleStart(it) }
                        .match(GoDetect::class.java) { handleGoDetect(it) }
                        .build()

        fun handleInitActor(init: InitActor) {
            log().debug("Received init actor at {} from {}", self().path().name(), sender.path().name())
            this.waiting_from = init.neighbourProcs
            sender.tell(InitActorCompleted(), self())
        }

        fun handleStart(start: Start) {
            log().info("Received start at {} from {}", self().path().name(), sender.path().name())
            if (waiting_from.isEmpty()) {
                log().info("no knot")
            } else {
                parent = sender()
                waiting_from.forEach {
                    it.tell(GoDetect(), self())
                }
            }
        }

        fun handleGoDetect(goDetect: GoDetect) {
            log().info("Received go detect at {} from {}", self().path().name(), sender.path().name())
            if (parent == self()) {
                sender.tell(CycleBack(), self())
            } else {
                if (parent == null) {
                    parent = sender()
                    if (waiting_from.isEmpty()) {
                        sender.tell(ParentBack(), self())
                    } else {
                        waiting_from.forEach {
                            it.tell(GoDetect(), self())
                        }
                    }
                } else {
                    if (in_cycle.isEmpty()) {
                        sender.tell(CycleBack(), self())
                    } else {
                        sender.tell(SeenBack(), self())
                    }
                }
            }

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
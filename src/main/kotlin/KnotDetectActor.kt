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
    class SeenBack
    class CycleBack
    class ParentBack(val seen: List<Pair<ActorRef, ActorRef>>, val in_cycle: List<ActorRef>)

    class KnotDetectActor : AbstractLoggingActor() {

        private var waiting_from: MutableList<ActorRef> = mutableListOf()
        private var in_cycle: MutableList<ActorRef> = mutableListOf()
        private var seen: MutableList<Pair<ActorRef, ActorRef>> = mutableListOf()
        private var parent: ActorRef? = null

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(Start::class.java) { handleStart(it) }
                        .match(GoDetect::class.java) { handleGoDetect(it) }
                        .match(SeenBack::class.java) { handleSeenBack(it) }
                        .match(CycleBack::class.java) { handleCycleBack(it) }
                        .match(ParentBack::class.java) { handleParentBack(it) }
                        .build()

        fun handleInitActor(init: InitActor) {
            log().debug("Received init actor at {} from {}", self().path().name(), sender.path().name())
            this.waiting_from = init.neighbourProcs.toMutableList()
            sender.tell(InitActorCompleted(), self())
        }

        fun handleStart(start: Start) {
            log().info("Received start at {} from {}", self().path().name(), sender.path().name())
            if (waiting_from.isEmpty()) {
                log().info("no knot at {}", self().path().name())
            } else {
                parent = self()
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
                        sender.tell(ParentBack(seen, in_cycle), self())
                    } else {
                        waiting_from.forEach {
                            it.tell(GoDetect(), self())
                        }
                    }
                } else {
                    if (!in_cycle.isEmpty()) {
                        sender.tell(CycleBack(), self())
                    } else {
                        sender.tell(SeenBack(), self())
                    }
                }
            }
        }

        fun handleCycleBack(cycleBack: CycleBack) {
            log().info("Received cycle back at {} from {}", self().path().name(), sender.path().name())
            waiting_from.remove(sender)
            in_cycle.add(sender)
            check_waiting_from()
        }

        fun handleSeenBack(seenBack: SeenBack) {
            log().info("Received seen back at {} from {}", self().path().name(), sender.path().name())
            waiting_from.remove(sender)
            seen.add(Pair(self, sender))
            check_waiting_from()
        }

        fun handleParentBack(parentBack: ParentBack) {
            log().info("Received parent back at {} from {}", self().path().name(), sender.path().name())
            waiting_from.remove(sender)
            seen.addAll(parentBack.seen)
            if (in_cycle.isEmpty()) {
                seen.add(Pair(self(), sender))
            } else {
                in_cycle.addAll(parentBack.in_cycle)
            }
            check_waiting_from()
        }

        fun check_waiting_from() {
            if (waiting_from.isEmpty()) {
                log().info("Check waiting from at {}", self().path().name())
                if (parent == self()) {
                    var candidates: MutableList<ActorRef> = mutableListOf()
                    in_cycle.forEach { k ->
                        in_cycle.remove(k)
                        candidates.add(k)
                        seen.forEach { p ->
                            if (p.second == k) {
                                in_cycle.add(p.first)
                                seen.remove(p)
                            }
                        }
                    }
                    if (seen.isEmpty()) {
                        log().info("knot at {} with {}", self().path().name(), candidates.map { c -> c.path().name() })
                    } else {
                        log().info("no knot at {}", self().path().name())
                    }
                } else {
                    if (!in_cycle.isEmpty()) {
                        in_cycle.add(self())
                    }
                    parent!!.tell(ParentBack(seen, in_cycle), self())
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
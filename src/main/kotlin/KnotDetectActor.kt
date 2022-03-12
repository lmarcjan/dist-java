import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.japi.pf.ReceiveBuilder
import akka.pattern.Patterns.ask
import akka.util.Timeout
import scala.concurrent.Await
import java.io.File
import java.util.concurrent.TimeUnit

fun main() {

    // messages
    data class InitActor(val neighbourProcs: List<ActorRef>)
    class InitActorCompleted
    class Start
    class GoDetect
    class SeenBack
    class CycleBack
    data class ParentBack(val seen: List<Pair<ActorRef, ActorRef>>, val in_cycle: List<ActorRef>)

    // message timeout
    val timeout = Timeout(5, TimeUnit.SECONDS)

    class KnotDetectActor : AbstractLoggingActor() {

        private lateinit var waiting_from: MutableList<ActorRef>
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
            log().info("Received init actor {} at {} from {}", init, self().path().name(), sender.path().name())
            this.waiting_from = init.neighbourProcs.toMutableList()
            sender.tell(InitActorCompleted(), self())
        }

        fun handleStart(start: Start) {
            log().info("Received start {} at {} from {}", start, self().path().name(), sender.path().name())
            if (waiting_from.isEmpty()) {
                log().info("No knot at {}", self().path().name())
            } else {
                log().info("Setting root at {}", self().path().name())
                parent = self()
                waiting_from.forEach {
                    it.tell(GoDetect(), self())
                }
            }
        }

        fun handleGoDetect(goDetect: GoDetect) {
            log().info("Received go detect {} at {} from {}", goDetect, self().path().name(), sender.path().name())
            if (parent == self()) {
                sender.tell(CycleBack(), self())
            } else {
                if (parent == null) {
                    log().info("Setting parent at {} from {}", self().path().name(), sender.path().name())
                    parent = sender()
                    if (waiting_from.isEmpty()) {
                        sender.tell(ParentBack(seen, in_cycle), self())
                    } else {
                        waiting_from.forEach {
                            it.tell(GoDetect(), self())
                        }
                    }
                } else {
                    if (in_cycle.isNotEmpty()) {
                        sender.tell(CycleBack(), self())
                    } else {
                        sender.tell(SeenBack(), self())
                    }
                }
            }
        }

        fun handleCycleBack(cycleBack: CycleBack) {
            log().info("Received cycle back {} at {} from {}", cycleBack, self().path().name(), sender.path().name())
            waiting_from.remove(sender)
            in_cycle.add(sender)
            check_waiting_from()
        }

        fun handleSeenBack(seenBack: SeenBack) {
            log().info("Received seen back {} at {} from {}", seenBack, self().path().name(), sender.path().name())
            waiting_from.remove(sender)
            seen.add(Pair(self(), sender))
            check_waiting_from()
        }

        fun handleParentBack(parentBack: ParentBack) {
            log().info("Received parent back {} at {} from {}", parentBack, self().path().name(), sender.path().name())
            waiting_from.remove(sender)
            seen.addAll(parentBack.seen)
            if (parentBack.in_cycle.isEmpty()) {
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
                    in_cycle.toList().forEach { k ->
                        in_cycle.remove(k)
                        candidates.add(k)
                        seen.toList().forEach { p ->
                            if (p.second == k) {
                                in_cycle.add(p.first)
                                seen.remove(p)
                            }
                        }
                    }
                    if (seen.isEmpty()) {
                        log().info("Knot at {} with {}", self().path().name(), candidates.map { c -> c.path().name() })
                    } else {
                        log().info("No knot at {}", self().path().name())
                    }
                } else {
                    if (in_cycle.isNotEmpty()) {
                        in_cycle.add(self())
                    }
                    parent?.tell(ParentBack(seen, in_cycle), self())
                }
            }
        }

    }

    val system = ActorSystem.create("KnotDetectSystem")
    val adj = GraphUtil.readGraphAdj(File(KnotDetectActor::class.java.getResource("graph-knot-detect.adj").file))
    val n = adj.size
    val actors = mutableMapOf<Int, ActorRef>()
    for (i in 1 until n + 1) {
        val actor = system.actorOf(Props.create(KnotDetectActor::class.java), "" + i)
        actors.put(i, actor)
    }

    val graph = mutableMapOf<ActorRef, List<ActorRef>>()
    for ((i, actorRef) in actors) {
        val actorAdj = adj.get(i - 1).map { actors.get(it + 1) }.filterNotNull()
        graph.put(actorRef, actorAdj)
    }
    
    graph.forEach { node, nbs ->
        val future = ask(node, InitActor(nbs), timeout.duration().toMillis())
        Await.result(future, timeout.duration())
    }

    actors.get(6)?.tell(Start(), ActorRef.noSender())

    Thread.sleep(timeout.duration().toMillis())

    system.terminate()

}
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

    // messages
    data class InitActor(val allActors: List<ActorRef>)
    class InitActorCompleted
    data class InitRing(val prdc: ActorRef, val succList: List<ActorRef>)
    class InitRingCompleted
    class InitJoin
    data class Join(val id: Int)
    class JoinAccepted
    class Fail

    // message timeout
    val timeout = Timeout(5, TimeUnit.SECONDS)

    class ChordActor : AbstractLoggingActor() {

        private lateinit var allActors: MutableList<ActorRef>
        private var id: Int = getId(self())
        private var prdc: ActorRef? = null
        private lateinit var succList: MutableList<ActorRef>

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(InitRing::class.java) { handleInitRing(it) }
                        .match(Join::class.java) { handleJoin(it) }
                        .match(JoinAccepted::class.java) { handleJoinAccepted(it) }
                        .match(InitJoin::class.java) { handleInitJoin(it) }
                        .match(Fail::class.java) { handleFail(it) }
                        .build()

        private fun handleJoinAccepted(joinAccepted: JoinAccepted) {
            log().info("Join accepted at {} from {}", self().path().name(), sender.path().name())
            this.prdc = sender
        }

        fun handleInitActor(init: InitActor) {
            log().debug("Received init actor {} at {} from {}", init, self().path().name(), sender.path().name())
            this.allActors = init.allActors.toMutableList()
            sender.tell(InitActorCompleted(), self())
        }

        private fun handleInitJoin(initJoin: InitJoin) {
            allActors.forEach { a ->
                a.tell(Join(id), self())
            }
        }

        private fun handleJoin(join: Join) {
            if (inRing() && between(id, join.id, getId(succList.get(0)))) {
                sender.tell(JoinAccepted(), self())
            }
        }

        fun handleInitRing(init: InitRing) {
            log().info("Received init ring {} at {} from {}", init, self().path().name(), sender.path().name())
            this.succList = init.succList.toMutableList()
            this.prdc = init.prdc
            sender.tell(InitRingCompleted(), self())
        }

        fun handleFail(fail: Fail) {
            log().info("Received fail {} at {} from {}", fail, self().path().name(), sender.path().name())
            context.stop(self);
        }

        private fun getId(actor: ActorRef): Int {
            return actor.path().name().toInt()
        }

        private fun inRing(): Boolean {
            return prdc != null
        }

        fun between(n1: Int, nb: Int, n2: Int): Boolean {
            if (n1 < n2) return (n1 < nb && nb < n2)
            else return (n1 < nb || nb < n2)
        }

    }

    val system = ActorSystem.create("ChordSystem")
    val n = 10
    val actors = mutableMapOf<Int, ActorRef>()
    for (i in 0 until n) {
        val actor = system.actorOf(Props.create(ChordActor::class.java), "" + i)
        actors.put(i, actor)
    }

    actors.values.forEach { node ->
        val future = ask(node, InitActor(actors.values.toList()), timeout.duration().toMillis())
        Await.result(future, timeout.duration())
    }

    // let even actors crate a ring
    for (i in 0 until n step 2) {
        val prdc = actors.get((n + i - 2) % n)!!
        val succList = listOf(actors.get((i + 2) % n)!!, actors.get((i + 4) % n)!!)
        val future = ask(actors.get(i), InitRing(prdc, succList), timeout.duration().toMillis())
        Await.result(future, timeout.duration())
    }

    // let actor 1 join ring
    actors.get(1)?.tell(InitJoin(), ActorRef.noSender())

    // let actor 0 fail
    //actors.get(0)?.tell(Fail(), ActorRef.noSender())

    Thread.sleep(timeout.duration().toMillis())

    system.terminate()

}
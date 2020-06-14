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

    class InitActor(val id: Int, val neighbourProcs: List<ActorRef>)
    class InitActorCompleted
    class Start
    class Proposal(val epoch: Int, val v: Int)

    class PaxosActor : AbstractLoggingActor() {

        private lateinit var acceptors: List<ActorRef>
        private lateinit var learners: List<ActorRef>
        private var id: Int = 0
        private var epoch: Int = 0

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(Start::class.java) { handleStart(it) }
                        .match(Proposal::class.java) { handleProposal(it) }
                        .build()

        fun handleInitActor(init: InitActor) {
            log().debug("Received init actor at {} from {}", self().path().name(), sender.path().name())
            this.id = init.id
            this.acceptors = init.neighbourProcs
            this.learners = init.neighbourProcs
            sender.tell(InitActorCompleted(), self())
        }

        fun handleStart(start: Start) {
            log().debug("Received start at {} from {}", self().path().name(), sender.path().name())
            acceptors.forEach {
                it.tell(Proposal(epoch, id), self())
            }

        }

        fun handleProposal(proposal: Proposal) {
            log().info("Received proposal {} at {} from {}", proposal.v, self().path().name(), sender.path().name())
            // TODO
        }

    }

    val system = ActorSystem.create("BfsSystem")
    val n = 10
    val actors = mutableMapOf<Int, ActorRef>()
    for (i in 1 until n + 1) {
        val actor = system.actorOf(Props.create(PaxosActor::class.java), "" + i)
        actors.put(i, actor)
    }

    val graph = mutableMapOf<ActorRef, List<ActorRef>>()
    for ((i, actorRef) in actors) {
        val actorAdj = 1.rangeTo(n).map { actors.get(it + 1) }.filterNotNull()
        graph.put(actorRef, actorAdj)
    }


    val timeout = Timeout(5, TimeUnit.SECONDS)

    graph.forEach { node, nbs ->
        val future = ask(node, InitActor(node.path().name().toInt(), nbs), timeout.duration().toMillis())
        Await.result(future, timeout.duration())
    }

    actors.keys.forEach { a -> actors.get(a)?.tell(Start(), ActorRef.noSender()) }

    Thread.sleep(timeout.duration().toMillis())

    system.terminate()

}
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

    data class Proposal(val epoch: Int, val value: Int)
    data class InitActor(val id: Int, val neighbourProcs: List<ActorRef>)
    class InitActorCompleted
    class Start
    data class Prepare(val proposal: Proposal)
    data class PrepareResponse(val proposal: Proposal)

    class PaxosActor : AbstractLoggingActor() {

        private lateinit var acceptors: List<ActorRef>
        private lateinit var learners: List<ActorRef>
        private var id: Int = 0
        private var currentEpoch: Int = 0
        private var currentProposal: Proposal? = null

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(Start::class.java) { handleStart(it) }
                        .match(Prepare::class.java) { handlePrepare(it) }
                        .match(PrepareResponse::class.java) { handlePrepareResponse(it) }
                        .build()

        fun handleInitActor(init: InitActor) {
            log().debug("Received init actor {} at {} from {}", init, self().path().name(), sender.path().name())
            this.id = init.id
            this.acceptors = init.neighbourProcs
            this.learners = init.neighbourProcs
            sender.tell(InitActorCompleted(), self())
        }

        fun handleStart(start: Start) {
            log().debug("Received start {} at {} from {}", start, self().path().name(), sender.path().name())
            acceptors.forEach {
                it.tell(Prepare(Proposal(currentEpoch, id)), self())
            }
        }

        fun handlePrepare(prepare: Prepare) {
            log().debug("Received prepare {} at {} from {}", prepare, self().path().name(), sender.path().name())
            if (prepare.proposal.epoch < currentEpoch) {
                // ignore
            } else {
                if (currentProposal != null) {
                    sender.tell(PrepareResponse(currentProposal!!), self())
                    if (prepare.proposal.epoch > currentEpoch) {
                        currentProposal = prepare.proposal
                    }
                } else {
                    sender.tell(PrepareResponse(prepare.proposal), self())
                    currentProposal = prepare.proposal
                }
                currentEpoch = Math.max(currentEpoch, prepare.proposal.epoch)
            }
        }

        fun handlePrepareResponse(prepareResponse: PrepareResponse) {
            log().debug("Received prepare response {} at {} from {}", prepareResponse, self().path().name(), sender.path().name())
            // TODO
        }
    }

    val system = ActorSystem.create("PaxosSystem")
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
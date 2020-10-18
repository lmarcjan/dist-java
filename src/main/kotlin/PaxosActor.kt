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
    data class InitActor(val id: Int, val neighbourProcs: List<ActorRef>, val n: Int)
    class InitActorCompleted
    class Start
    data class PrepareRequest(val time: Int, val value: Int)
    data class PrepareResponse(val time: Int, val value: Int)
    data class AcceptRequest(val time: Int, val value: Int)
    data class Accepted(val value: Int)

    // message timeout
    val timeout = Timeout(5, TimeUnit.SECONDS)

    class PaxosActor : AbstractLoggingActor() {

        private var n: Int = 0
        private lateinit var acceptors: List<ActorRef>
        private lateinit var learners: List<ActorRef>

        private var proposerValue: Int = -1
        private var proposerTime: Int = -1
        private var proposerResponseCount: Int = 0
        private var proposerResponseValue: Int = -1
        private var proposerResponseTime: Int = -1
        private var proposerAcceptRequested = false;
        private var acceptorTime: Int = -1
        private var acceptorValue: Int = -1
        private var learnerAcceptedCount: Int = 0
        private var learnerAcceptedValue: Int = -1
        private var learnerFinished = false;

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(Start::class.java) { handleStart(it) }
                        .match(PrepareRequest::class.java) { handlePrepareRequest(it) }
                        .match(PrepareResponse::class.java) { handlePrepareResponse(it) }
                        .match(AcceptRequest::class.java) { handleAcceptRequest(it) }
                        .match(Accepted::class.java) { handleAccepted(it) }
                        .build()

        fun handleInitActor(init: InitActor) {
            log().debug("Received init actor {} at {} from {}", init, self().path().name(), sender.path().name())
            n = init.n
            acceptors = init.neighbourProcs
            learners = init.neighbourProcs
            proposerTime = 0
            proposerValue = (1..n).random()
            sender.tell(InitActorCompleted(), self())
        }

        fun handleStart(start: Start) {
            log().debug("Received start {} at {} from {}", start, self().path().name(), sender.path().name())
            acceptors.forEach {
                it.tell(PrepareRequest(proposerTime, proposerValue), self())
            }
        }

        fun handlePrepareRequest(prepareRequest: PrepareRequest) {
            log().debug("Received prepare request {} at {} from {}", prepareRequest, self().path().name(), sender.path().name())
            if (prepareRequest.time < acceptorTime) {
                // ignore
            } else {
                if (acceptorTime != -1) {
                    sender.tell(PrepareResponse(acceptorTime, acceptorValue), self())
                    if (prepareRequest.time > acceptorTime) {
                        acceptorValue = prepareRequest.value
                    }
                } else {
                    sender.tell(PrepareResponse(prepareRequest.time, prepareRequest.value), self())
                    acceptorValue = prepareRequest.value
                }
                acceptorTime = prepareRequest.time
            }
        }

        fun handlePrepareResponse(prepareResponse: PrepareResponse) {
            log().debug("Received prepare response {} at {} from {}", prepareResponse, self().path().name(), sender.path().name())
            if (prepareResponse.time < proposerResponseTime) {
                // ignore
            } else {
                proposerResponseCount++
                proposerResponseValue = prepareResponse.value
                proposerResponseTime = prepareResponse.time
                if (!proposerAcceptRequested && proposerResponseCount >= n / 2) {
                    acceptors.forEach {
                        it.tell(AcceptRequest(proposerResponseTime, proposerResponseValue), self())
                    }
                    proposerAcceptRequested = true
                }
            }
        }

        fun handleAcceptRequest(acceptRequest: AcceptRequest) {
            log().debug("Received accept request {} at {} from {}", acceptRequest, self().path().name(), sender.path().name())
            if (acceptRequest.time < acceptorTime) {
                // ignore
            } else {
                learners.forEach {
                    it.tell(Accepted(proposerResponseValue), self())
                }
            }
        }

        fun handleAccepted(accepted: Accepted) {
            log().debug("Received accepted {} at {} from {}", accepted, self().path().name(), sender.path().name())
            learnerAcceptedCount++
            learnerAcceptedValue = accepted.value
            if (!learnerFinished && learnerAcceptedCount >= n / 2) {
                log().info("Learnerd value {} at {}", learnerAcceptedValue, self().path().name())
                learnerFinished = true
            }
        }


    }

    val system = ActorSystem.create("PaxosSystem")
    val n = 12
    val actors = mutableMapOf<Int, ActorRef>()
    for (i in 1 until n + 1) {
        val actor = system.actorOf(Props.create(PaxosActor::class.java), "" + i)
        actors.put(i, actor)
    }

    val graph = mutableMapOf<ActorRef, List<ActorRef>>()
    for ((i, actorRef) in actors) {
        val actorAdj = 0.rangeTo(n).map { actors.get(it + 1) }.filterNotNull()
        graph.put(actorRef, actorAdj)
    }

    graph.forEach { node, nbs ->
        val nodeId = node.path().name().toInt()
        val future = ask(node, InitActor(nodeId, nbs, n), timeout.duration().toMillis())
        Await.result(future, timeout.duration())
    }

    actors.keys.forEach { a -> actors.get(a)?.tell(Start(), ActorRef.noSender()) }

    Thread.sleep(timeout.duration().toMillis())

    system.terminate()

}
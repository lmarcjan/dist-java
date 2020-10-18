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

    data class InitActor(val prdc: ActorRef, val succList: List<ActorRef>)
    class InitActorCompleted
    class Fail

    class ChordActor : AbstractLoggingActor() {

        private var prdc: ActorRef? = null
        private lateinit var succList: MutableList<ActorRef>

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(Fail::class.java) { handleFail(it) }
                        .build()

        fun handleInitActor(init: InitActor) {
            log().info("Received init actor {} at {} from {}", init, self().path().name(), sender.path().name())
            this.succList = init.succList.toMutableList()
            this.prdc = init.prdc
            sender.tell(InitActorCompleted(), self())
        }

        fun handleFail(fail: Fail) {
            log().info("Received fail {} at {} from {}", fail, self().path().name(), sender.path().name())
            context.stop(self);
        }

    }

    val system = ActorSystem.create("ChordSystem")
    val n = 10
    val actors = mutableMapOf<Int, ActorRef>()
    for (i in 0 until n step 2) {
        val actor = system.actorOf(Props.create(ChordActor::class.java), "" + i)
        actors.put(i, actor)
    }

    val timeout = Timeout(5, TimeUnit.SECONDS)

    for (i in 0 until n step 2) {
        val prdc = actors.get((n + i - 2) % n)!!
        val succList = listOf(actors.get((i + 2) % n)!!, actors.get((i + 4) % n)!!)
        val future = ask(actors.get(i), InitActor(prdc, succList), timeout.duration().toMillis())
        Await.result(future, timeout.duration())
    }

    actors.get(0)?.tell(Fail(), ActorRef.noSender())

    Thread.sleep(timeout.duration().toMillis())

    system.terminate()

}
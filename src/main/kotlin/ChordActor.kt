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

    data class InitActor(val successor: ActorRef)
    class InitActorCompleted
    class Fail

    class ChordActor : AbstractLoggingActor() {

        private var successor: ActorRef? = null

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(Fail::class.java) { handleFail(it) }
                        .build()

        fun handleInitActor(init: InitActor) {
            log().info("Received init actor {} at {} from {}", init, self().path().name(), sender.path().name())
            this.successor = init.successor
            sender.tell(InitActorCompleted(), self())
        }

        fun handleFail(fail: Fail) {
            log().info("Received fail {} at {} from {}", fail, self().path().name(), sender.path().name())
            context.stop(self);
        }

    }

    val system = ActorSystem.create("ChordSystem")
    val n = 12
    val actors = mutableMapOf<Int, ActorRef>()
    for (i in 0 until n) {
        val actor = system.actorOf(Props.create(ChordActor::class.java), "" + i)
        actors.put(i, actor)
    }

    val timeout = Timeout(5, TimeUnit.SECONDS)

    for (i in 0 until n) {
        val actorSucc = actors.get((i + 1) % n)!!
        val future = ask(actors.get(i), InitActor(actorSucc), timeout.duration().toMillis())
        Await.result(future, timeout.duration())
    }

    actors.get(0)?.tell(Fail(), ActorRef.noSender())

    Thread.sleep(timeout.duration().toMillis())

    system.terminate()

}
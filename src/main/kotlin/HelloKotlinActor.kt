import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.japi.pf.ReceiveBuilder

fun main() {

    open class Event
    class Event1 : Event()
    class Event2 : Event()

    class HelloKotlinActor : AbstractLoggingActor() {

        override fun createReceive() =
                ReceiveBuilder()
                        .match(Event1::class.java) { log().info("Received Event 1") }
                        .match(Event2::class.java) { log().info("Received Event 2") }
                        .build()
    }

    val actorSystem = ActorSystem.create("HelloKotlin")
    val actorRef1 = actorSystem.actorOf(Props.create(HelloKotlinActor::class.java), "Actor1")
    val actorRef2 = actorSystem.actorOf(Props.create(HelloKotlinActor::class.java), "Actor2")

    actorRef1.tell(Event1(), ActorRef.noSender())
    actorRef2.tell(Event2(), ActorRef.noSender())

    actorSystem.terminate()
}
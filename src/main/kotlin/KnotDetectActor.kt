import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.japi.pf.ReceiveBuilder

fun main() {

    data class InitActor(val neighbourProcs: List<ActorRef>)
    class InitActorCompleted
    class Start

    class KnotDetectActor : AbstractLoggingActor() {

        private var neighbours: List<ActorRef> = emptyList()

        override fun createReceive() =
                ReceiveBuilder()
                        .match(InitActor::class.java) { handleInitActor(it) }
                        .match(Start::class.java) { handleStart(it) }
                        .build()

        fun handleInitActor(init: InitActor) {
            log().info("Received {}", init)
            this.neighbours = init.neighbourProcs
            sender.tell(InitActorCompleted(), self())
        }

        fun handleStart(start: Start) {
            log().info("Received {}", start)
        }
    }

    val actorSystem = ActorSystem.create("KnotDetect")
    val a1 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "1")
    val a2 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "2")
    val a3 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "3")
    val a4 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "4")
    val a5 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "5")
    val a6 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "6")
    val a7 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "7")
    val a8 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "8")
    val a9 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "9")
    val a10 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "10")
    val a11 = actorSystem.actorOf(Props.create(KnotDetectActor::class.java), "11")

    a1.tell(InitActor(emptyList()), ActorRef.noSender())
}
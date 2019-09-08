package randezvous

import akka.actor._

// messages
case class Hello(response: Boolean)

/**
  * Scala/Akka implementation of probabilistic distributed rendez-vous algorithm
  */
class MyRandezVous extends CellularAlgorithm {

  def communicate(x: ActorRef) {
    log.info("Sending Hello to " + x.path.name + " from " + self.path.name)
    x ! Hello(true)
  }

  override def receive = super.receive orElse {
    case Hello(response) =>
      log.info("Received Hello at " + self.path.name + " from " + sender.path.name)
      if (response) {
        sender ! Hello(false)
      }
  }

}

object MyRandezVousMain extends App {
  val system = ActorSystem("MyRandezVousSystem")
  // default Actor constructor
  val a = system.actorOf(Props[MyRandezVous], name = "a")
  val b = system.actorOf(Props[MyRandezVous], name = "b")
  val c = system.actorOf(Props[MyRandezVous], name = "c")

  a ! InitActor(List(b, c))
  b ! InitActor(List(a, c))
  c ! InitActor(List(a, b))

  Thread.sleep(10000)

  system.terminate()
}

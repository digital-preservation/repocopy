package uk.gov.tna.dri.repocopy


import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws._
import play.api.libs.ws.ahc._

import scala.concurrent._
import scala.concurrent.duration._


import scala.concurrent.{Await, Future}


object Repocopy extends App {

  import DefaultBodyReadables._
  import scala.concurrent.ExecutionContext.Implicits._


  // Create Akka system for thread and streaming management
  implicit val system = ActorSystem()

  implicit val materializer = ActorMaterializer()

  // Create the standalone WS client
  // no argument defaults to a AhcWSClientConfig created from
  // "AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)"
  val wsClient = StandaloneAhcWSClient()

  callMavenRepo(wsClient, "uk.gov.nationalarchives", "droid-core")


  Await.ready(call(wsClient)
    .andThen { case _ => wsClient.close() }
    .andThen { case _ => system.terminate() }, 5 seconds)

  println("konec")




  def call(wsClient: StandaloneWSClient): Future[Unit] = {
    wsClient.url("http://www.google.com").get().map { response ⇒
      val statusText: String = response.statusText
      val body = response.body[String]
      //println(s"Got a response $statusText")
    }
  }


  def callMavenRepo(wsClient: StandaloneWSClient, groupID: String, artifactId: String): Unit = {
    import play.api.libs.ws.XMLBodyReadables._

    val urlGroup = groupID.replaceAll("\\.", "/")


//     https://oss.sonatype.org/              content/repositories/releases/uk/gov/nationalarchives/droid-core/
    // https://oss.sonatype.org/service/local/repositories/releases/content/uk/gov/nationalarchives/droid-core/
    //                                      http://central.maven.org/maven2/uk/gov/nationalarchives/droid-core/

    // https://oss.sonatype.org/service/local/repositories/snapshots/content/uk/gov/nationalarchives/droid-core/maven-metadata.xml


    val responseBody: Future[scala.xml.Elem] = wsClient.url(s"https://oss.sonatype.org/service/local/repositories/releases/content/${urlGroup}/${artifactId}/maven-metadata.xml")
      .get().map {responseBody => responseBody.body[scala.xml.Elem]}

    val metadata = Await.result(responseBody, 10 seconds)

    println("")
    println(metadata)
    println("\n")



    val version = (metadata \ "versioning" \ "release").text

    println(s"version : ${version}")

    val soubory: Future[scala.xml.Elem] = wsClient.url(s"https://oss.sonatype.org/service/local/repositories/releases/content/${urlGroup}/${artifactId}/${version}/")
      .get().map {responseBody => responseBody.body[scala.xml.Elem]}

    val files = Await.result(soubory, 10 seconds)

    val ggg = (files \ "data" \ "content-item" \ "text").toList.map(_.text).filter(x =>  !(x.endsWith(".sha1") || x.endsWith(".md5") || x.endsWith(".asc")))

    println(ggg.mkString("\n"))

//    Console.println(files)


  }



}

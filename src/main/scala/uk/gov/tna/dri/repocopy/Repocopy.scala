package uk.gov.tna.dri.repocopy


import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws._
import play.api.libs.ws.ahc._
import play.api.libs.ws.XMLBodyReadables._

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try


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

  //callMavenRepo(wsClient, "uk.gov.nationalarchives", "droid-core")

  callGroupProject(wsClient, "uk.gov.nationalarchives", "droid")


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




  def callGroupProject(wsClient: StandaloneWSClient, groupID: String, artifactId: String): Unit = {

    val urlGroup = groupID.replaceAll("\\.", "/")

    val responseBody: Future[scala.xml.Elem] = wsClient.url(s"https://oss.sonatype.org/service/local/repositories/releases/content/${urlGroup}/${artifactId}/maven-metadata.xml")
      .get().map {responseBody => responseBody.body[scala.xml.Elem]}

    val metadata = Await.result(responseBody, 10 seconds)

//    println("")
//    println(metadata)
//    println("\n")

    val version = (metadata \ "versioning" \ "release").text

    val pomFuture: Future[scala.xml.Elem] = wsClient.url(s"https://oss.sonatype.org/service/local/repositories/releases/content/" +
      s"${urlGroup}/${artifactId}/${version}/${artifactId}-${version}.pom")
      .get().map {responseBody => responseBody.body[scala.xml.Elem]}


    val pom = Await.result(pomFuture, 10 seconds)

//    print(pom)

    val modules = (pom \ "modules" \ "module").toList.map(_.text)

    println(modules.mkString("\n"))

    val files = modules.map(x => Try(callMavenRepo(wsClient, groupID, x, version))).filter(_.isSuccess).map(_.get).flatten

    println("\n\nall files to copy : ")
    println(files.mkString("\n"))

  }



  def callMavenRepo(wsClient: StandaloneWSClient, groupID: String, artifactId: String, version: String): List[String] = {


    val urlGroup = groupID.replaceAll("\\.", "/")
    

    val soubory: Future[scala.xml.Elem] = wsClient.url(s"https://oss.sonatype.org/service/local/repositories/releases/content/${urlGroup}/${artifactId}/${version}/")
      .get().map {responseBody => responseBody.body[scala.xml.Elem]}

    val files = Await.result(soubory, 10 seconds)

    val ggg = (files \ "data" \ "content-item" \ "text").toList.map(_.text).filter(x =>  !(x.endsWith(".sha1") || x.endsWith(".md5") || x.endsWith(".asc")))

    println(ggg.mkString("\n"))

//    Console.println(files)

    ggg

  }



}

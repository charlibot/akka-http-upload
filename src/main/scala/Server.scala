import akka.actor.ActorSystem
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.HttpEntity.apply
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.stream.ActorMaterializer
import akka.util.ByteString
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.event.Logging
import akka.stream.scaladsl.Source
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import org.json4s.DefaultFormats


case class A(s: String)
case class FinishedUpload(result: String)
object Boom{
  def jsonMarshallAndComplete(p: AnyRef) = complete {
    implicit val formats = DefaultFormats
  	  val s = Serialization.write(p)
  	  println(s)
      val source = Source.fromIterator(() ⇒ s.grouped(8).map(ByteString(_)))
      println(source)
      HttpEntity(ContentTypes.`application/json`, s.length().toLong, source)
    }
}

object Service {
  import Json4sSupport._
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val formats = DefaultFormats
//  implicit val jacksonSerialization = Serialization

  implicit def myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e: Exception =>
        extractRequest { req =>
          extractUri { uri =>
            complete(HttpResponse(InternalServerError, entity = e.toString))
          }
        }
    }


  val route =
    path("blah") {
      get {
        complete {
          "Fish"
        }
      } ~
      post {
        complete {
          "Frog"
        }
      }
    } ~
  path("foo") {
    get {
      complete {
        "moose"
      }
    } ~
    post {
      Boom.jsonMarshallAndComplete {
        ""
      }
    }
  }

  val route2 =
    path("fit") {
      post {
        complete {
          "Boom"
        }
      }
    }

}

object Server extends App  {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  
  implicit val formats = DefaultFormats
  implicit val jacksonSerialization = Serialization
  
  def jsonMarshallAndComplete(p: AnyRef) = complete {
	  val s = Serialization.write(p)
	  println(s)
    val source = Source.fromIterator(() ⇒ s.grouped(8).map(ByteString(_)))
    println(source)
    println(source.runFold("")((a,b) => a))
    HttpEntity(ContentTypes.`application/json`, s.length().toLong, source)
  }

  val route2 =
    path("fit") {
      post {
        complete {
          "Boom"
        }
      }
    }
  
  val route = 
  path("fit") {
    post {
      complete {
        "Boom"
      }
    }
  } ~
    path("playing") {
      get {
        Boom.jsonMarshallAndComplete {
//          val x = scala.io.Source.fromFile("/Users/Charlie/Downloads/customerRev.csv")
//          val x = akka.stream.scaladsl.FileIO.fromFile(new java.io.File("/Users/Charlie/Downloads/customerRev.csv"))
//          val y = Source.fromIterator { () => x.map(ByteString(_)) }
          val a = FinishedUpload("Finished upload")
          a
//          HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, 12590378L, x))
        }
      }
    } ~ 
    path("filedown") {
      get {
        getFromFile("/Users/Charlie/Downloads/customerRev.csv")
      }
    }
  
  val myLoggedRoute = DebuggingDirectives.logResult(("booms", Logging.InfoLevel))
  Http().bindAndHandle(myLoggedRoute(route), "0.0.0.0", 9999)
  println("Server started")
}
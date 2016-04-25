import java.io.{File, RandomAccessFile}
import java.util.concurrent.ConcurrentHashMap

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{extractRequest => _, extractUri => _, mapRejections => _, _}
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.server.directives.BasicDirectives.{extractRequestContext => _, _}
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.server.directives.FutureDirectives.{onSuccess => _, _}
import akka.http.scaladsl.server.directives.MarshallingDirectives.{as => _, entity => _, _}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Sink, Source, StreamConverters}
import akka.util.ByteString

import scala.collection.mutable

/*
  * `resumableChunkNumber`: The index of the chunk in the current upload. First chunk is `1` (no base-0 counting here).
  * `resumableTotalChunks`: The total number of chunks.
  * `resumableChunkSize`: The general chunk size. Using this value and `resumableTotalSize` you can calculate the total number of chunks. Please note that the size of the data received in the HTTP might be lower than `resumableChunkSize` of this for the last chunk for a file.
  * `resumableTotalSize`: The total file size.
  * `resumableIdentifier`: A unique identifier for the file contained in the request.
  * `resumableFilename`: The original file name (since a bug in Firefox results in the file name not being transmitted in chunk multipart posts).
  * `resumableRelativePath`: The file's relative path when selecting a directory (defaults to file name in all browsers except Chrome).
 */
case class ResumableInfo(
  resumableChunkNumber: Int,
  resumableCurrentChunkSize: Long,
  resumableChunkSize: Long,
  resumableTotalSize: Long,
  resumableIdentifier: String,
  resumableFilename: String,
  resumableRelativePath: String)

object ResumableFileUploadServer {

  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val uploadDir = "uploadedFiles"

  val resInfoParams = parameter(
    'resumableChunkNumber.as[Int],
    'resumableCurrentChunkSize.as[Long],
    'resumableChunkSize.as[Long],
    'resumableTotalSize.as[Long],
    'resumableIdentifier,
    'resumableFilename,
    'resumableRelativePath).as(ResumableInfo)

  val resInfoFormFields = formFields(
    'resumableChunkNumber.as[Int],
    'resumableCurrentChunkSize.as[Long],
    'resumableChunkSize.as[Long],
    'resumableTotalSize.as[Long],
    'resumableIdentifier,
    'resumableFilename,
    'resumableRelativePath).as(ResumableInfo)

  implicit def myExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e: Exception =>
        extractRequest { req =>
          extractUri { uri =>
            println(s"Received $uri request $req.", e)
            complete(HttpResponse(InternalServerError, entity = e.toString))
          }
        }
    }

  val route =
    path("upload") {
      get {
        getFromResource("webapp/testUpload.html")
      } ~
      post {
        fileUpload("file") { case (metadata, byteSource) =>
          val f = new File(uploadDir, metadata.fileName)
          val sink = FileIO.toFile(f)
          val startTime = System.currentTimeMillis()
          complete {
            byteSource.runWith(sink).map { bytesWritten =>
              val endTime = System.currentTimeMillis()
              val timeTaken = endTime - startTime
              s"$bytesWritten in $timeTaken milliseconds"
            }
          }
        }
      }
    } ~
  pathPrefix("java-example") {
    pathPrefix("upload") {
      post {
        resInfoParams { resInfo =>
          extractRequest { request =>
            val byteSource = request.entity.dataBytes
            val filePath = new File(uploadDir, resInfo.resumableFilename).getAbsolutePath() + ".temp"
            val rafOutputStream = new RandomFileOutputStream(filePath)
            val seekPoint = (resInfo.resumableChunkNumber - 1) * resInfo.resumableChunkSize
            rafOutputStream.setFilePointer(seekPoint)
            val sink = StreamConverters.fromOutputStream(() => rafOutputStream)
            complete {
              byteSource.runWith(sink).map { bytesWritten =>
                rafOutputStream.close()
                addSeenChunk(resInfo.resumableIdentifier, resInfo.resumableChunkNumber)
                if (haveFinished(resInfo.resumableIdentifier, resInfo.resumableCurrentChunkSize, resInfo.resumableTotalSize)) {
                  val file = new File(filePath)
                  val newPath = file.getAbsolutePath().substring(0, file.getAbsolutePath().length() - ".temp".length())
                  file.renameTo(new File(newPath))
                }
                (StatusCodes.OK, HttpEntity.Empty)
              }
            }
          }
        }
      } ~
      get {
        resInfoParams {  resInfo =>
          // check we haevnt already seen this file
          val check = haveSeenChunk(resInfo.resumableIdentifier, resInfo.resumableChunkNumber, resInfo.resumableChunkSize, resInfo.resumableTotalSize)
          if (check) {
            complete((StatusCodes.OK, HttpEntity.Empty))
          } else {
            complete((StatusCodes.NoContent, HttpEntity.Empty))
          }
        }
      }
    } ~
    get {
      getFromResource("webapp/index.html")
//      getFromFile("/Users/Charlie/Documents/Massive/akka-test/src/main/webapp/index.html")
    }
  } ~
  path(Rest) { f =>
    get {
      getFromResource(s"webapp/$f")
//      getFromDirectory(s"/Users/Charlie/Documents/Massive/akka-test/src/main/webapp/$f")
    }
  }

  def mapRejectionsWithLog(uri: Uri) = mapRejections { rejection =>
    println(s"Rejection for $uri with rejections: $rejection")
    rejection
  }

  def haveFinished(fileIdentifer: String, chunkSize: Long, totalSize: Long): Boolean = {
    chunksSeen.get(fileIdentifer).forall(x => x)
  }

  def addSeenChunk(fileIdentifer: String, chunkNumber: Int) = {
    chunksSeen.get(fileIdentifer)(chunkNumber - 1) = true
  }

  def haveSeenChunk(fileIdentifer: String, chunkNumber: Int, normalChunkSize: Long, totalSize: Long): Boolean = {
    if (!chunksSeen.containsKey(fileIdentifer)) {
      addNewFileInChunksSeen(fileIdentifer, normalChunkSize, totalSize)
    }
    chunksSeen.get(fileIdentifer)(chunkNumber - 1)
  }

  def addNewFileInChunksSeen(fileIdentifer: String, normalChunkSize: Long, totalSize: Long): Unit = {
    val numberOfChunksRequired = (totalSize / normalChunkSize) + 1
    chunksSeen.put(fileIdentifer, Array.fill(numberOfChunksRequired.toInt)(false))
  }

  val chunksSeen = new ConcurrentHashMap[String, Array[Boolean]]()

  def main(args: Array[String]) {
    val r = extractUri(mapRejectionsWithLog(_)(route))
    Http().bindAndHandle(r, "0.0.0.0", 9998)
    println("Server started")
  }

}
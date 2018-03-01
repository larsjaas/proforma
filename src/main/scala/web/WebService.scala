package skjemail.web

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.{Http, server}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.HttpCharsets.`UTF-8`
import akka.http.scaladsl.model.{HttpEntity, MediaType, _}
import akka.http.scaladsl.model.headers.HttpEncodings.gzip
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, RouteResult, StandardRoute}
import akka.http.scaladsl.settings.{ParserSettings, RoutingSettings}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import skjemail.framework.ConfigMonitor.GetConfig
import skjemail.framework.{AddConfigListener, AppConfig, ConfigEvent, ConfigUpdatedEvent}

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}


class WebService(configMonitor: ActorRef, files: ActorRef, restHandler: ActorRef) extends Actor with LazyLogging {
    var bindingFuture: Future[ServerBinding] = _
    var actorMaterializer: ActorMaterializer = _

    var port: Int = -1
    var isUp: Boolean = false
    var gzipEnabled = false

    override def preStart: Unit = {
        logger.info(s"${WebService.NAME} born")

        configMonitor ! GetConfig
        configMonitor ! AddConfigListener(WebService.configBaseKey)
    }

    def compress(input: Array[Byte]): Array[Byte] = {
        val bos = new ByteArrayOutputStream(input.length)
        val gzip = new GZIPOutputStream(bos)
        gzip.write(input)
        gzip.close()
        val compressed = bos.toByteArray
        bos.close()
        compressed
    }

    def setUp(): Unit = {
        if (!isUp) {
            implicit val system = context.system

            actorMaterializer = ActorMaterializer()(context = context)
            implicit val materializer = actorMaterializer

            // needed for the future flatMap/onComplete in the end
            implicit val executionContext = system.dispatcher
            implicit val timeout = Timeout(5 seconds)

            val route: Route =
                get {
                    extractRequest { req: HttpRequest =>

                        def respond(typ: Option[ContentType], filedata: Option[FileData]): server.Route = {
                            if (typ.isDefined) {
                                if (filedata.get.compressed) {
                                    respondWithHeader(RawHeader("Content-Encoding", "gzip")) {
                                        complete(HttpEntity(typ.get, ByteString(filedata.get.payload)))
                                    }
                                }
                                else if (gzipEnabled) {
                                    respondWithHeader(RawHeader("Content-Encoding", "gzip")) {
                                        complete(HttpEntity(typ.get, ByteString(compress(filedata.get.payload))))
                                    }
                                }
                                else {
                                    complete(HttpEntity(typ.get, ByteString(filedata.get.payload)))
                                }
                            }
                            else
                                reject
                        }

                        def isGzipAllowed(req: HttpRequest): Boolean = {
                            req.headers
                              .find({ h: HttpHeader => h.lowercaseName == "accept-encoding" })
                              .map({ h: HttpHeader => h.value.indexOf("gzip") != -1 })
                            match {
                                case Some(true) => true
                                case _ => false
                            }
                        }

                        pathSingleSlash {
                            val future: Future[Any] = files ? FileRequest(FileName("/index.html"), isGzipAllowed(req))
                            onSuccess(future) { filedata =>
                                val (typ: Option[ContentType], payload: Option[FileData]) = filedata match {
                                    case f@FileData(FileName(_, "index", ".html"), data, compressed) =>
                                        (Some(ContentTypes.`text/html(UTF-8)`), Some(f))
                                    case _ => (None, None)
                                }
                                respond(typ, payload)
                            }
                        } ~
                          path("index.html") {
                              val future: Future[Any] = files ? FileRequest(FileName("/index.html"), isGzipAllowed(req))
                              onSuccess(future) { filedata =>
                                  val (typ: Option[ContentType], payload: Option[FileData]) = filedata match {
                                      case f@FileData(FileName(_, "index", ".html"), data, compressed) =>
                                          (Some(ContentTypes.`text/html(UTF-8)`), Some(f))
                                      case _ => (None, None)
                                  }
                                  respond(typ, payload)
                              }
                          } ~
                          path(("css" | "js" | "html") / RemainingPath) { p =>
                              extractMatchedPath { matched ⇒
                                  val uri = matched.toString
                                  var future: Future[Any] = files ? FileRequest(FileName(uri), isGzipAllowed(req))
                                  onSuccess(future) { filedata =>
                                      val typ: Option[ContentType] = filedata match {
                                          case FileData(FileName(_, _, ".html"), _, _) => Some(ContentTypes.`text/html(UTF-8)`)
                                          case FileData(FileName(_, _, ".txt"), _, _) => Some(ContentTypes.`text/plain(UTF-8)`)
                                          case FileData(FileName(_, _, ".css"), _, _) => Some(CustomTypes.`text/css(UTF-8)`)
                                          case FileData(FileName(_, _, ".js"), _, _) => Some(CustomTypes.`application/javascript(UTF-8)`)
                                          case FileData(FileName(_, _, ".json"), _, _) => Some(CustomTypes.`application/json(UTF-8)`)
                                          case FileData(FileName(_, _, ".woff"), _, _) => Some(CustomTypes.`application/font-woff`)
                                          case FileData(FileName(_, _, ".woff2"), _, _) => Some(CustomTypes.`application/font-woff2`)
                                          case FileData(FileName(_, _, ".ttf"), _, _) => Some(CustomTypes.`application/x-font-truetype`)
                                          case FileData(FileName(_, _, ".svg"), _, _) => Some(CustomTypes.`image/svg+xml(UTF-8)`)
                                          case FileData(FileName(path, b, e), _, _) =>
                                              logger.warn(s"no registered mime-type for '$path' + '$b' + '$e' - defaulting to text/html")
                                              Some(ContentTypes.`text/html(UTF-8)`)
                                          case _ => None
                                      }
                                      val payload: Option[FileData] = filedata match {
                                          case f@FileData(_, _, _) => Some(f)
                                          case _ => None
                                      }
                                      respond(typ, payload)
                                  }
                              }
                          } ~
                          path("api" / RemainingPath) { remaining =>
                              val future = restHandler ? RestRequest("GET", remaining.toString.split('/'), null, "")
                              onSuccess(future) { x: Any =>
                                  x match {
                                      case r: String => complete(HttpEntity(CustomTypes.`application/json(UTF-8)`, ByteString(r)))
                                      case _ => reject
                                  }
                              }
                          }
                          //~
                          //path("poll") { // move to "api/"?
                          //    implicit val timeout = Timeout(15000L, TimeUnit.MILLISECONDS)
                          //    val future = pollHandler ? RestRequest("GET", null, null, "")
                          //    onSuccess(future) { x: Any =>
                          //        x match {
                          //            case r: String => complete(HttpEntity(CustomTypes.`application/json(UTF-8)`, ByteString(r)))
                          //            case _ => reject
                          //        }
                          //    }
                          //}
                    }
                } ~
                post {
                    extractRequest { req: HttpRequest =>
                        path("api" / RemainingPath) { remaining =>
                            val getBodySource : HttpRequest => Source[ByteString,_] =
                                _.entity
                                    .dataBytes

                            val convertSrcToSeq : Source[ByteString,_] => Future[Seq[ByteString]] =
                                _ runWith Sink.seq

                            val reduceSeqToStr : Future[Seq[ByteString]] => Future[ByteString] =
                                _ map (_ reduceOption (_ ++ _) getOrElse ByteString.empty)

                            val getBodyStrFromRequest : HttpRequest => Future[ByteString] =
                                getBodySource andThen convertSrcToSeq andThen reduceSeqToStr

                            onSuccess(getBodyStrFromRequest(req)) { body: ByteString =>
                                val bodystr = new String(body.to[Array], "UTF-8")
                                val future = restHandler ? RestRequest("POST", remaining.toString.split('/'), null, bodystr)
                                onSuccess(future) { x: Any =>
                                    x match {
                                        case r: String => complete(HttpEntity(CustomTypes.`application/json(UTF-8)`, ByteString(r)))
                                        case _ => reject
                                    }
                                }
                            }
                        }
                    }
                }


            implicit val routingSettings = RoutingSettings(system)
            implicit val parserSettings = ParserSettings(system)
            val routeFlow = RouteResult.route2HandlerFlow(route)

            bindingFuture = Http().bindAndHandle(routeFlow, "::1", port)
            bindingFuture onComplete {
                case Success(x) =>
                    logger.info(s"Web service is online at http://localhost:$port/")
                case Failure(e) =>
                    logger.error(s"bind failed - exiting: $e")
                    system.terminate()
            }
            isUp = true
        }
    }

    override def postStop: Unit = {
        takeDown()
        logger.info(s"${WebService.NAME} died")
    }

    def takeDown(): Unit = {
        if (isUp) {
            implicit val materializer = actorMaterializer
            implicit val executionContext = context.system.dispatcher
            bindingFuture.flatMap(_.unbind())
            logger.info("stopped web service")
            isUp = false
        }
    }

    def receive: Actor.Receive = {
        case ev: ConfigEvent => reconfigure(ev.config)
        case ev: ConfigUpdatedEvent => reconfigure(ev.config)
        case x => logger.warn(s"unknown message ${x.getClass}")
    }

    def reconfigure(config: Config): Unit = {
        logger.info("updating config")
        val newPort = config.getInt(WebService.portConfigKey)
        if (port != newPort) {
            port = newPort
            takeDown()
            setUp()
        }
        gzipEnabled = config.getBoolean(WebService.gzipConfigKey)
    }
}

object WebService {
    val NAME = "WebService"

    val configBaseKey = "skjemail.web"
    val portConfigKey = "skjemail.web.port"
    val gzipConfigKey = "skjemail.web.gzip"
}

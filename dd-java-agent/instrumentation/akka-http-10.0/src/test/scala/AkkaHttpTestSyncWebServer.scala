import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import io.opentelemetry.auto.agent.test.base.HttpServerTest
import io.opentelemetry.auto.agent.test.base.HttpServerTest.ServerEndpoint._
import groovy.lang.Closure

import scala.concurrent.Await

object AkkaHttpTestSyncWebServer {
  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher
  val syncHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, uri: Uri, _, _, _) => {
      val endpoint = HttpServerTest.ServerEndpoint.forPath(uri.path.toString())
      HttpServerTest.controller(endpoint, new Closure[HttpResponse](()) {
        def doCall(): HttpResponse = {
          val resp = HttpResponse(status = endpoint.getStatus)
          endpoint match {
            case SUCCESS => resp.withEntity(endpoint.getBody)
            case QUERY_PARAM => resp.withEntity(uri.queryString().orNull)
            case REDIRECT => resp.withHeaders(headers.Location(endpoint.getBody))
            case ERROR => resp.withEntity(endpoint.getBody)
            case EXCEPTION => throw new Exception(endpoint.getBody)
            case _ => HttpResponse(status = NOT_FOUND.getStatus).withEntity(NOT_FOUND.getBody)
          }
        }
      })
    }
  }

  private var binding: ServerBinding = null

  def start(port: Int): Unit = synchronized {
    if (null == binding) {
      import scala.concurrent.duration._
      binding = Await.result(Http().bindAndHandleSync(syncHandler, "localhost", port), 10 seconds)
    }
  }

  def stop(): Unit = synchronized {
    if (null != binding) {
      binding.unbind()
      system.terminate()
      binding = null
    }
  }
}

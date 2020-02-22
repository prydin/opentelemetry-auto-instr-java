import io.opentelemetry.auto.test.AgentTestRunner
import org.glassfish.jersey.client.JerseyClientBuilder
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.util.concurrent.AsyncConditions

import javax.ws.rs.client.Client

import static io.opentelemetry.auto.test.server.http.TestHttpServer.httpServer

class JaxMultithreadedClientTest extends AgentTestRunner {

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      prefix("success") {
        String msg = "Hello."
        response.status(200).send(msg)
      }
    }
  }

  def "multiple threads using the same builder works"() {
    given:
    def conds = new AsyncConditions(10)
    def uri = server.address.resolve("/success")
    def builder = new JerseyClientBuilder()

    // Start 10 threads and do 50 requests each
    when:
    (1..10).each {
      Thread.start {
        boolean hadErrors = (1..50).any {
          try {
            Client client = builder.build()
            client.target(uri).request().get()
          } catch (Exception e) {
            e.printStackTrace()
            return true
          }
          return false
        }

        conds.evaluate {
          assert !hadErrors
        }
      }
    }

    then:
    conds.await(10)
  }
}

import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.netty40.client.NettyHttpClientDecorator
import io.opentelemetry.auto.test.base.HttpClientTest
import org.asynchttpclient.AsyncCompletionHandler
import org.asynchttpclient.AsyncHttpClient
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.asynchttpclient.Response
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import static io.opentelemetry.auto.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.auto.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace
import static org.asynchttpclient.Dsl.asyncHttpClient

class Netty40ClientTest extends HttpClientTest<NettyHttpClientDecorator> {

  @Shared
  def clientConfig = DefaultAsyncHttpClientConfig.Builder.newInstance().setRequestTimeout(TimeUnit.SECONDS.toMillis(10).toInteger())
  @Shared
  @AutoCleanup
  AsyncHttpClient asyncHttpClient = asyncHttpClient(clientConfig)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def methodName = "prepare" + method.toLowerCase().capitalize()
    def requestBuilder = asyncHttpClient."$methodName"(uri.toString())
    headers.each { requestBuilder.setHeader(it.key, it.value) }
    def response = requestBuilder.execute(new AsyncCompletionHandler() {
      @Override
      Object onCompleted(Response response) throws Exception {
        callback?.call()
        return response
      }
    }).get()
    return response.statusCode
  }

  @Override
  NettyHttpClientDecorator decorator() {
    return NettyHttpClientDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "netty.client.request"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  def "connection error (unopened port)"() {
    given:
    def uri = new URI("http://127.0.0.1:$UNUSABLE_PORT/") // Use numeric address to avoid ipv4/ipv6 confusion

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      def size = traces[0].size()
      trace(0, size) {
        basicSpan(it, 0, "parent", null, null, thrownException)

        // AsyncHttpClient retries across multiple resolved IP addresses (e.g. 127.0.0.1 and 0:0:0:0:0:0:0:1)
        // for up to a total of 10 seconds (default connection time limit)
        for (def i = 1; i < size; i++) {
          span(i) {
            operationName "netty.connect"
            childOf span(0)
            errored true
            tags {
              "$Tags.COMPONENT" "netty"
              Class errorClass = ConnectException
              try {
                errorClass = Class.forName('io.netty.channel.AbstractChannel$AnnotatedConnectException')
              } catch (ClassNotFoundException e) {
                // Older versions use 'java.net.ConnectException' and do not have 'io.netty.channel.AbstractChannel$AnnotatedConnectException'
              }
              errorTags errorClass, "Connection refused: /127.0.0.1:$UNUSABLE_PORT"
            }
          }
        }
      }
    }

    where:
    method = "GET"
  }
}

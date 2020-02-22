import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.test.instrumentation.springwebflux.server.EchoHandlerFunction
import io.opentelemetry.test.instrumentation.springwebflux.server.FooModel
import io.opentelemetry.test.instrumentation.springwebflux.server.SpringWebFluxTestApplication
import io.opentelemetry.test.instrumentation.springwebflux.server.TestController
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.web.server.ResponseStatusException

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [SpringWebFluxTestApplication, ForceNettyAutoConfiguration])
class SpringWebfluxTest extends AgentTestRunner {

  @TestConfiguration
  static class ForceNettyAutoConfiguration {
    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory()
    }
  }

  static final okhttp3.MediaType PLAIN_TYPE = okhttp3.MediaType.parse("text/plain; charset=utf-8")
  static final String INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX = SpringWebFluxTestApplication.getName() + "\$"
  static final String SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX = SpringWebFluxTestApplication.getSimpleName() + "\$"

  @LocalServerPort
  private int port

  OkHttpClient client = OkHttpUtils.client(true)

  def "Basic GET test #testName"() {
    setup:
    String url = "http://localhost:$port$urlPath"
    def request = new Request.Builder().url(url).get().build()
    when:
    def response = client.newCall(request).execute()

    then:
    response.code == 200
    response.body().string() == expectedResponseBody
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "netty.request"
          parent()
          tags {
            "$MoreTags.RESOURCE_NAME" "GET $urlPathWithVariables"
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "netty"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Long
            "$Tags.HTTP_URL" url
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
        span(1) {
          if (annotatedMethod == null) {
            // Functional API
            operationNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          } else {
            // Annotation API
            operationName TestController.getSimpleName() + "." + annotatedMethod
          }
          childOf(span(0))
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "spring-webflux-controller"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            if (annotatedMethod == null) {
              // Functional API
              "request.predicate" "(GET && $urlPathWithVariables)"
              "handler.type" { String tagVal ->
                return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
              }
            } else {
              // Annotation API
              "handler.type" TestController.getName()
            }
          }
        }
      }
    }

    where:
    testName                             | urlPath              | urlPathWithVariables   | annotatedMethod | expectedResponseBody
    "functional API without parameters"  | "/greet"             | "/greet"               | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE
    "functional API with one parameter"  | "/greet/WORLD"       | "/greet/{name}"        | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " WORLD"
    "functional API with two parameters" | "/greet/World/Test1" | "/greet/{name}/{word}" | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " World Test1"
    "functional API delayed response"    | "/greet-delayed"     | "/greet-delayed"       | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE

    "annotation API without parameters"  | "/foo"               | "/foo"                 | "getFooModel"   | new FooModel(0L, "DEFAULT").toString()
    "annotation API with one parameter"  | "/foo/1"             | "/foo/{id}"            | "getFooModel"   | new FooModel(1L, "pass").toString()
    "annotation API with two parameters" | "/foo/2/world"       | "/foo/{id}/{name}"     | "getFooModel"   | new FooModel(2L, "world").toString()
    "annotation API delayed response"    | "/foo-delayed"       | "/foo-delayed"         | "getFooDelayed" | new FooModel(3L, "delayed").toString()
  }

  def "GET test with async response #testName"() {
    setup:
    String url = "http://localhost:$port$urlPath"
    def request = new Request.Builder().url(url).get().build()
    when:
    def response = client.newCall(request).execute()

    then:
    response.code == 200
    response.body().string() == expectedResponseBody
    assertTraces(1) {
      println TEST_WRITER
      trace(0, 3) {
        span(0) {
          operationName "netty.request"
          parent()
          tags {
            "$MoreTags.RESOURCE_NAME" "GET $urlPathWithVariables"
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "netty"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Long
            "$Tags.HTTP_URL" url
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
        span(1) {
          if (annotatedMethod == null) {
            // Functional API
            operationNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          } else {
            // Annotation API
            operationName TestController.getSimpleName() + "." + annotatedMethod
          }
          childOf(span(0))
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "spring-webflux-controller"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            if (annotatedMethod == null) {
              // Functional API
              "request.predicate" "(GET && $urlPathWithVariables)"
              "handler.type" { String tagVal ->
                return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
              }
            } else {
              // Annotation API
              "handler.type" TestController.getName()
            }
          }
        }
        span(2) {
          if (annotatedMethod == null) {
            // Functional API
            operationName "trace.annotation"
          } else {
            // Annotation API
            operationName "trace.annotation"
          }
          childOf(span(1))
          errored false
          tags {
            "$MoreTags.RESOURCE_NAME" annotatedMethod == null ? "SpringWebFluxTestApplication.tracedMethod" : "TestController.tracedMethod"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }

    where:
    testName                                  | urlPath                       | urlPathWithVariables             | annotatedMethod       | expectedResponseBody
    "functional API traced method from mono"  | "/greet-mono-from-callable/4" | "/greet-mono-from-callable/{id}" | null                  | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " 4"
    "functional API traced method"            | "/greet-traced-method/5"      | "/greet-traced-method/{id}"      | null                  | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " 5"
    "functional API traced method with delay" | "/greet-delayed-mono/6"       | "/greet-delayed-mono/{id}"       | null                  | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " 6"

    "annotation API traced method from mono"  | "/foo-mono-from-callable/7"   | "/foo-mono-from-callable/{id}"   | "getMonoFromCallable" | new FooModel(7L, "tracedMethod").toString()
    "annotation API traced method"            | "/foo-traced-method/8"        | "/foo-traced-method/{id}"        | "getTracedMethod"     | new FooModel(8L, "tracedMethod").toString()
    "annotation API traced method with delay" | "/foo-delayed-mono/9"         | "/foo-delayed-mono/{id}"         | "getFooDelayedMono"   | new FooModel(9L, "tracedMethod").toString()
  }

  def "404 GET test"() {
    setup:
    String url = "http://localhost:$port/notfoundgreet"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code == 404
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "netty.request"
          parent()
          tags {
            "$MoreTags.RESOURCE_NAME" "GET /**"
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "netty"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Long
            "$Tags.HTTP_URL" url
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 404
          }
        }
        span(1) {
          operationName "ResourceWebHandler.handle"
          childOf(span(0))
          errored true
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "spring-webflux-controller"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "handler.type" "org.springframework.web.reactive.resource.ResourceWebHandler"
            errorTags(ResponseStatusException, String)
          }
        }
      }
    }
  }

  def "Basic POST test"() {
    setup:
    String echoString = "TEST"
    String url = "http://localhost:$port/echo"
    RequestBody body = RequestBody.create(PLAIN_TYPE, echoString)
    def request = new Request.Builder().url(url).post(body).build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code() == 202
    response.body().string() == echoString
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "netty.request"
          parent()
          tags {
            "$MoreTags.RESOURCE_NAME" "POST /echo"
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "netty"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Long
            "$Tags.HTTP_URL" url
            "$Tags.HTTP_METHOD" "POST"
            "$Tags.HTTP_STATUS" 202
          }
        }
        span(1) {
          operationName EchoHandlerFunction.getSimpleName() + ".handle"
          childOf(span(0))
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "spring-webflux-controller"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "request.predicate" "(POST && /echo)"
            "handler.type" { String tagVal ->
              return tagVal.contains(EchoHandlerFunction.getName())
            }
          }
        }
        span(2) {
          operationName "echo"
          childOf(span(1))
          tags {
            "$MoreTags.RESOURCE_NAME" "echo"
            "$Tags.COMPONENT" "trace"
          }
        }
      }
    }
  }

  def "GET to bad endpoint #testName"() {
    setup:
    String url = "http://localhost:$port$urlPath"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code() == 500
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "netty.request"
          errored true
          parent()
          tags {
            "$MoreTags.RESOURCE_NAME" "GET $urlPathWithVariables"
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "netty"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Long
            "$Tags.HTTP_URL" url
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 500
          }
        }
        span(1) {
          if (annotatedMethod == null) {
            // Functional API
            operationNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          } else {
            // Annotation API
            operationName TestController.getSimpleName() + "." + annotatedMethod
          }
          childOf(span(0))
          errored true
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "spring-webflux-controller"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            if (annotatedMethod == null) {
              // Functional API
              "request.predicate" "(GET && $urlPathWithVariables)"
              "handler.type" { String tagVal ->
                return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
              }
            } else {
              // Annotation API
              "handler.type" TestController.getName()
            }
            errorTags(RuntimeException, "bad things happen")
          }
        }
      }
    }

    where:
    testName                   | urlPath             | urlPathWithVariables   | annotatedMethod
    "functional API fail fast" | "/greet-failfast/1" | "/greet-failfast/{id}" | null
    "functional API fail Mono" | "/greet-failmono/1" | "/greet-failmono/{id}" | null

    "annotation API fail fast" | "/foo-failfast/1"   | "/foo-failfast/{id}"   | "getFooFailFast"
    "annotation API fail Mono" | "/foo-failmono/1"   | "/foo-failmono/{id}"   | "getFooFailMono"
  }

  def "Redirect test"() {
    setup:
    String url = "http://localhost:$port/double-greet-redirect"
    String finalUrl = "http://localhost:$port/double-greet"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code == 200
    assertTraces(2) {
      // TODO: why order of spans is different in these traces?
      trace(0, 2) {
        span(0) {
          operationName "netty.request"
          parent()
          tags {
            "$MoreTags.RESOURCE_NAME" "GET /double-greet-redirect"
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "netty"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Long
            "$Tags.HTTP_URL" url
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 307
          }
        }
        span(1) {
          operationName "RedirectComponent.lambda"
          childOf(span(0))
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "spring-webflux-controller"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "request.predicate" "(GET && /double-greet-redirect)"
            "handler.type" { String tagVal ->
              return (tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
                || tagVal.contains("Lambda"))
            }
          }
        }
      }
      trace(1, 2) {
        span(0) {
          operationName "netty.request"
          parent()
          tags {
            "$MoreTags.RESOURCE_NAME" "GET /double-greet"
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "netty"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_HOST_IPV4" "127.0.0.1"
            "$Tags.PEER_PORT" Long
            "$Tags.HTTP_URL" finalUrl
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
        span(1) {
          operationNameContains(SpringWebFluxTestApplication.getSimpleName() + "\$", ".handle")
          childOf(span(0))
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "spring-webflux-controller"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
            "request.predicate" "(GET && /double-greet)"
            "handler.type" { String tagVal ->
              return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
            }
          }
        }
      }
    }
  }

  def "Multiple GETs to delaying route #testName"() {
    setup:
    def requestsCount = 50 // Should be more than 2x CPUs to fish out some bugs
    String url = "http://localhost:$port$urlPath"
    def request = new Request.Builder().url(url).get().build()
    when:
    def responses = (0..requestsCount - 1).collect { client.newCall(request).execute() }

    then:
    responses.every { it.code == 200 }
    responses.every { it.body().string() == expectedResponseBody }
    assertTraces(responses.size()) {
      responses.eachWithIndex { def response, int i ->
        trace(i, 2) {
          span(0) {
            operationName "netty.request"
            parent()
            tags {
              "$MoreTags.RESOURCE_NAME" "GET $urlPathWithVariables"
              "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
              "$Tags.COMPONENT" "netty"
              "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
              "$Tags.PEER_HOSTNAME" "localhost"
              "$Tags.PEER_HOST_IPV4" "127.0.0.1"
              "$Tags.PEER_PORT" Long
              "$Tags.HTTP_URL" url
              "$Tags.HTTP_METHOD" "GET"
              "$Tags.HTTP_STATUS" 200
            }
          }
          span(1) {
            if (annotatedMethod == null) {
              // Functional API
              operationNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
            } else {
              // Annotation API
              operationName TestController.getSimpleName() + "." + annotatedMethod
            }
            childOf(span(0))
            tags {
              "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
              "$Tags.COMPONENT" "spring-webflux-controller"
              "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
              if (annotatedMethod == null) {
                // Functional API
                "request.predicate" "(GET && $urlPathWithVariables)"
                "handler.type" { String tagVal ->
                  return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
                }
              } else {
                // Annotation API
                "handler.type" TestController.getName()
              }
            }
          }
        }
      }
    }

    where:
    testName                          | urlPath          | urlPathWithVariables | annotatedMethod | expectedResponseBody
    "functional API delayed response" | "/greet-delayed" | "/greet-delayed"     | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE
    "annotation API delayed response" | "/foo-delayed"   | "/foo-delayed"       | "getFooDelayed" | new FooModel(3L, "delayed").toString()
  }
}

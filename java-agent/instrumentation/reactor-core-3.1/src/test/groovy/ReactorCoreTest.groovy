import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.Trace
import io.opentelemetry.auto.instrumentation.api.AgentSpan
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.reactor.core.ReactorCoreAdviceUtils
import io.opentelemetry.auto.test.AgentTestRunner
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Shared

import java.time.Duration

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan

class ReactorCoreTest extends AgentTestRunner {

  public static final String EXCEPTION_MESSAGE = "test exception"

  @Shared
  def addOne = { i -> addOneFunc(i) }
  @Shared
  def throwException = { throw new RuntimeException(EXCEPTION_MESSAGE) }

  def "Publisher '#name' test"() {
    when:
    def result = runUnderTrace(publisher)

    then:
    result == expected
    and:
    assertTraces(1) {
      trace(0, workSpans + 2) {
        span(0) {
          operationName "trace-parent"
          parent()
          tags {
            "$MoreTags.RESOURCE_NAME" "trace-parent"
            "$Tags.COMPONENT" "trace"
          }
        }
        span(1) {
          operationName "publisher-parent"
          childOf(span(0))
          tags {
          }
        }
        for (int i = 0; i < workSpans; i++) {
          span(i + 2) {
            operationName "addOne"
            childOf(span(1))
            tags {
              "$MoreTags.RESOURCE_NAME" "addOne"
              "$Tags.COMPONENT" "trace"
            }
          }
        }
      }
    }

    where:
    name                  | expected | workSpans | publisher
    "basic mono"          | 2        | 1         | Mono.just(1).map(addOne)
    "two operations mono" | 4        | 2         | Mono.just(2).map(addOne).map(addOne)
    "delayed mono"        | 4        | 1         | Mono.just(3).delayElement(Duration.ofMillis(100)).map(addOne)
    "delayed twice mono"  | 6        | 2         | Mono.just(4).delayElement(Duration.ofMillis(100)).map(addOne).delayElement(Duration.ofMillis(100)).map(addOne)
    "basic flux"          | [6, 7]   | 2         | Flux.fromIterable([5, 6]).map(addOne)
    "two operations flux" | [8, 9]   | 4         | Flux.fromIterable([6, 7]).map(addOne).map(addOne)
    "delayed flux"        | [8, 9]   | 2         | Flux.fromIterable([7, 8]).delayElements(Duration.ofMillis(100)).map(addOne)
    "delayed twice flux"  | [10, 11] | 4         | Flux.fromIterable([8, 9]).delayElements(Duration.ofMillis(100)).map(addOne).delayElements(Duration.ofMillis(100)).map(addOne)

    "mono from callable"  | 12       | 2         | Mono.fromCallable({ addOneFunc(10) }).map(addOne)
  }

  def "Publisher error '#name' test"() {
    when:
    runUnderTrace(publisher)

    then:
    def exception = thrown RuntimeException
    exception.message == EXCEPTION_MESSAGE
    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "trace-parent"
          parent()
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "trace-parent"
            "$Tags.COMPONENT" "trace"
            errorTags(RuntimeException, EXCEPTION_MESSAGE)
          }
        }
        span(1) {
          operationName "publisher-parent"
          childOf(span(0))
          errored true
          tags {
            errorTags(RuntimeException, EXCEPTION_MESSAGE)
          }
        }
      }
    }

    where:
    name   | publisher
    "mono" | Mono.error(new RuntimeException(EXCEPTION_MESSAGE))
    "flux" | Flux.error(new RuntimeException(EXCEPTION_MESSAGE))
  }

  def "Publisher step '#name' test"() {
    when:
    runUnderTrace(publisher)

    then:
    def exception = thrown RuntimeException
    exception.message == EXCEPTION_MESSAGE
    and:
    assertTraces(1) {
      trace(0, workSpans + 2) {
        span(0) {
          operationName "trace-parent"
          parent()
          errored true
          tags {
            "$MoreTags.RESOURCE_NAME" "trace-parent"
            "$Tags.COMPONENT" "trace"
            errorTags(RuntimeException, EXCEPTION_MESSAGE)
          }
        }
        span(1) {
          operationName "publisher-parent"
          childOf(span(0))
          errored true
          tags {
            errorTags(RuntimeException, EXCEPTION_MESSAGE)
          }
        }
        for (int i = 0; i < workSpans; i++) {
          span(i + 2) {
            operationName "addOne"
            childOf(span(1))
            tags {
              "$MoreTags.RESOURCE_NAME" "addOne"
              "$Tags.COMPONENT" "trace"
            }
          }
        }
      }
    }

    where:
    name                 | workSpans | publisher
    "basic mono failure" | 1         | Mono.just(1).map(addOne).map({ throwException() })
    "basic flux failure" | 1         | Flux.fromIterable([5, 6]).map(addOne).map({ throwException() })
  }

  def "Publisher '#name' cancel"() {
    when:
    cancelUnderTrace(publisher)

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "trace-parent"
          parent()
          tags {
            "$MoreTags.RESOURCE_NAME" "trace-parent"
            "$Tags.COMPONENT" "trace"
          }
        }
        span(1) {
          operationName "publisher-parent"
          childOf(span(0))
          tags {
          }
        }
      }
    }

    where:
    name         | publisher
    "basic mono" | Mono.just(1)
    "basic flux" | Flux.fromIterable([5, 6])
  }

  @Trace(operationName = "trace-parent", resourceName = "trace-parent")
  def runUnderTrace(def publisher) {
    // This is important sequence of events:
    // We have a 'trace-parent' that covers whole span and then we have publisher-parent that overs only
    // operation to create publisher (and set its context).
    // The expectation is that then publisher is executed under 'publisher-parent', not under 'trace-parent'
    final AgentSpan span = startSpan("publisher-parent")
    publisher = ReactorCoreAdviceUtils.setPublisherSpan(publisher, span)
    // do not finish span here, it will be finished by ReactorCoreAdviceUtils

    // Read all data from publisher
    if (publisher instanceof Mono) {
      return publisher.block()
    } else if (publisher instanceof Flux) {
      return publisher.toStream().toArray({ size -> new Integer[size] })
    }

    throw new RuntimeException("Unknown publisher: " + publisher)
  }

  @Trace(operationName = "trace-parent", resourceName = "trace-parent")
  def cancelUnderTrace(def publisher) {
    final AgentSpan span = startSpan("publisher-parent")
    publisher = ReactorCoreAdviceUtils.setPublisherSpan(publisher, span)
    span.finish()

    publisher.subscribe(new Subscriber<Integer>() {
      void onSubscribe(Subscription subscription) {
        subscription.cancel()
      }

      void onNext(Integer t) {
      }

      void onError(Throwable error) {
      }

      void onComplete() {
      }
    })
  }

  @Trace(operationName = "addOne", resourceName = "addOne")
  def static addOneFunc(int i) {
    return i + 1
  }
}

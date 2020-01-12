package io.opentelemetry.auto.agent.decorator

import io.opentelemetry.auto.api.Config
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.AgentSpan
import io.opentelemetry.auto.instrumentation.api.Tags

import static io.opentelemetry.auto.agent.test.utils.ConfigUtils.withConfigOverride

class DatabaseClientDecoratorTest extends ClientDecoratorTest {

  def span = Mock(AgentSpan)

  def "test afterStart"() {
    setup:
    def decorator = newDecorator((String) serviceName)

    when:
    decorator.afterStart(span)

    then:
    if (serviceName != null) {
      1 * span.setTag(MoreTags.SERVICE_NAME, serviceName)
    }
    1 * span.setTag(Tags.COMPONENT, "test-component")
    1 * span.setTag(Tags.SPAN_KIND, "client")
    1 * span.setTag(Tags.DB_TYPE, "test-db")
    1 * span.setTag(MoreTags.SPAN_TYPE, "test-type")
    0 * _

    where:
    serviceName << ["test-service", "other-service", null]
  }

  def "test onConnection"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "$renameService") {
      decorator.onConnection(span, session)
    }

    then:
    if (session) {
      1 * span.setTag(Tags.DB_USER, session.user)
      1 * span.setTag(Tags.DB_INSTANCE, session.instance)
      if (renameService && session.instance) {
        1 * span.setTag(MoreTags.SERVICE_NAME, session.instance)
      }
    }
    0 * _

    where:
    renameService | session
    false         | null
    true          | [user: "test-user"]
    false         | [instance: "test-instance"]
    true          | [user: "test-user", instance: "test-instance"]
  }

  def "test onStatement"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onStatement(span, statement)

    then:
    1 * span.setTag(Tags.DB_STATEMENT, statement)
    0 * _

    where:
    statement      | _
    null           | _
    ""             | _
    "db-statement" | _
  }

  def "test assert null span"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.afterStart((AgentSpan) null)

    then:
    thrown(AssertionError)

    when:
    decorator.onConnection(null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onStatement(null, null)

    then:
    thrown(AssertionError)
  }

  @Override
  def newDecorator(String serviceName = "test-service") {
    return new DatabaseClientDecorator<Map>() {
      @Override
      protected String[] instrumentationNames() {
        return ["test1", "test2"]
      }

      @Override
      protected String service() {
        return serviceName
      }

      @Override
      protected String component() {
        return "test-component"
      }

      @Override
      protected String spanType() {
        return "test-type"
      }

      @Override
      protected String dbType() {
        return "test-db"
      }

      @Override
      protected String dbUser(Map map) {
        return map.user
      }

      @Override
      protected String dbInstance(Map map) {
        return map.instance
      }
    }
  }
}
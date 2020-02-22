import com.google.common.reflect.ClassPath
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.SpockRunner
import io.opentelemetry.auto.test.utils.ClasspathUtils
import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.auto.tooling.Constants

import java.lang.reflect.Field
import java.util.concurrent.TimeoutException

import static io.opentelemetry.auto.api.Config.TRACE_CLASSES_EXCLUDE
import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

class AgentTestRunnerTest extends AgentTestRunner {
  private static final ClassLoader BOOTSTRAP_CLASSLOADER = null
  private static final boolean AGENT_INSTALLED_IN_CLINIT

  static {
    ConfigUtils.updateConfig {
      System.setProperty("opentelemetry.auto." + TRACE_CLASSES_EXCLUDE, "config.exclude.packagename.*, config.exclude.SomeClass,config.exclude.SomeClass\$NestedClass")
    }

    AGENT_INSTALLED_IN_CLINIT = getAgentTransformer() != null
  }

  def "spock runner bootstrap prefixes correct for test setup"() {
    expect:
    SpockRunner.BOOTSTRAP_PACKAGE_PREFIXES_COPY == Constants.BOOTSTRAP_PACKAGE_PREFIXES
  }

  def "classpath setup"() {
    setup:
    final List<String> bootstrapClassesIncorrectlyLoaded = []
    for (ClassPath.ClassInfo info : ClasspathUtils.getTestClasspath().getAllClasses()) {
      for (int i = 0; i < Constants.BOOTSTRAP_PACKAGE_PREFIXES.length; ++i) {
        if (info.getName().startsWith(Constants.BOOTSTRAP_PACKAGE_PREFIXES[i])) {
          Class<?> bootstrapClass = Class.forName(info.getName())
          def loader
          try {
            loader = bootstrapClass.getClassLoader()
          } catch (NoClassDefFoundError e) {
            // some classes in com.google.errorprone.annotations cause groovy to throw
            // java.lang.NoClassDefFoundError: [Ljavax/lang/model/element/Modifier;
            break
          }
          if (loader != BOOTSTRAP_CLASSLOADER) {
            bootstrapClassesIncorrectlyLoaded.add(bootstrapClass)
          }
          break
        }
      }
    }

    expect:
    !AGENT_INSTALLED_IN_CLINIT
    getAgentTransformer() != null
    bootstrapClassesIncorrectlyLoaded == []
  }

  def "waiting for child spans times out"() {
    when:
    runUnderTrace("parent") {
      TEST_WRITER.waitForTraces(1)
    }

    then:
    thrown(TimeoutException)
  }

  def "logging works"() {
    when:
    org.slf4j.LoggerFactory.getLogger(AgentTestRunnerTest).debug("hello")
    then:
    noExceptionThrown()
  }

  def "excluded classes are not instrumented"() {
    when:
    runUnderTrace("parent") {
      subject.run()
    }

    then:
    !TRANSFORMED_CLASSES.contains(subject.class.name)
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "parent"
        }
      }
    }

    where:
    subject                                                | _
    new config.exclude.SomeClass()                         | _
    new config.exclude.SomeClass.NestedClass()             | _
    new config.exclude.packagename.SomeClass()             | _
    new config.exclude.packagename.SomeClass.NestedClass() | _
  }

  def "test unblocked by completed span"() {
    setup:
    runUnderTrace("parent") {
      runUnderTrace("child") {}
    }

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
        }
        span(1) {
          operationName "child"
          childOf(span(0))
        }
      }
    }
  }

  private static getAgentTransformer() {
    Field f
    try {
      f = AgentTestRunner.getDeclaredField("activeTransformer")
      f.setAccessible(true)
      return f.get(null)
    } finally {
      f.setAccessible(false)
    }
  }
}

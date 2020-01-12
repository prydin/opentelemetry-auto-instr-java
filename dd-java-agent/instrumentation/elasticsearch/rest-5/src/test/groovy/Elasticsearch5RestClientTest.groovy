import com.anotherchrisberry.spock.extensions.retry.RetryOnFailure
import io.opentelemetry.auto.agent.test.AgentTestRunner
import io.opentelemetry.auto.agent.test.utils.PortUtils
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import groovy.json.JsonSlurper
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.apache.http.util.EntityUtils
import org.elasticsearch.client.Response
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.common.io.FileSystemUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.env.Environment
import org.elasticsearch.node.Node
import org.elasticsearch.node.internal.InternalSettingsPreparer
import org.elasticsearch.transport.Netty3Plugin
import spock.lang.Shared

import static org.elasticsearch.cluster.ClusterName.CLUSTER_NAME_SETTING

@RetryOnFailure(times = 3, delaySeconds = 1)
class Elasticsearch5RestClientTest extends AgentTestRunner {
  @Shared
  int httpPort
  @Shared
  int tcpPort
  @Shared
  Node testNode
  @Shared
  File esWorkingDir

  @Shared
  static RestClient client

  def setupSpec() {
    httpPort = PortUtils.randomOpenPort()
    tcpPort = PortUtils.randomOpenPort()

    esWorkingDir = File.createTempDir("test-es-working-dir-", "")
    esWorkingDir.deleteOnExit()
    println "ES work dir: $esWorkingDir"

    def settings = Settings.builder()
      .put("path.home", esWorkingDir.path)
      .put("http.port", httpPort)
      .put("transport.tcp.port", tcpPort)
      .put("transport.type", "netty3")
      .put("http.type", "netty3")
      .put(CLUSTER_NAME_SETTING.getKey(), "test-cluster")
      .build()
    testNode = new Node(new Environment(InternalSettingsPreparer.prepareSettings(settings)), [Netty3Plugin])
    testNode.start()

    client = RestClient.builder(new HttpHost("localhost", httpPort))
      .setMaxRetryTimeoutMillis(Integer.MAX_VALUE)
      .setRequestConfigCallback(new RestClientBuilder.RequestConfigCallback() {
        @Override
        RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder builder) {
          return builder.setConnectTimeout(Integer.MAX_VALUE).setSocketTimeout(Integer.MAX_VALUE)
        }
      })
      .build()

  }

  def cleanupSpec() {
    testNode?.close()
    if (esWorkingDir != null) {
      FileSystemUtils.deleteSubDirectories(esWorkingDir.toPath())
      esWorkingDir.delete()
    }
  }

  def "test elasticsearch status"() {
    setup:
    Response response = client.performRequest("GET", "_cluster/health")

    Map result = new JsonSlurper().parseText(EntityUtils.toString(response.entity))

    expect:
    result.status == "green"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "elasticsearch.rest.query"
          parent()
          tags {
            "$MoreTags.SERVICE_NAME" "elasticsearch"
            "$MoreTags.SPAN_TYPE" SpanTypes.ELASTICSEARCH
            "$Tags.COMPONENT" "elasticsearch-java"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME" "localhost"
            "$Tags.PEER_PORT" httpPort
            "$Tags.HTTP_URL" "_cluster/health"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.DB_TYPE" "elasticsearch"
          }
        }
        span(1) {
          operationName "http.request"
          childOf span(0)
          tags {
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "apache-httpasyncclient"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.HTTP_URL" "_cluster/health"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
          }
        }
      }
    }
  }
}

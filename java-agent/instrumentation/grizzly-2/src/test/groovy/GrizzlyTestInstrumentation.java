import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.agent.test.base.HttpServerTestAdvice;
import io.opentelemetry.auto.agent.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class GrizzlyTestInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder
        .type(named("org.glassfish.grizzly.http.server.HttpHandlerChain"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(named("doHandle"), HttpServerTestAdvice.ServerEntryAdvice.class.getName()));
  }
}
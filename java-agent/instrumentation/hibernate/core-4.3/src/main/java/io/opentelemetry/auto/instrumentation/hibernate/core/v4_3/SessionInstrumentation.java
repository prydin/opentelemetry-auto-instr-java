package io.opentelemetry.auto.instrumentation.hibernate.core.v4_3;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.hibernate.SessionMethodUtils;
import io.opentelemetry.auto.instrumentation.hibernate.SessionState;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.hibernate.SharedSessionContract;
import org.hibernate.procedure.ProcedureCall;

@AutoService(Instrumenter.class)
public class SessionInstrumentation extends Instrumenter.Default {

  public SessionInstrumentation() {
    super("hibernate", "hibernate-core");
  }

  @Override
  public Map<String, String> contextStore() {
    final Map<String, String> map = new HashMap<>();
    map.put("org.hibernate.SharedSessionContract", SessionState.class.getName());
    map.put("org.hibernate.procedure.ProcedureCall", SessionState.class.getName());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.instrumentation.hibernate.SessionMethodUtils",
      "io.opentelemetry.auto.instrumentation.hibernate.SessionState",
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.DatabaseClientDecorator",
      "io.opentelemetry.auto.decorator.OrmClientDecorator",
      "io.opentelemetry.auto.instrumentation.hibernate.HibernateDecorator",
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("org.hibernate.SharedSessionContract")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();

    transformers.put(
        isMethod().and(returns(safeHasSuperType(named("org.hibernate.procedure.ProcedureCall")))),
        SessionInstrumentation.class.getName() + "$GetProcedureCallAdvice");

    return transformers;
  }

  public static class GetProcedureCallAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void getProcedureCall(
        @Advice.This final SharedSessionContract session,
        @Advice.Return final ProcedureCall returned) {

      final ContextStore<SharedSessionContract, SessionState> sessionContextStore =
          InstrumentationContext.get(SharedSessionContract.class, SessionState.class);
      final ContextStore<ProcedureCall, SessionState> returnedContextStore =
          InstrumentationContext.get(ProcedureCall.class, SessionState.class);

      SessionMethodUtils.attachSpanFromStore(
          sessionContextStore, session, returnedContextStore, returned);
    }
  }
}

package io.opentelemetry.auto.test.utils

import io.opentelemetry.auto.api.Config
import lombok.SneakyThrows

import java.lang.reflect.Modifier
import java.util.concurrent.Callable

class ConfigUtils {

  static final CONFIG_INSTANCE_FIELD = Config.getDeclaredField("INSTANCE")

  @SneakyThrows
  synchronized static <T extends Object> Object withConfigOverride(final String name, final String value, final Callable<T> r) {
    // Ensure the class was retransformed properly in AgentSpecification.makeConfigInstanceModifiable()
    assert Modifier.isPublic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isStatic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isVolatile(CONFIG_INSTANCE_FIELD.getModifiers())
    assert !Modifier.isFinal(CONFIG_INSTANCE_FIELD.getModifiers())

    def existingConfig = Config.get()
    Properties properties = new Properties()
    properties.put(name, value)
    CONFIG_INSTANCE_FIELD.set(null, new Config(properties, existingConfig))
    assert Config.get() != existingConfig
    try {
      return r.call()
    } finally {
      CONFIG_INSTANCE_FIELD.set(null, existingConfig)
    }
  }

  /**
   * Provides an callback to set up the testing environment and reset the global configuration after system properties and envs are set.
   *
   * @param r
   * @return
   */
  static updateConfig(final Callable r) {
    r.call()
    resetConfig()
  }

  /**
   * Reset the global configuration. Please note that Runtime ID is preserved to the pre-existing value.
   */
  static void resetConfig() {
    // Ensure the class was re-transformed properly in AgentSpecification.makeConfigInstanceModifiable()
    assert Modifier.isPublic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isStatic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isVolatile(CONFIG_INSTANCE_FIELD.getModifiers())
    assert !Modifier.isFinal(CONFIG_INSTANCE_FIELD.getModifiers())

    def newConfig = new Config()
    CONFIG_INSTANCE_FIELD.set(null, newConfig)
  }
}

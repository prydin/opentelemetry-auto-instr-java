package io.opentelemetry.auto.test.utils

import okhttp3.OkHttpClient

import java.util.concurrent.TimeUnit

class OkHttpUtils {

  static clientBuilder() {
    def unit = TimeUnit.MINUTES
    new OkHttpClient.Builder()
      .connectTimeout(1, unit)
      .writeTimeout(1, unit)
      .readTimeout(1, unit)
  }

  static client(boolean followRedirects = false) {
    clientBuilder().followRedirects(followRedirects).build()
  }
}

package io.opentelemetry.auto.api;

public class MoreTags {
  public static final String SPAN_TYPE = "span.type";
  public static final String SERVICE_NAME = "service.name";
  public static final String RESOURCE_NAME = "resource.name";
  public static final String DB_STATEMENT = "sql.query";

  public static final String HTTP_QUERY = "http.query.string";
  public static final String HTTP_FRAGMENT = "http.fragment.string";

  public static final String USER_NAME = "user.principal";

  public static final String ERROR_MSG = "error.msg"; // string representing the error message
  public static final String ERROR_TYPE = "error.type"; // string representing the type of the error
  public static final String ERROR_STACK = "error.stack"; // human readable version of the stack
}

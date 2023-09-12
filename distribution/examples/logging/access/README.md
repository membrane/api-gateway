# Access Log Interceptor

The access log interceptor provides a set of common and useful exchange properties to use in a `log4j2.xml` pattern layout configuration.
The default implementation provides a [Apache Common Log](https://httpd.apache.org/docs/trunk/logs.html#common) pattern.

You can provide optional `<additionalPattern>` to extend or override the default configuration.

## Configuration

You can configure the following attibutes

### AccessLogInterceptor

The base interceptor

| Attribute | Use-Case | Default |
|-----------|----------|---------|
| defaultValue | if the value is not existent we log this character in place | - |
| dateTimePattern | format timestamps according to the provided pattern | dd/MM/yyyy:HH:mm:ss Z |
| excludePayloadSize | reading the payload size will disable streaming | false |

### AdditionalPattern

Provide optional pattern to the `AccessLogInterceptor`

| Key    | Value | Default | Required |
|--------|-------|---------|----------|
| create | the access key for the pattern layout | | x |
| withExchange | [SpEL](https://docs.spring.io/spring-framework/docs/3.2.x/spring-framework-reference/html/expressions.html) expression to access the Exchange object | | x |
| orDefaultValue | provide a default value if the value could not be retrieved from the Exchange | - | |
| override | toggle if the given access key is able to override existing access keys | true | |







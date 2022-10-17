# Spring Boot中使用RSocket

### 概述

`RSocket`应用层协议支持 `Reactive Streams`语义， 例如：用 RSocket 作为 HTTP 的一种替代方案。在本教程中， 我们将看到`RSocket`用在 Spring Boot 中，特别是 Spring Boot 如何帮助抽象出更低级别的 RSocket API 。

### 依赖

让我们从添加`spring-boot-starter-rsocket`依赖开始：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-rsocket</artifactId>
</dependency>
```

这个依赖会传递性的拉取`RSocket`相关的依赖，比如：`rsocket-core` 和 `rsocket-transport-netty`

### 示例程序

现在继续我们的简单应用程序。为了突出`RSocket`提供的交互模式，我打算创建一个交易应用程序， 交易应用程序包括客户端和服务器。

#### 服务端设置

首先，我们设置由springboot应用程序引导的`RSocket server`服务器。 因为有`spring-boot-starter-rsocket dependency`依赖，所以 Spring Boot 会自动配置`RSocket server`。
跟平常一样， 可以用属性驱动的方式修改`RSocket server`默认配置值。例如：通过增加如下配置在`application.properties`中，来修改`RSocket`端口：

```application.properties
# RSocket Server Port
spring.rsocket.server.port=9999
```

也可以根据需要进一步修改服务器的[其他属性](https://docs.spring.io/spring-boot/docs/2.2.0.M2/reference/html/appendix.html#rsocket-properties)

#### 客户端设置

接下来，我们来设置客户端，也是一个 Spring Boot 应用程序。虽然 Spring Boot 自动配置大部分 RSocket 相关的组件，但还要自定义一些对象来完成设置。

```java
@Configuration
public class ClientConfiguration {
    @Bean
    public RSocketRequester getRSocketRequester(){
        RSocketRequester.Builder builder = RSocketRequester.builder();

        return builder
                .rsocketConnector(
                        rSocketConnector ->
                                rSocketConnector.reconnect(Retry.fixedDelay(2, Duration.ofSeconds(2)))
                )
                .dataMimeType(MimeTypeUtils.APPLICATION_JSON)
                .rsocketStrategies(rsocketStrategies())
                .tcp("localhost", 9999);
    }

    @Bean
    public RSocketStrategies rsocketStrategies() {
        return RSocketStrategies.builder()
                .decoder(new Jackson2JsonDecoder())
                .encoder(new Jackson2JsonEncoder())
                // .dataBufferFactory(new DefaultDataBufferFactory(true))
                .build();
    }
}
```

注意这里的`rsocketStrategies`不能省略，原文的 Github 源码省略这个属性导致我在尝试的时候总是提示 `No decoder for xxx`，具体可见本文末尾给出的参考链接。

我们正在创建`RSocket`客户端并且配置TCP端口为：9999 。注意： 该服务端口我们在前面已经配置过。
接下来我们定义了一个RSocket的装饰器对象`RSocketRequester`。 这个对象在我们跟`RSocket server`交互时会为我们提供帮助。
定义这些对象配置后，我们还只是有了一个骨架。在接下来，我们将暴露不同的交互模式， 并看看 Spring Boot 在这个地方提供帮助的。

### `Spring Boot RSocket` 中的 `Request/Response`

我们从`Request/Response`开始，`HTTP`也使用这种通信方式，这也是最常见的、最相似的交互模式。
在这种交互模式里， 由客户端初始化通信并发送一个请求。之后，服务器端执行操作并返回一个响应给客户端--这时通信完成。
在我们的交易应用程序里， 一个客户端询问一个给定的股票的当前的市场数据。 作为回复，服务器会传递请求的数据。

#### 服务端

在服务器这边，我们首先应该创建一个`controller` 来持有我们的处理器方法。 我们会使用 `@MessageMapping`注解来代替像SpringMVC中的`@RequestMapping`或者`@GetMapping`注解：

```java
@Controller
public class MarketDataRSocketController {
    @Autowired
    private MarketDataRepository repository;

    @MessageMapping("currentMarketData")
    public Mono<MarketData> currentMarketData(MarketDataRequest request) {
        return repository.getOne(request.getStock());
    }
}
```

来研究下我们的控制器。 我们将使用`@Controller`注解来定义一个控制器来处理进入 RSocket 的请求。 另外，注解`@MessageMapping`让我们定义我们感兴趣的路由和如何响应一个请求。
在这个示例中， 服务器监听路由`currentMarketData`， 并响应一个单一的结果`Mono<MarketData>`给客户端。

#### 客户端

接下来， 我们的 RSocket 客户端应该询问一只股票的价格并得到一个单一的响应。 为了初始化请求， 我们该使用`RSocketRequester`类，如下：

```java
@RestController
public class MarketDataRestController {
    private final Random random = new Random();

    @Autowired
    private RSocketRequester rSocketRequester;

    @GetMapping(value = "/current/{stock}")
    public Publisher<MarketData> current(@PathVariable("stock") String stock) {
        return rSocketRequester.route("currentMarketData")
                .data(new MarketDataRequest(stock))
                .retrieveMono(MarketData.class);
    }    
}
```

注意：在示例中，`RSocket`客户端也是一个`REST`风格的`controller`，以此来访问我们的`RSocket`服务器。因此，我们使用`@RestController`和`@GetMapping`注解来定义我们的请求/响应端点。
在端点方法中， 我们使用的是类`RSocketRequester`并指定了路由。 事实上，这个是服务器端`RSocket`所期望的路由，然后我们传递请求数据。最后，当调用`retrieveMono()`方法时，Spring Boot 会帮我们初始化一个请求/响应交互。

### `Spring Boot RSocket`中的`Fire And Forget`模式

接下来我们将查看 `Fire And Forget`交互模式。正如名字提示的一样，客户端发送一个请求给服务器，但是不期望服务器的返回响应回来。 在我们的交易程序中， 一些客户端会作为数据资源服务，并且推送市场数据给服务器端。

#### 服务端

我们来创建另外一个端点在我们的服务器应用程序中，如下：

```java
@MessageMapping("collectMarketData")
public Mono<Void> collectMarketData(MarketData marketData) {
	repository.add(marketData);
	return Mono.empty();
}
```

我们又一次定义了一个新的`@MessageMapping`路由为`collectMarketData`。此外， Spring Boot 自动转换传入的负载为一个`MarketData`实例。
但是，这里最大的不同是我们返回一个`Mono<Void>`，因为客户端不需要服务器的返回。

#### 客户端

来看看我们如何初始化我们的`fire-and-forget`模式的请求。 我们将创建另外一个REST风格的端点，如下：

```java
@GetMapping(value = "/collect")
public Publisher<Void> collect() {
	return rSocketRequester.route("collectMarketData")
			.data(getMarketData())
			.send();
}
```

这里我们指定路由和负载将是一个`MarketData`实例。 由于我们使用`send()`方法来代替`retrieveMono()`，所有交互模式变成了`fire-and-forget`模式。

### `Spring Boot RSocket`中的`Request Stream`

请求流是一种更复杂的交互模式， 这个模式中客户端发送一个请求，但是在一段时间内从服务器端获取到多个响应。 为了模拟这种交互模式， 客户端会询问给定股票的所有市场数据。

#### 服务端

我们从服务器端开始。 我们将添加另外一个消息映射方法，如下：

```java
@MessageMapping("feedMarketData")
public Flux<MarketData> feedMarketData(MarketDataRequest marketDataRequest) {
	return repository.getAll(marketDataRequest.getStock());
}
```

正如所见， 这个处理器方法跟其他的处理器方法非常类似。 不同的部分是我们返回一个`Flux<MarketData>`来代替`Mono<MarketData>`。 最后我们的 RSocket 服务器会返回多个响应给客户端。

#### 客户端

在客户端这边， 我们该创建一个端点来初始化请求/响应通信，如下：

```java
@GetMapping(value = "/feed/{stock}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Publisher<MarketData> feed(@PathVariable("stock") String stock) {
	return rSocketRequester.route("feedMarketData")
			.data(new MarketDataRequest(stock))
			.retrieveFlux(MarketData.class);
}
```

我们来研究下 RSocket 请求。 首先我们定义了路由和请求负载。 然后，我们定义了使用`retrieveFlux()`调用的响应期望。这部分决定了交互模式。
另外注意：由于我们的客户端也是`REST`风格的服务器，客户端也定义了响应媒介类型`MediaType.TEXT_EVENT_STREAM_VALUE`。

### 异常处理

现在让我们看看在服务器程序中，如何以声明式的方式处理异常。 当处理`请求/响应`式， 我可以简单的使用`@MessageExceptionHandler`注解，如下：

```java
@MessageExceptionHandler
public Mono<MarketData> handleException(Exception e) {
	return Mono.just(MarketData.fromException(e));
}
```

这里我们给异常处理方法标记注解为`@MessageExceptionHandler`。作为结果， 这个方法将处理所有类型的异常， 因为`Exception`是所有其他类型的异常的超类。
我们也可以明确地创建更多的不同类型的，不同的异常处理方法。 这当然是请求/响应模式，并且我们返回的是`Mono<MarketData>`。我们期望这里的响应类型跟我们的交互模式的返回类型相匹配。

### 最后写点

客户端程序在 Windows 上运行处理请求时，会访问 hosts 文件，需要修改 hosts 文件给与读取权限，否则会抛出异常。

---

参考资料：

[Spring Boot中使用RSocket - 掘金 (juejin.cn)](https://juejin.cn/post/6844903860192935949)

[RSocket Using Spring Boot | Baeldung](https://www.baeldung.com/spring-boot-rsocket)

[springboot rsocket 2.2.0.RELEASE bug · Issue #18703 · spring-projects/spring-boot (github.com)](https://github.com/spring-projects/spring-boot/issues/18703)

[Spring rsocket not works properly in request Channel mode after version 2.5 of the spring framework · Issue #28462 · spring-projects/spring-framework (github.com)](https://github.com/spring-projects/spring-framework/issues/28462)

[election-spring-rsocket-with-rsocket-js/RSocketConfig.java at master · kasra-haghpanah/election-spring-rsocket-with-rsocket-js (github.com)](https://github.com/kasra-haghpanah/election-spring-rsocket-with-rsocket-js/blob/master/src/main/java/com/council/election/configuration/rsocket/RSocketConfig.java)

[han1448/spring-rsocket-example (github.com)](https://github.com/han1448/spring-rsocket-example)

package com.syuez.rsocketserver.controller;

import com.syuez.rsocketserver.model.MarketData;
import com.syuez.rsocketserver.model.MarketDataRequest;
import com.syuez.rsocketserver.repository.MarketDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
public class MarketDataRSocketController {
    @Autowired
    private MarketDataRepository repository;

    @MessageMapping("currentMarketData")
    public Mono<MarketData> currentMarketData(MarketDataRequest request) {
        return repository.getOne(request.getStock());
    }

    @MessageMapping("feedMarketData")
    public Flux<MarketData> feedMarketData(MarketDataRequest marketDataRequest) {
        return repository.getAll(marketDataRequest.getStock());
    }

    @MessageMapping("collectMarketData")
    public Mono<Void> collectMarketData(MarketData marketData) {
        repository.add(marketData);
        return Mono.empty();
    }

    @MessageExceptionHandler
    public Mono<MarketData> handleException(Exception e) {
        return Mono.just(MarketData.fromException(e));
    }
}

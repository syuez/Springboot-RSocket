package com.syuez.rsocketclient.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MarketDataRequest {
    private String stock;
}

package com.syuez.rsocketclient.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarketData {
    /**
     * 库存
     */
    private String stock;
    private int currentPrice;

    public static MarketData fromException(Exception e) {
        MarketData marketData = new MarketData();
        marketData.setStock(e.getMessage());
        return marketData;
    }
}

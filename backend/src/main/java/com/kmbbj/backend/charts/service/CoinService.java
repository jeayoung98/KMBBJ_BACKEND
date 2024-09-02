package com.kmbbj.backend.charts.service;

import com.kmbbj.backend.charts.dto.CoinResponse;
import com.kmbbj.backend.charts.entity.CoinStatus;
import com.kmbbj.backend.charts.entity.coin.Coin;
import org.springframework.data.domain.Page;

import java.util.List;

public interface CoinService {
    CoinResponse getCoinResponse(String symbol);
    List<CoinResponse> getCoinResponseList(List<Coin> coins);
    Page<CoinResponse> getPageCoins(int pageNo, int size, String orderBy, String sort, String name);
    void addCoin(String symbol, String coin_name);
    void deleteCoin(String symbol);
    void updateCoin(String symbol, CoinStatus status);
}

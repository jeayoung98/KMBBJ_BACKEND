package com.kmbbj.backend.charts.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kmbbj.backend.charts.dto.TradeSymbolPairDto;
import com.kmbbj.backend.charts.entity.coin.Coin;
import com.kmbbj.backend.charts.entity.coin.CoinDetail;
import com.kmbbj.backend.charts.repository.coin.CoinDetailRepository;
import com.kmbbj.backend.charts.repository.coin.CoinRepository;
import com.kmbbj.backend.global.config.exception.ApiException;
import com.kmbbj.backend.global.config.exception.ExceptionEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BinanceApiServiceImpl implements BinanceApiService {
    private final CoinDetailRepository coinDetailRepository;
    private final CoinRepository coinRepository;

    @Value("${RESTAPI_BINANCE_ACCESSKEY}")
    private String accessKey;

    private final WebClient webClient;

    /* 기본 Url 설정 */
    public BinanceApiServiceImpl(CoinDetailRepository coinDetailRepository, CoinRepository coinRepository, WebClient.Builder webClientBuilder) {
        this.coinRepository = coinRepository;
        this.coinDetailRepository = coinDetailRepository;
        // WebClient를 Binance API의 기본 URL로 빌드
        this.webClient = webClientBuilder.baseUrl("https://api.binance.com").build();
    }

    /**
     * 매개변수 조건에 해당하는 kline 데이터를 가져옴
     * @param symbol 코인의 심볼 (예: BTCUSDT, ETHUSDT)
     * @param interval 시간 간격
     * @param startTime 시작 시간 설정
     * @param endTime 종료 시간 설정
     * @param limit 받아올 klines 데이터 갯수 제한(기본 500, 최대 1000)
     * @return 매개변수의 조건에 해당하는 kline 데이터
     */
    @Override
    public Mono<String> getKlines(String symbol, String interval, Long startTime, Long endTime, Integer limit) {
        String endpoint = "/api/v3/klines";
        StringBuilder queryString = new StringBuilder();
        // 심볼과 간격을 쿼리 문자열에 추가
        queryString.append("symbol=").append(symbol)
                .append("&interval=").append(interval);
        // 시작 시간 설정이 있을 경우 쿼리 문자열에 추가
        if (startTime != null) {
            queryString.append("&startTime=").append(startTime);
        }
        // 종료 시간 설정이 있을 경우 쿼리 문자열에 추가
        if (endTime != null) {
            queryString.append("&endTime=").append(endTime);
        }
        // 데이터 갯수 제한 설정이 있을 경우 쿼리 문자열에 추가
        if (limit != null) {
            queryString.append("&limit=").append(limit);
        }

        // WebClient를 통해 JSON 데이터를 받아와서 String 형태로 변환하여 반환
        return getJsonToWebClientForSingleSymbol(endpoint, queryString).map(JsonArray::toString);
    }

    /**
     *  DB에 저장된 모든 코인의 데이터를 업데이트
     */
    @Override
    public void updateCoinData() {
        // 모든 코인 심볼을 가져와 USDT를 붙인 심볼 리스트를 생성
        List<String> symbols = coinRepository.findAll()
                .stream()
                .map(coin -> coin.getSymbol() + "USDT")
                .toList();

        // 최근 거래 내역을 저장할 List 생성
        List<Mono<TradeSymbolPairDto>> recentlyTradeMonos = new ArrayList<>();
        //  각 심볼에 대해 최근 거래 데이터를 가져와 TradeSymbolPairDto로 변환 후 리스트에 추가
        for (String symbol : symbols) {
            recentlyTradeMonos.add(getRecentlyTrade(symbol).map(trades -> new TradeSymbolPairDto(symbol, trades)));
        }

        // 모든 비동기 작업을 묶어서 처리
        Mono.zip(recentlyTradeMonos, trades -> {
            List<TradeSymbolPairDto> recentlyTradeResponses = new ArrayList<>();
            for (Object trade : trades) {
                recentlyTradeResponses.add((TradeSymbolPairDto) trade);
            }
            return recentlyTradeResponses;
        }).flatMap(recentlyTradeResponses -> {
            // 심볼 배열에 대한 bookTicker 데이터를 가져옴
            Mono<List<Map<String, Object>>> bookTickerMono = getBookTicker(symbols);
            return bookTickerMono.flatMap(bookTickerResponse -> {
                // 최근 거래 데이터와 bookTicker 데이터를 기반으로 코인 가격 데이터를 파싱
                List<CoinDetail> coinDetails = parseCoinData(recentlyTradeResponses, bookTickerResponse);
                coinDetailRepository.saveAll(coinDetails);
                return Mono.empty();
            });
        }).subscribe(); // 작업을 비동기적으로 실행
    }

    /**
     * 주어진 심볼에 대한 최근 거래 데이터를 가져옴
     * @param symbol 코인의 심볼 (예: BTCUSDT, ETHUSDT)
     * @return 최근 거래 데이터를 포함하는 Mono<JsonArray>
     */
    @Override
    public Mono<JsonArray> getRecentlyTrade(String symbol) {
        String endpoint = "/api/v3/trades";
        StringBuilder queryString = new StringBuilder();
        // 가장 최근의 거래내역 중 1개만 가져오기 위한 쿼리 문자열 생성
        queryString.append("symbol=").append(symbol).append("&limit=1");
        // WebClient를 통해 JSON 데이터를 받아옴
        return getJsonToWebClientForSingleSymbol(endpoint, queryString);
    }

    /**
     * 주어진 심볼 배열에 대한 현재의 매수 및 매도 호가를 확인할 수 있는 주문장부상의 최적 가격과 수량 데이터를 가져옴
     * @param symbols 코인의 심볼 배열 (예: BTCUSDT, ETHUSDT)
     * @return bookTicker 데이터를 포함하는 Mono<List<Map<String, Object>>>
     */
    @Override
    public Mono<List<Map<String, Object>>> getBookTicker(List<String> symbols) {
        String endpoint = "/api/v3/ticker/bookTicker";
        StringBuilder queryString = buildSymbolsQuery(symbols);

        return getJsonToWebClientForMultipleSymbols(endpoint, queryString);
    }

    /**
     * 최근 거래 응답과 bookTicker 응답을 기반으로 코인 데이터를 파싱
     * @param recentlyTradeResponses 최근 거래 응답 리스트
     * @param bookTickerResponse bookTicker 응답 리스트
     * @return 파싱된 코인 리스트
     */
    public List<CoinDetail> parseCoinData(List<TradeSymbolPairDto> recentlyTradeResponses, List<Map<String, Object>> bookTickerResponse) {
        List<CoinDetail> coinDetails = new ArrayList<>();
        Map<String, JsonObject> tradeMap = new HashMap<>();

        // 최근 거래 데이터를 심볼별로 매핑하여 tradeMap에 저장
        for (TradeSymbolPairDto pair : recentlyTradeResponses) {
            JsonArray trades = pair.getTrades();
            String symbol = pair.getSymbol();
            for (JsonElement tradeElement : trades) {
                JsonObject trade = tradeElement.getAsJsonObject();
                tradeMap.put(symbol, trade);
            }
        }

        // bookTicker 데이터를 이용하여 각 코인의 매수/매도 호가 및 거래 내역을 CoinDetail 객체에 설정
        for (Map<String, Object> bookTicker : bookTickerResponse) {
            String symbol = (String) bookTicker.get("symbol");
            String symbolWithoutUSDT = symbol.replace("USDT", "");
            // CoinDetail 객체 생성 및 설정
            CoinDetail coinDetail = new CoinDetail();
            Coin coin = coinRepository.findBySymbol(symbolWithoutUSDT).orElseThrow(() -> new ApiException(ExceptionEnum.NOT_FOUND_SYMBOL));
            coinDetail.setCoin(coin);
            coinDetail.setBidPrice(Double.parseDouble((String) bookTicker.get("bidPrice")));
            coinDetail.setBidQty(Double.parseDouble((String) bookTicker.get("bidQty")));
            coinDetail.setAskPrice(Double.parseDouble((String) bookTicker.get("askPrice")));
            coinDetail.setAskQty(Double.parseDouble((String) bookTicker.get("askQty")));

            // 최근 거래 데이터에서 가격 및 거래량을 설정
            JsonObject trade = tradeMap.get(symbol);
            if (trade != null) {
                double currentPrice = trade.get("price").getAsDouble();
                coinDetail.setPrice(currentPrice);
                coinDetail.setVotingAmount(trade.get("qty").getAsDouble());
            }

            coinDetails.add(coinDetail);
        }

        return coinDetails;
    }

    /**
     * 주어진 심볼 배열을 쿼리 문자열로 빌드
     * @param symbols 코인의 심볼 배열 (예: BTCUSDT, ETHUSDT)
     * @return 빌드된 쿼리 문자열
     */
    private StringBuilder buildSymbolsQuery(List<String> symbols) {
        StringBuilder queryString = new StringBuilder();
        // 심볼이 하나일 경우 단일 심볼 쿼리 생성
        if (symbols.size() == 1) {
            queryString.append("symbol=").append(symbols.getFirst());
        } else {
            // symbol이 여러개일 경우 symbol=["BTCUSDT","BNBUSDT"] 형태로 쿼리 문자열 생성
            queryString.append("symbols=");
            StringBuilder symbolsArray = new StringBuilder();
            symbolsArray.append("[");
            for (int i = 0; i < symbols.size(); i++) {
                symbolsArray.append("\"").append(symbols.get(i)).append("\"");
                if (i < symbols.size() - 1) {
                    symbolsArray.append(",");
                }
            }
            symbolsArray.append("]");
            queryString.append(symbolsArray);
        }
        return queryString;
    }

    /**
     * 주어진 엔드포인트와 쿼리 문자열에 대한 JSON 데이터를 가져옴(하나의 심볼)
     * @param endpoint 엔드포인트 URL
     * @param queryString 쿼리 문자열
     * @return JSON 데이터를 포함하는 Mono<JsonArray>
     */
    private Mono<JsonArray> getJsonToWebClientForSingleSymbol(String endpoint, StringBuilder queryString) {
        // WebClient를 통해 GET 요청을 보내고, 응답을 JsonArray로 변환하여 반환
        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path(endpoint)
                        .query(queryString.toString())
                        .build())
                .header("Content-Type", "application/json")
                .header("X-MBX-APIKEY", accessKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> JsonParser.parseString(response).getAsJsonArray());
    }

    /**
     * 주어진 엔드포인트와 쿼리 문자열에 대한 JSON 데이터를 가져옴(여러 심볼)
     * @param  endpoint 엔드포인트 URL
     * @param queryString 쿼리 문자열
     * @return JSON 데이터 리스트를 포함하는 Mono<List<Map<String, Object>>>
     */
    private Mono<List<Map<String, Object>>> getJsonToWebClientForMultipleSymbols(String endpoint, StringBuilder queryString) {
        // WebClient를 통해 GET 요청을 보내고, 응답을 JsonArray로 변환한 후, Map으로 변환하여 반환
        return this.webClient.get()
                .uri(uriBuilder -> uriBuilder.path(endpoint)
                        .query(queryString.toString())
                        .build())
                .header("Content-Type", "application/json")
                .header("X-MBX-APIKEY", accessKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    JsonArray jsonArray = JsonParser.parseString(response).getAsJsonArray();
                    List<Map<String, Object>> jsonObjectList = new ArrayList<>();
                    jsonArray.forEach(jsonElement -> jsonObjectList.add(convertJsonObjectToMap(jsonElement.getAsJsonObject())));
                    return jsonObjectList;
                });
    }

    /**
     * JsonObject를 Map<String, Object>로 변환
     * @param jsonObject 변환할 JsonObject
     * @return 변환된 Map<String, Object>
     */
    private Map<String, Object> convertJsonObjectToMap(JsonObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            JsonElement value = entry.getValue();
            //value.isJsonPrimitive 메서드를 통해 JsonElement가 원시 타입자열(String), 숫자(Number), 논리 값(Boolean)인지 체크하고,
            // 맞다면 각 타입에 맞게 변환하여 맵에 넣습니다.
            if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isString()) {
                    map.put(entry.getKey(), value.getAsString());
                } else if (value.getAsJsonPrimitive().isNumber()) {
                    map.put(entry.getKey(), value.getAsNumber());
                }
            //JsonElement가 JSON 객체(JsonObject)인 경우, convertJsonObjectToMap 메서드를 재귀적으로 호출하여 해당 객체를 다시 Map<String, Object>로 변환합니다.
            } else if (value.isJsonObject()) {
                map.put(entry.getKey(), convertJsonObjectToMap(value.getAsJsonObject()));
            } //JsonElement가 JSON 배열(JsonArray)인 경우, 해당 배열을 그대로 Map에 추가합니다.
            else if (value.isJsonArray()) {
                map.put(entry.getKey(), value.getAsJsonArray());
            } //JsonElement가 JSON null 값을 나타내는 경우, Map에 null을 값으로 추가합니다.
            else if (value.isJsonNull()) {
                map.put(entry.getKey(), null);
            }
        }
        return map;
    }
}
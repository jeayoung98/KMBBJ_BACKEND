package com.kmbbj.backend.feature.exchange.service;

import com.kmbbj.backend.feature.exchange.controller.request.CanselRequest;
import com.kmbbj.backend.feature.exchange.controller.request.OrderRequest;
import com.kmbbj.backend.feature.exchange.service.buy.save.SaveBuyOrder;
import com.kmbbj.backend.feature.exchange.service.cansel.CanselOrder;
import com.kmbbj.backend.feature.exchange.service.execution.matching.ExecutionAllMatchingOrder;
import com.kmbbj.backend.feature.exchange.service.sell.save.SaveSellOrder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TransactionServiceImpl implements TransactionService {
    private final SaveSellOrder saveSellOrder;
    private final SaveBuyOrder saveBuyOrder;
    private final CanselOrder canselOrder;
    private final ExecutionAllMatchingOrder executionAllMatchingOrder;

    public TransactionServiceImpl(@Qualifier("saveSellOrderImpl") SaveSellOrder saveSellOrder,
                                  @Qualifier("saveBuyOrderImpl") SaveBuyOrder saveBuyOrder,
                                  @Qualifier("canselOrderImpl") CanselOrder canselOrder,
                                  @Qualifier("executionAllMatchingOrderImpl") ExecutionAllMatchingOrder executionAllMatchingOrder) {
        this.saveSellOrder = saveSellOrder;
        this.saveBuyOrder = saveBuyOrder;
        this.canselOrder = canselOrder;
        this.executionAllMatchingOrder = executionAllMatchingOrder;
    }

    @Override
    public void saveBuyOrder(OrderRequest orderRequest) {
        saveBuyOrder.saveBuyOrder(orderRequest);
    }

    @Override
    public void saveSellOrder(OrderRequest orderRequest) {
        saveSellOrder.saveSellOrder(orderRequest);
    }

    @Override
    public void canselOrder(CanselRequest canselRequest) {
        canselOrder.canselOrder(canselRequest);
    }

    @Override
    public void matchOrders(Long coinId, BigDecimal price) {
        executionAllMatchingOrder.matchOrders(coinId, price);
    }
}
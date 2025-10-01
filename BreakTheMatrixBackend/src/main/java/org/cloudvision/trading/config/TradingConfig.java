package org.cloudvision.trading.config;

import org.cloudvision.trading.provider.TradingDataProvider;
import org.cloudvision.trading.service.UniversalTradingDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Configuration
public class TradingConfig {

    @Autowired
    private UniversalTradingDataService tradingService;

    @Autowired
    private List<TradingDataProvider> providers;

    @PostConstruct
    public void registerProviders() {
        providers.forEach(tradingService::registerProvider);
        System.out.println("Auto-registered " + providers.size() + " trading providers");
    }
}

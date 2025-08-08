package com.app.heartbound.controllers;

import com.app.heartbound.config.security.RateLimited;
import com.app.heartbound.config.security.Views;
import com.app.heartbound.dto.CreateTradeDto;
import com.app.heartbound.entities.Trade;
import com.app.heartbound.enums.RateLimitKeyType;
import com.app.heartbound.services.TradeService;
import com.fasterxml.jackson.annotation.JsonView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/trades")
@PreAuthorize("isAuthenticated()")
public class TradeController {

    private static final Logger logger = LoggerFactory.getLogger(TradeController.class);
    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping
    @RateLimited(requestsPerMinute = 10, keyType = RateLimitKeyType.USER, keyPrefix = "trade_create")
    public ResponseEntity<Trade> createTrade(@Valid @RequestBody CreateTradeDto tradeDto, Authentication authentication) {
        String initiatorId = getCurrentUserId(authentication);
        logger.info("User {} is creating a trade with user {}", initiatorId, tradeDto.getReceiverId());
        Trade trade = tradeService.createTrade(tradeDto, initiatorId);
        return ResponseEntity.ok(trade);
    }

    @PostMapping("/{id}/accept")
    @RateLimited(requestsPerMinute = 15, keyType = RateLimitKeyType.USER, keyPrefix = "trade_accept")
    public ResponseEntity<Trade> acceptFinalTrade(@PathVariable Long id, Authentication authentication) {
        String currentUserId = getCurrentUserId(authentication);
        logger.info("User {} is accepting trade {}", currentUserId, id);
        Trade trade = tradeService.acceptFinalTrade(id, currentUserId);
        return ResponseEntity.ok(trade);
    }

    @PostMapping("/{id}/decline")
    @RateLimited(requestsPerMinute = 15, keyType = RateLimitKeyType.USER, keyPrefix = "trade_decline")
    public ResponseEntity<Trade> declineTrade(@PathVariable Long id, Authentication authentication) {
        String currentUserId = getCurrentUserId(authentication);
        logger.info("User {} is declining trade {}", currentUserId, id);
        Trade trade = tradeService.declineTrade(id, currentUserId);
        return ResponseEntity.ok(trade);
    }

    @PostMapping("/{id}/cancel")
    @RateLimited(requestsPerMinute = 15, keyType = RateLimitKeyType.USER, keyPrefix = "trade_cancel")
    public ResponseEntity<Trade> cancelTrade(@PathVariable Long id, Authentication authentication) {
        String currentUserId = getCurrentUserId(authentication);
        logger.info("User {} is canceling trade {}", currentUserId, id);
        Trade trade = tradeService.cancelTrade(id, currentUserId);
        return ResponseEntity.ok(trade);
    }

    @GetMapping("/{id}")
    @JsonView(Views.Public.class)
    @PostAuthorize("hasRole('ADMIN') or returnObject.body.initiator.id == authentication.name or returnObject.body.receiver.id == authentication.name")
    public ResponseEntity<Trade> getTradeDetails(@PathVariable Long id, Authentication authentication) {
        String currentUserId = getCurrentUserId(authentication);
        Trade trade = tradeService.getTradeDetails(id);
        logger.debug("User {} viewing details for trade {}", currentUserId, id);
        return ResponseEntity.ok(trade);
    }

    @GetMapping
    @JsonView(Views.Public.class)
    public ResponseEntity<List<Trade>> getUserTrades(Authentication authentication) {
        String currentUserId = getCurrentUserId(authentication);
        logger.debug("Fetching trades for user {}", currentUserId);
        List<Trade> trades = tradeService.getUserTrades(currentUserId);
        return ResponseEntity.ok(trades);
    }

    // Helper to standardize current user resolution and enable future enhancement (e.g., principal types)
    private String getCurrentUserId(Authentication authentication) {
        return authentication.getName();
    }
}
package com.app.heartbound.controllers;

import com.app.heartbound.config.security.RateLimited;
import com.app.heartbound.dto.CreateTradeDto;
import com.app.heartbound.entities.Trade;
import com.app.heartbound.enums.RateLimitKeyType;
import com.app.heartbound.exceptions.UnauthorizedOperationException;
import com.app.heartbound.services.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
        String initiatorId = authentication.getName();
        logger.info("User {} is creating a trade with user {}", initiatorId, tradeDto.getReceiverId());
        Trade trade = tradeService.createTrade(tradeDto, initiatorId);
        return ResponseEntity.ok(trade);
    }

    @PostMapping("/{id}/accept")
    @RateLimited(requestsPerMinute = 15, keyType = RateLimitKeyType.USER, keyPrefix = "trade_accept")
    public ResponseEntity<Trade> acceptFinalTrade(@PathVariable Long id, Authentication authentication) {
        String currentUserId = authentication.getName();
        logger.info("User {} is accepting trade {}", currentUserId, id);
        Trade trade = tradeService.acceptFinalTrade(id, currentUserId);
        return ResponseEntity.ok(trade);
    }

    @PostMapping("/{id}/decline")
    @RateLimited(requestsPerMinute = 15, keyType = RateLimitKeyType.USER, keyPrefix = "trade_decline")
    public ResponseEntity<Trade> declineTrade(@PathVariable Long id, Authentication authentication) {
        String currentUserId = authentication.getName();
        logger.info("User {} is declining trade {}", currentUserId, id);
        Trade trade = tradeService.declineTrade(id, currentUserId);
        return ResponseEntity.ok(trade);
    }

    @PostMapping("/{id}/cancel")
    @RateLimited(requestsPerMinute = 15, keyType = RateLimitKeyType.USER, keyPrefix = "trade_cancel")
    public ResponseEntity<Trade> cancelTrade(@PathVariable Long id, Authentication authentication) {
        String currentUserId = authentication.getName();
        logger.info("User {} is canceling trade {}", currentUserId, id);
        Trade trade = tradeService.cancelTrade(id, currentUserId);
        return ResponseEntity.ok(trade);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Trade> getTradeDetails(@PathVariable Long id, Authentication authentication) {
        String currentUserId = authentication.getName();
        Trade trade = tradeService.getTradeDetails(id);

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !trade.getInitiator().getId().equals(currentUserId) && !trade.getReceiver().getId().equals(currentUserId)) {
            logger.warn("Unauthorized access attempt on trade {} by user {}", id, currentUserId);
            throw new UnauthorizedOperationException("You do not have permission to view this trade.");
        }

        logger.debug("User {} viewing details for trade {}", currentUserId, id);
        return ResponseEntity.ok(trade);
    }

    @GetMapping
    public ResponseEntity<List<Trade>> getUserTrades(Authentication authentication) {
        String currentUserId = authentication.getName();
        logger.debug("Fetching trades for user {}", currentUserId);
        List<Trade> trades = tradeService.getUserTrades(currentUserId);
        return ResponseEntity.ok(trades);
    }
} 
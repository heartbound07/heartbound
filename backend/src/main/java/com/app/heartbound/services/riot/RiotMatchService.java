package com.app.heartbound.services.riot;

import com.app.heartbound.dto.riot.RiotMatchDto;
import com.app.heartbound.dto.riot.RiotMatchlistDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with Riot Games Valorant Match API.
 * Handles fetching match history and match details while respecting rate limits.
 */
@Service
public class RiotMatchService {
    private static final Logger logger = LoggerFactory.getLogger(RiotMatchService.class);
    
    private static final String VALORANT_API_BASE_URL = "https://api.riotgames.com";
    private static final String MATCH_HISTORY_ENDPOINT = "/val/match/v1/matchlists/by-puuid/{puuid}";
    private static final String MATCH_DETAILS_ENDPOINT = "/val/match/v1/matches/{matchId}";
    
    private final RestTemplate restTemplate;
    
    @Value("${riot.api.key}")
    private String riotApiKey;
    
    // Rate limit tracking
    private final Map<String, Instant> methodRateLimitTimers = new HashMap<>();
    private final Map<String, Integer> methodRateLimitCounters = new HashMap<>();
    
    public RiotMatchService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Fetches recent match history for a player.
     *
     * @param puuid The player's PUUID
     * @param limit Maximum number of matches to return (optional)
     * @return Optional containing match history if successful, empty otherwise
     */
    public Optional<RiotMatchlistDto> getPlayerMatchHistory(String puuid, Integer limit) {
        if (puuid == null || puuid.isEmpty()) {
            logger.warn("Attempted to fetch match history with empty PUUID");
            return Optional.empty();
        }
        
        String endpoint = MATCH_HISTORY_ENDPOINT.replace("{puuid}", puuid);
        String methodKey = "match-history";
        
        // Check if we're within rate limits
        if (!canMakeRequest(methodKey)) {
            logger.warn("Rate limit exceeded for match history endpoint. Backing off.");
            return Optional.empty();
        }
        
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(VALORANT_API_BASE_URL + endpoint);
            
            // Add limit parameter if provided
            if (limit != null && limit > 0) {
                builder.queryParam("size", limit);
            }
            
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<RiotMatchlistDto> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    RiotMatchlistDto.class
            );
            
            incrementRequestCount(methodKey);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            } else {
                logger.warn("Failed to fetch match history for PUUID: {}. Status: {}", puuid, response.getStatusCode());
                return Optional.empty();
            }
        } catch (HttpClientErrorException e) {
            handleRiotApiError(e, "match history", puuid);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error fetching match history for PUUID {}: {}", puuid, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Fetches detailed information about a specific match.
     *
     * @param matchId The match ID
     * @return Optional containing match details if successful, empty otherwise
     */
    public Optional<RiotMatchDto> getMatchDetails(String matchId) {
        if (matchId == null || matchId.isEmpty()) {
            logger.warn("Attempted to fetch match details with empty match ID");
            return Optional.empty();
        }
        
        String endpoint = MATCH_DETAILS_ENDPOINT.replace("{matchId}", matchId);
        String methodKey = "match-details";
        
        // Check if we're within rate limits
        if (!canMakeRequest(methodKey)) {
            logger.warn("Rate limit exceeded for match details endpoint. Backing off.");
            return Optional.empty();
        }
        
        try {
            HttpEntity<String> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<RiotMatchDto> response = restTemplate.exchange(
                    VALORANT_API_BASE_URL + endpoint,
                    HttpMethod.GET,
                    entity,
                    RiotMatchDto.class
            );
            
            incrementRequestCount(methodKey);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            } else {
                logger.warn("Failed to fetch details for Match ID: {}. Status: {}", matchId, response.getStatusCode());
                return Optional.empty();
            }
        } catch (HttpClientErrorException e) {
            handleRiotApiError(e, "match details", matchId);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error fetching details for Match ID {}: {}", matchId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Creates HTTP headers with the Riot API key.
     * 
     * @return HttpHeaders with the X-Riot-Token header
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Riot-Token", riotApiKey);
        return headers;
    }
    
    /**
     * Handles Riot API errors and logs appropriate messages.
     * 
     * @param e The HTTP exception
     * @param context The context of the request (e.g., "match history")
     * @param identifier The identifier (e.g., puuid or matchId)
     */
    private void handleRiotApiError(HttpClientErrorException e, String context, String identifier) {
        switch (e.getStatusCode().value()) {
            case 400:
                logger.error("Bad request when fetching {} for {}: {}", context, identifier, e.getResponseBodyAsString());
                break;
            case 401:
                logger.error("Unauthorized - API key invalid or expired when fetching {} for {}", context, identifier);
                break;
            case 403:
                logger.error("Forbidden - API key doesn't have permission for {} endpoint", context);
                break;
            case 404:
                logger.info("{} not found for identifier: {}", context, identifier);
                break;
            case 429:
                logger.warn("Rate limit exceeded when fetching {} for {}. Response: {}", 
                        context, identifier, e.getResponseBodyAsString());
                // Extract retry-after if present and implement backoff logic
                String retryAfter = e.getResponseHeaders().getFirst("Retry-After");
                if (retryAfter != null) {
                    try {
                        int seconds = Integer.parseInt(retryAfter);
                        logger.info("API suggests waiting {} seconds before retrying", seconds);
                    } catch (NumberFormatException nfe) {
                        logger.warn("Could not parse Retry-After header: {}", retryAfter);
                    }
                }
                break;
            case 500:
            case 502:
            case 503:
            case 504:
                logger.error("Riot API server error ({}) when fetching {} for {}: {}", 
                        e.getStatusCode().value(), context, identifier, e.getResponseBodyAsString());
                break;
            default:
                logger.error("HTTP error {} when fetching {} for {}: {}", 
                        e.getStatusCode().value(), context, identifier, e.getResponseBodyAsString());
        }
    }
    
    /**
     * Simple implementation of rate limit tracking.
     * In a production environment, consider using a more robust solution like Resilience4j.
     * 
     * @param methodKey The method key to check
     * @return true if a request can be made, false otherwise
     */
    private synchronized boolean canMakeRequest(String methodKey) {
        // Example limits (adjust based on your production API key limits)
        final int METHOD_LIMIT = 30; // 30 requests per minute
        final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
        
        Instant now = Instant.now();
        Instant lastRequestTime = methodRateLimitTimers.getOrDefault(methodKey, Instant.EPOCH);
        int count = methodRateLimitCounters.getOrDefault(methodKey, 0);
        
        // If window has expired, reset counter
        if (Duration.between(lastRequestTime, now).compareTo(RATE_LIMIT_WINDOW) > 0) {
            methodRateLimitCounters.put(methodKey, 0);
            methodRateLimitTimers.put(methodKey, now);
            return true;
        }
        
        // Check if we've hit the limit
        return count < METHOD_LIMIT;
    }
    
    /**
     * Increments the request count for a method.
     * 
     * @param methodKey The method key
     */
    private synchronized void incrementRequestCount(String methodKey) {
        Instant now = Instant.now();
        methodRateLimitTimers.put(methodKey, now);
        int currentCount = methodRateLimitCounters.getOrDefault(methodKey, 0);
        methodRateLimitCounters.put(methodKey, currentCount + 1);
    }
} 
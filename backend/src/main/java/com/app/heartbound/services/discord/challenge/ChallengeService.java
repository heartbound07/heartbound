package com.app.heartbound.services.discord.challenge;

import com.app.heartbound.entities.challenge.ChallengeParticipant;
import com.app.heartbound.repositories.challenge.ChallengeParticipantRepository;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final ChallengeParticipantRepository challengeParticipantRepository;
    private static final String CURRENT_CHALLENGE_PERIOD = "july-2024";

    private static final Map<String, String> TEAM_ROLES = Map.of(
            "1396652642888974407", "Team 1",
            "1396652738116452382", "Team 2",
            "1396652757670170627", "Team 3",
            "1396652786334302318", "Team 4",
            "1396652786908926002", "Team 5",
            "1396652830512644177", "Team 6",
            "1396652834472202372", "Team 7"
    );

    @Transactional
    public void incrementMessageCount(String userId, String teamId, String teamName) {
        ChallengeParticipant participant = challengeParticipantRepository
                .findByUserIdAndChallengePeriod(userId, CURRENT_CHALLENGE_PERIOD)
                .orElseGet(() -> ChallengeParticipant.builder()
                        .userId(userId)
                        .teamId(teamId)
                        .teamName(teamName)
                        .challengePeriod(CURRENT_CHALLENGE_PERIOD)
                        .messageCount(0L)
                        .build());

        participant.setMessageCount(participant.getMessageCount() + 1);
        challengeParticipantRepository.save(participant);
    }

    public List<TeamLeaderboardEntry> getTeamLeaderboard() {
        return challengeParticipantRepository.findTeamMessageCounts(CURRENT_CHALLENGE_PERIOD)
                .stream()
                .map(result -> new TeamLeaderboardEntry((String) result[0], (Long) result[1]))
                .collect(Collectors.toList());
    }

    public List<ChallengeParticipantDTO> getUserLeaderboardForTeam(String teamId) {
        return challengeParticipantRepository.findUserLeaderboardForTeam(teamId, CURRENT_CHALLENGE_PERIOD);
    }

    public Optional<TeamInfo> getMemberTeam(Member member) {
        if (member == null) {
            return Optional.empty();
        }
        for (Role role : member.getRoles()) {
            if (TEAM_ROLES.containsKey(role.getId())) {
                return Optional.of(new TeamInfo(role.getId(), TEAM_ROLES.get(role.getId())));
            }
        }
        return Optional.empty();
    }

    public List<String> getTeamIds() {
        return Arrays.asList("1396652642888974407", "1396652738116452382", "1396652757670170627", "1396652786334302318", "1396652786908926002", "1396652830512644177", "1396652834472202372");
    }

    public String getTeamNameById(String teamId) {
        return TEAM_ROLES.get(teamId);
    }
    
    public record TeamInfo(String teamId, String teamName) {
    }

    public record TeamLeaderboardEntry(String teamName, long totalMessageCount) {
    }

    public record ChallengeParticipantDTO(String userId, long messageCount, String username, String displayName) {
    }
} 
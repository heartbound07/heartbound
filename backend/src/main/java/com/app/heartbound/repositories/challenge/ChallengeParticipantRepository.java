package com.app.heartbound.repositories.challenge;

       import com.app.heartbound.dto.ChallengeParticipantDTO;
import com.app.heartbound.entities.challenge.ChallengeParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, Long> {

    Optional<ChallengeParticipant> findByUserIdAndChallengePeriod(String userId, String challengePeriod);

    @Query("SELECT new com.app.heartbound.dto.ChallengeParticipantDTO(cp.userId, cp.messageCount, u.username, u.displayName) " +
           "FROM ChallengeParticipant cp JOIN User u ON cp.userId = u.id " +
           "WHERE cp.teamId = :teamId AND cp.challengePeriod = :challengePeriod " +
           "ORDER BY cp.messageCount DESC")
    List<ChallengeParticipantDTO> findUserLeaderboardForTeam(@Param("teamId") String teamId, @Param("challengePeriod") String challengePeriod);

    List<ChallengeParticipant> findByTeamIdAndChallengePeriodOrderByMessageCountDesc(String teamId, String challengePeriod);

    @Query("SELECT cp.teamName as teamName, SUM(cp.messageCount) as totalMessageCount " +
           "FROM ChallengeParticipant cp " +
           "WHERE cp.challengePeriod = :challengePeriod " +
           "GROUP BY cp.teamName " +
           "ORDER BY totalMessageCount DESC")
    List<Object[]> findTeamMessageCounts(@Param("challengePeriod") String challengePeriod);
} 
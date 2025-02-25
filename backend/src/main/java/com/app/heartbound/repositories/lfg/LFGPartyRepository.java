package com.app.heartbound.repositories.lfg;

import com.app.heartbound.entities.LFGParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LFGPartyRepository extends JpaRepository<LFGParty, UUID>, JpaSpecificationExecutor<LFGParty> {
    // Additional derived query methods can be added here if needed.
}

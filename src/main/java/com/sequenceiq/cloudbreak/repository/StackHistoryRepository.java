package com.sequenceiq.cloudbreak.repository;

import com.sequenceiq.cloudbreak.domain.StackHistory;
import org.springframework.data.repository.CrudRepository;

public interface StackHistoryRepository extends CrudRepository<StackHistory, Long> {

}

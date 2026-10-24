package it.glknet.financial_manager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import it.glknet.financial_manager.model.SharedGroupMember;

public interface SharedGroupMemberRepository extends JpaRepository<SharedGroupMember, UUID> {

	List<SharedGroupMember> findByUserId(UUID userId);

	List<SharedGroupMember> findByGroupIdOrderByJoinedAtAsc(UUID groupId);

	boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

	void deleteByGroupIdAndUserId(UUID groupId, UUID userId);

	void deleteByGroupId(UUID groupId);
}

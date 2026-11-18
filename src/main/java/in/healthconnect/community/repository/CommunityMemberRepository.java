package in.healthconnect.community.repository;

import in.healthconnect.community.dto.MemberDto;
import in.healthconnect.community.entity.CommunityMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, Integer> {

    Optional<CommunityMember> findByCommunityIdAndUserId(Integer communityId, Integer userId);

    boolean existsByCommunityIdAndUserId(Integer communityId, Integer userId);

    @Query("select m.userId from CommunityMember m where m.communityId = :communityId")
    List<Integer> findUserIds(@Param("communityId") Integer communityId);

    // Everyone who shares at least one community with this user (for presence fan-out).
    @Query("""
            select distinct m2.userId from CommunityMember m1
            join CommunityMember m2 on m2.communityId = m1.communityId
            where m1.userId = :userId and m2.userId <> :userId""")
    List<Integer> findCoMemberUserIds(@Param("userId") Integer userId);

    @Query("""
            select new in.healthconnect.community.dto.MemberDto(m.userId, u.username,
                coalesce(concat(d.firstName, ' ', d.lastName), concat(p.firstName, ' ', p.lastName), u.username), m.role)
            from CommunityMember m join User u on u.id = m.userId
                left join Doctor d on d.id = u.doctorId
                left join Patient p on p.id = u.patientId
            where m.communityId = :communityId
            order by m.role, u.username""")
    List<MemberDto> findMembers(@Param("communityId") Integer communityId);

    // Move the read marker forward only (never backwards), which clears the unread badge.
    @Modifying
    @Query("""
            update CommunityMember m set m.lastReadMessageId = :messageId
            where m.communityId = :communityId and m.userId = :userId
              and (m.lastReadMessageId is null or m.lastReadMessageId < :messageId)""")
    void markRead(@Param("communityId") Integer communityId, @Param("userId") Integer userId,
                  @Param("messageId") Integer messageId);
}

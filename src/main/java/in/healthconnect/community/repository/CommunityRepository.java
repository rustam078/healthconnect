package in.healthconnect.community.repository;

import in.healthconnect.community.dto.CommunityDto;
import in.healthconnect.community.entity.Community;
import in.healthconnect.community.entity.enums.CommunityVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommunityRepository extends JpaRepository<Community, Integer> {

    /** The user's communities with member count and unread count (non-system messages from others), in one query. */
    @Query("""
            select new in.healthconnect.community.dto.CommunityDto(c.id, c.name, c.description, c.visibility, m.role,
                (select count(m2) from CommunityMember m2 where m2.communityId = c.id),
                (select count(msg) from ChatMessage msg where msg.communityId = c.id
                    and msg.type <> in.healthconnect.community.entity.enums.MessageType.SYSTEM
                    and msg.senderId <> :userId and msg.id > coalesce(m.lastReadMessageId, 0)))
            from Community c join CommunityMember m on m.communityId = c.id
            where m.userId = :userId
            order by c.id""")
    List<CommunityDto> findCommunitiesOfUser(@Param("userId") Integer userId);

    /** Groups the user may discover and join but is not in yet, limited to the visibilities their account can see. */
    @Query("""
            select new in.healthconnect.community.dto.CommunityDto(c.id, c.name, c.description, c.visibility,
                (select count(m2) from CommunityMember m2 where m2.communityId = c.id))
            from Community c
            where c.visibility in :visibilities
              and not exists (select 1 from CommunityMember m where m.communityId = c.id and m.userId = :userId)
            order by c.id""")
    List<CommunityDto> findDiscoverable(@Param("userId") Integer userId,
                                        @Param("visibilities") Collection<CommunityVisibility> visibilities);
}

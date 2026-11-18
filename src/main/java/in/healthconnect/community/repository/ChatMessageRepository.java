package in.healthconnect.community.repository;

import in.healthconnect.community.dto.ChatMessageDto;
import in.healthconnect.community.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Integer> {

    @Query("""
            select new in.healthconnect.community.dto.ChatMessageDto(
                msg.id, msg.communityId, msg.senderId,
                coalesce(concat(d.firstName, ' ', d.lastName), concat(p.firstName, ' ', p.lastName), u.username),
                u.avatarFileId, msg.type, msg.body, msg.attachmentId, sf.originalFilename, msg.createdAt)
            from ChatMessage msg join User u on u.id = msg.senderId
                left join Doctor d on d.id = u.doctorId
                left join Patient p on p.id = u.patientId
                left join StoredFile sf on sf.id = msg.attachmentId
            where msg.communityId = :communityId
            order by msg.id desc""")
    List<ChatMessageDto> findLatest(@Param("communityId") Integer communityId, Pageable pageable);

    @Query("""
            select new in.healthconnect.community.dto.ChatMessageDto(
                msg.id, msg.communityId, msg.senderId,
                coalesce(concat(d.firstName, ' ', d.lastName), concat(p.firstName, ' ', p.lastName), u.username),
                u.avatarFileId, msg.type, msg.body, msg.attachmentId, sf.originalFilename, msg.createdAt)
            from ChatMessage msg join User u on u.id = msg.senderId
                left join Doctor d on d.id = u.doctorId
                left join Patient p on p.id = u.patientId
                left join StoredFile sf on sf.id = msg.attachmentId
            where msg.communityId = :communityId and msg.id < :beforeId
            order by msg.id desc""")
    List<ChatMessageDto> findBefore(@Param("communityId") Integer communityId,
                                    @Param("beforeId") Integer beforeId, Pageable pageable);

    Optional<ChatMessage> findByIdAndCommunityId(Integer id, Integer communityId);
}

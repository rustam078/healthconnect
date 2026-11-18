package in.healthconnect.community.service;

import in.healthconnect.community.repository.CommunityMemberRepository;
import in.healthconnect.storage.entity.StoredFile;
import in.healthconnect.storage.entity.enums.FileCategory;
import in.healthconnect.storage.service.FileAccessGuard;
import org.springframework.stereotype.Component;

// A chat attachment may be read only by a member of the community it was posted in.
@Component
public class CommunityChatFileGuard implements FileAccessGuard {

    private final CommunityMemberRepository memberRepository;

    public CommunityChatFileGuard(CommunityMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public FileCategory category() {
        return FileCategory.COMMUNITY_CHAT;
    }

    @Override
    public boolean canAccess(StoredFile file, Integer userId) {
        return file.getReferenceId() != null
                && memberRepository.existsByCommunityIdAndUserId(file.getReferenceId(), userId);
    }
}

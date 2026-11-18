package in.healthconnect.storage.service;

import in.healthconnect.storage.entity.StoredFile;
import in.healthconnect.storage.entity.enums.FileCategory;
import org.springframework.stereotype.Component;

// Profile images are not sensitive: any signed-in user may view one (the controller already
// requires authentication before this runs).
@Component
public class ProfileImageGuard implements FileAccessGuard {

    @Override
    public FileCategory category() {
        return FileCategory.PROFILE_IMAGE;
    }

    @Override
    public boolean canAccess(StoredFile file, Integer userId) {
        return true;
    }
}

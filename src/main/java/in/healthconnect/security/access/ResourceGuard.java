package in.healthconnect.security.access;

import in.healthconnect.portal.AccessScope;

// One resource type's ownership rules: may this scope see / change the row with this id?
// A miss throws ResourceNotFoundException (404) so out-of-scope ids cannot be probed.
public interface ResourceGuard {

    ResourceType type();

    void assertVisible(Integer id, AccessScope scope);

    void assertEditable(Integer id, AccessScope scope);
}

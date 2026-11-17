package in.healthconnect.security.dto;

import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.PermissionAction;

import java.util.List;

// One module and the actions a role is allowed on it. Used both ways: the Role screen sends
// these, and we send them back grouped per module (one row per module in the checkbox matrix).
public record PermissionGroup(AppModule module, List<PermissionAction> actions) {
}

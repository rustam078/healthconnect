package in.healthconnect.security.dto;

import in.healthconnect.security.model.AppModule;
import in.healthconnect.security.model.PermissionAction;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// The list of modules + actions the Role screen uses to draw its checkbox matrix.
public record ModuleCatalogResponse(List<ModuleInfo> modules, List<String> actions) {

    public record ModuleInfo(String name, String label) {
    }

    public static ModuleCatalogResponse build() {
        List<ModuleInfo> modules = Arrays.stream(AppModule.values())
                .map(module -> new ModuleInfo(module.name(), label(module.name())))
                .toList();
        List<String> actions = Arrays.stream(PermissionAction.values()).map(Enum::name).toList();
        return new ModuleCatalogResponse(modules, actions);
    }

    // "CUSTOMER_PORTAL" -> "Customer Portal"
    private static String label(String name) {
        return Arrays.stream(name.split("_"))
                .map(word -> word.charAt(0) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}

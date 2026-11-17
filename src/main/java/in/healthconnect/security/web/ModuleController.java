package in.healthconnect.security.web;

import in.healthconnect.security.dto.ModuleCatalogResponse;
import in.healthconnect.wrapper.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Lists all modules + actions so the Role screen can draw its checkbox matrix.
@RestController
@RequestMapping("/api/v1/modules")
public class ModuleController {

    @GetMapping
    public ResponseEntity<ApiResponse<ModuleCatalogResponse>> catalog() {
        return ResponseEntity.ok(ApiResponse.success(ModuleCatalogResponse.build()));
    }
}

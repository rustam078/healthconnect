package in.healthconnect.security.web;

import in.healthconnect.security.dto.RoleRequest;
import in.healthconnect.security.dto.RoleResponse;
import in.healthconnect.security.service.RoleService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Admin management of roles. Guarded centrally by ROLES_* (no annotations here).
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(roleService.list()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getOne(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(roleService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> create(@RequestBody @Valid RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(roleService.create(request), "Role created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> update(@PathVariable Integer id,
                                                            @RequestBody @Valid RoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(roleService.update(id, request), "Role updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Integer id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted"));
    }
}

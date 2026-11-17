package in.healthconnect.security.web;

import in.healthconnect.security.dto.CreateUserRequest;
import in.healthconnect.security.dto.UpdateUserRequest;
import in.healthconnect.security.dto.UserAdminResponse;
import in.healthconnect.security.service.UserService;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Admin management of login users. Guarded centrally by USERS_* (no annotations here).
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserAdminResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(userService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserAdminResponse>> create(@RequestBody @Valid CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.create(request), "User created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserAdminResponse>> update(@PathVariable Integer id,
                                                                 @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.update(id, request), "User updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Integer id) {
        userService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted"));
    }
}

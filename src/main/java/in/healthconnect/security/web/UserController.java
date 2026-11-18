package in.healthconnect.security.web;

import in.healthconnect.portal.CurrentUserProvider;
import in.healthconnect.security.dto.CreateUserRequest;
import in.healthconnect.security.dto.UpdateUserRequest;
import in.healthconnect.security.dto.UserAdminResponse;
import in.healthconnect.security.service.UserService;
import in.healthconnect.storage.dto.StoredFileResponse;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// Admin management of login users. Guarded centrally by USERS_* (no annotations here).
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUser;

    // Any logged-in user sets their own profile image here.
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoredFileResponse>> updateMyAvatar(@RequestParam MultipartFile file) {
        StoredFileResponse response = userService.updateMyAvatar(currentUser.currentUser().getId(), file);
        return ResponseEntity.ok(ApiResponse.success(response, "Profile photo updated"));
    }

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

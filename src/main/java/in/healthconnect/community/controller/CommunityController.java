package in.healthconnect.community.controller;

import in.healthconnect.community.dto.CommunityDto;
import in.healthconnect.community.dto.CreateCommunityRequest;
import in.healthconnect.community.dto.MemberDto;
import in.healthconnect.community.service.CommunityService;
import in.healthconnect.portal.CurrentUserProvider;
import in.healthconnect.wrapper.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Groups and membership: list, discover, create, delete, members, join. The acting user comes
// from the JWT (CurrentUserProvider), never from the URL.
@RestController
@RequestMapping("/api/v1/communities")
public class CommunityController {

    private final CommunityService communityService;
    private final CurrentUserProvider currentUser;

    public CommunityController(CommunityService communityService, CurrentUserProvider currentUser) {
        this.communityService = communityService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommunityDto>>> myCommunities() {
        return ok(communityService.findMyCommunities(currentUser.currentUser().getId()));
    }

    @GetMapping("/discover")
    public ResponseEntity<ApiResponse<List<CommunityDto>>> discover() {
        return ok(communityService.discover(currentUser.currentUser()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityDto>> create(@Valid @RequestBody CreateCommunityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                communityService.create(currentUser.currentUser(), request), "Group created"));
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer communityId) {
        communityService.delete(currentUser.currentUser(), communityId);
        return ResponseEntity.ok(ApiResponse.success(null, "Group deleted"));
    }

    @GetMapping("/{communityId}/members")
    public ResponseEntity<ApiResponse<List<MemberDto>>> members(@PathVariable Integer communityId) {
        return ok(communityService.findMembers(communityId, currentUser.currentUser().getId()));
    }

    @PostMapping("/{communityId}/members")
    public ResponseEntity<ApiResponse<List<MemberDto>>> addMembers(@PathVariable Integer communityId,
                                                                   @RequestBody List<Integer> memberIds) {
        return ok(communityService.addMembers(currentUser.currentUser(), communityId, memberIds));
    }

    @DeleteMapping("/{communityId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable Integer communityId,
                                                          @PathVariable Integer memberId) {
        communityService.removeMember(currentUser.currentUser(), communityId, memberId);
        return ResponseEntity.ok(ApiResponse.success(null, "Member removed"));
    }

    @PostMapping("/{communityId}/join")
    public ResponseEntity<ApiResponse<CommunityDto>> join(@PathVariable Integer communityId) {
        return ResponseEntity.ok(ApiResponse.success(
                communityService.join(currentUser.currentUser(), communityId), "Joined"));
    }

    @PostMapping("/{communityId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Integer communityId,
                                                      @RequestParam Integer messageId) {
        communityService.markRead(communityId, currentUser.currentUser().getId(), messageId);
        return ResponseEntity.ok(ApiResponse.success(null, "Marked read"));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}

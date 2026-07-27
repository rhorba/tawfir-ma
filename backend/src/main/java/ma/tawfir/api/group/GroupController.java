package ma.tawfir.api.group;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ma.tawfir.api.group.dto.CreateGroupRequest;
import ma.tawfir.api.group.dto.GroupDetailResponse;
import ma.tawfir.api.group.dto.GroupSummaryResponse;
import ma.tawfir.api.group.entity.Group;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

	private final GroupService groupService;

	public GroupController(GroupService groupService) {
		this.groupService = groupService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public GroupDetailResponse createGroup(@Valid @RequestBody CreateGroupRequest request, Authentication authentication) {
		UUID organizerId = userId(authentication);
		Group group = groupService.createGroup(organizerId, request);
		return groupService.getGroupDetail(organizerId, group.getId(), false);
	}

	@PostMapping("/{groupId}/finalize")
	public GroupDetailResponse finalizeGroup(@PathVariable UUID groupId, Authentication authentication) {
		UUID userId = userId(authentication);
		groupService.finalizeGroup(userId, groupId);
		return groupService.getGroupDetail(userId, groupId, false);
	}

	@GetMapping
	public List<GroupSummaryResponse> listGroups(Authentication authentication) {
		return groupService.listGroupsForUser(userId(authentication));
	}

	@GetMapping("/{groupId}")
	public GroupDetailResponse getGroup(@PathVariable UUID groupId, Authentication authentication) {
		return groupService.getGroupDetail(userId(authentication), groupId, isAdmin(authentication));
	}

	private UUID userId(Authentication authentication) {
		return UUID.fromString(authentication.getName());
	}

	private boolean isAdmin(Authentication authentication) {
		return authentication.getAuthorities().stream()
			.anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
	}

}

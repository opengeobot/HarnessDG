const fs = require(fs);
const path = require(path);
const NL = \r\n;
const base = d:\\workspace\\opengeobot\\modelscope\\HarnessDG\\modules;
function patch(relPath, replacements) {
  const fullPath = path.join(base, relPath);
  let content = fs.readFileSync(fullPath, utf8);
  for (const [old, nw] of replacements) {
    if (!content.includes(old)) {
      console.error(WARN: pattern not found in  + relPath + :  + old.substring(0, 80));
      continue;
    }
    content = content.replace(old, nw);
  }
  fs.writeFileSync(fullPath, content, utf8);
  console.log(Done:  + relPath);
}
// Wave 2.2: OrganizationMembershipEntity
patch(identity-access\\src\\main\\java\\com\\modelhub\\identity\\domain\\OrganizationMembershipEntity.java, [
  [
     private OffsetDateTime updatedAt = OffsetDateTime.now(); + NL + NL +  public Long getId(),
     private OffsetDateTime updatedAt = OffsetDateTime.now(); + NL + NL +  @Column(nullable = false) + NL +  private long version = 0; + NL + NL +  public Long getId()
  ],
  [
     public boolean isOwner() { return "owner".equals(role); } + NL + },
     public boolean isOwner() { return "owner".equals(role); } + NL +  public long getVersion() { return version; } + NL +  public void setVersion(long version) { this.version = version; } + NL + }
  ]
]);
// Wave 2.2: OrganizationService - MemberView nested user + version
patch(identity-access\\src\\main\\java\\com\\modelhub\\identity\\service\\OrganizationService.java, [
  [
     public record MemberView(String userPublicId, String username, String nickname, String role, + NL +  String status, OffsetDateTime joinedAt) {},
     /** Member view: nested user + version (OpenAPI Member schema). */ + NL +  public record MemberView(UserInfo user, String role, String status, long version, OffsetDateTime joinedAt) { + NL +  public record UserInfo(String id, String username, String nickname) {} + NL +  }
  ],
  [
     private MemberView toMemberView(OrganizationMembershipEntity m) { + NL +  UserEntity u = users.findById(m.getUserId()).orElse(null); + NL +  return new MemberView( + NL +  u == null ? null : u.getPublicId().toString(), + NL +  u == null ? null : u.getUsername(), + NL +  u == null ? null : u.getNickname(), + NL +  m.getRole(), m.getStatus(), m.getCreatedAt()); + NL +  },
     private MemberView toMemberView(OrganizationMembershipEntity m) { + NL +  UserEntity u = users.findById(m.getUserId()).orElse(null); + NL +  MemberView.UserInfo userInfo = u == null ? null : new MemberView.UserInfo( + NL +  u.getPublicId().toString(), u.getUsername(), u.getNickname()); + NL +  return new MemberView(userInfo, m.getRole(), m.getStatus(), m.getVersion(), m.getCreatedAt()); + NL +  }
  ],
  [
    ETags.requireMatch(ifMatch, ETags.ofVersion(ROLE_RANK.get(target.getRole())), "\u6210\u5458\u89d2\u8272");,
    ETags.requireMatch(ifMatch, ETags.ofVersion(target.getVersion()), "\u6210\u5458");
  ],
  [
     target.setRole(newRole); + NL +  target.setUpdatedAt(OffsetDateTime.now()); + NL +  memberships.save(target); + NL +  auditService.appendSimple(actor.username(), "org.member_role_change",,
     target.setRole(newRole); + NL +  target.setVersion(target.getVersion() + 1); + NL +  target.setUpdatedAt(OffsetDateTime.now()); + NL +  memberships.save(target); + NL +  auditService.appendSimple(actor.username(), "org.member_role_change",
  ]
]);
// Wave 2.3: AuthRequests DTO validation
patch(api\\src\\main\\java\\com\\modelhub\\api\\dto\\AuthRequests.java, [
  [@NotBlank @Size(min = 3, max = 31) String username, @NotBlank @Size(min = 3, max = 64) String username],
  [@NotBlank @Size(min = 8, max = 128) String password,, @NotBlank @Size(min = 12, max = 128) String password,],
  [@Size(max = 128) String nickname, @Size(max = 64) String nickname],
  [@NotBlank @Size(min = 8, max = 128) String newPassword, @NotBlank @Size(min = 12, max = 128) String newPassword]
]);

// Wave 2.3: OrgRequests DTO validation
patch(api\\src\\main\\java\\com\\modelhub\\api\\dto\\OrgRequests.java, [
  [@NotBlank @Size(min = 3, max = 63) String slug, @NotBlank @Size(min = 2, max = 64) String slug],
  [
     public record UpdateOrgRequest( + NL +  @Size(max = 128) String name, + NL +  @Size(max = 1024) String description) {},
     public record UpdateOrgRequest( + NL +  @Size(max = 128) String name, + NL +  String status) {}
  ],
  [
    import jakarta.validation.constraints.NotBlank; + NL + import jakarta.validation.constraints.Size;,
    import jakarta.validation.constraints.NotBlank; + NL + import jakarta.validation.constraints.NotNull; + NL + import jakarta.validation.constraints.Size; + NL + import java.util.UUID;
  ],
  [
     public record AddMemberRequest( + NL +  @NotBlank String userId, + NL +  @NotBlank String role) {},
     public record AddMemberRequest( + NL +  @NotNull UUID userId, + NL +  @NotBlank String role) {}
  ]
]);
// Wave 2.4: AdminController unlock 204 + Idempotency-Key
patch(api\\src\\main\\java\\com\\modelhub\\api\\controller\\AdminController.java, [
  [
    import org.springframework.web.bind.annotation.RequestParam;,
    import org.springframework.web.bind.annotation.RequestHeader; + NL + import org.springframework.web.bind.annotation.RequestParam;
  ],
  [
     @PostMapping("/users/{userId}:unlock") + NL +  public ResponseEntity<ApiEnvelope<Map<String, String>>> unlock(@PathVariable("userId") UUID userId, + NL +  HttpServletRequest request) { + NL +  authService.unlockUser(Principals.requireCurrent(request), userId); + NL +  return ResponseEntity.ok(ApiEnvelope.ok(Map.of("userId", userId.toString(), "status", "active"))); + NL +  },
     @PostMapping("/users/{userId}:unlock") + NL +  public ResponseEntity<Void> unlock(@PathVariable("userId") UUID userId, + NL +  @RequestHeader("Idempotency-Key") String idempotencyKey, + NL +  HttpServletRequest request) { + NL +  authService.unlockUser(Principals.requireCurrent(request), userId); + NL +  return ResponseEntity.noContent().header("Idempotency-Key", idempotencyKey).build(); + NL +  }
  ]
]);

// Wave 2.5 + 2.6: OrganizationsController
patch(api\\src\\main\\java\\com\\modelhub\\api\\controller\\OrganizationsController.java, [
  [UUID.fromString(body.userId()), body.role(), body.userId(), body.role()],
  [
     @GetMapping("/{orgId}") + NL +  public ApiEnvelope<OrgView> get(@PathVariable UUID orgId, HttpServletRequest request) { + NL +  return ApiEnvelope.ok(organizationService.get(Principals.requireCurrent(request), orgId)); + NL +  },
     @GetMapping("/{orgId}") + NL +  public ResponseEntity<ApiEnvelope<OrgView>> get(@PathVariable UUID orgId, HttpServletRequest request) { + NL +  OrgView view = organizationService.get(Principals.requireCurrent(request), orgId); + NL +  return ResponseEntity.ok().eTag(view.etag()).body(ApiEnvelope.ok(view)); + NL +  }
  ]
]);

console.log(\nAll Wave 2.2-2.6 patches applied!);

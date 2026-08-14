 = [Environment]::NewLine

# === 2.2: OrganizationMembershipEntity - add version field ===
 = 'd:\workspace\opengeobot\modelscope\HarnessDG\modules\identity-access\src\main\java\com\modelhub\identity\domain\OrganizationMembershipEntity.java'
 = [System.IO.File]::ReadAllText()

 = '    private OffsetDateTime updatedAt = OffsetDateTime.now();' +  +  + '    public Long getId()'
 = '    private OffsetDateTime updatedAt = OffsetDateTime.now();' +  +  + '    @Column(nullable = false)' +  + '    private long version = 0;' +  +  + '    public Long getId()'
 = .Replace(, )

 = '    public boolean isOwner() { return owner.equals(role); }' +  + '}'
 = '    public boolean isOwner() { return owner.equals(role); }' +  + '    public long getVersion() { return version; }' +  + '    public void setVersion(long version) { this.version = version; }' +  + '}'
 = .Replace(, )

[System.IO.File]::WriteAllText(, )
Write-Host Done:MembershipEntity

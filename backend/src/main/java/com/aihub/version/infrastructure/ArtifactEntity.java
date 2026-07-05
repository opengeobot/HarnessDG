package com.aihub.version.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("version_artifact")
public class ArtifactEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("artifact_id")
    private String artifactId;
    @TableField("version_id")
    private String versionId;
    @TableField("path")
    private String path;
    @TableField("dvc_file")
    private String dvcFile;
    @TableField("dvc_hash")
    private String dvcHash;
    @TableField("sha256")
    private String sha256;
    @TableField("size")
    private Long size;
    @TableField("media_type")
    private String mediaType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getDvcFile() { return dvcFile; }
    public void setDvcFile(String dvcFile) { this.dvcFile = dvcFile; }
    public String getDvcHash() { return dvcHash; }
    public void setDvcHash(String dvcHash) { this.dvcHash = dvcHash; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
}

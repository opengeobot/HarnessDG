package com.aihub.version.domain;

import java.util.Objects;

/**
 * 版本工件（文件）。
 *
 * <p>表示版本内的一个文件条目，包含路径、DVC 哈希与 SHA-256 摘要。
 */
public final class Artifact {

    private final String artifactId;
    private final String versionId;
    private final String path;
    private String dvcFile;
    private String dvcHash;
    private final String sha256;
    private final long size;
    private String mediaType;

    public Artifact(String artifactId, String versionId, String path,
                    String dvcFile, String dvcHash, String sha256,
                    long size, String mediaType) {
        this.artifactId = Objects.requireNonNull(artifactId);
        this.versionId = Objects.requireNonNull(versionId);
        this.path = Objects.requireNonNull(path);
        this.dvcFile = dvcFile;
        this.dvcHash = dvcHash;
        this.sha256 = Objects.requireNonNull(sha256);
        if (size < 0) {
            throw new IllegalArgumentException("artifact size must be non-negative");
        }
        this.size = size;
        this.mediaType = mediaType;
    }

    /** 绑定 DVC 哈希与文件路径。 */
    public void bindDvc(String dvcFile, String dvcHash) {
        this.dvcFile = Objects.requireNonNull(dvcFile);
        this.dvcHash = Objects.requireNonNull(dvcHash);
    }

    public String artifactId() { return artifactId; }
    public String versionId() { return versionId; }
    public String path() { return path; }
    public String dvcFile() { return dvcFile; }
    public String dvcHash() { return dvcHash; }
    public String sha256() { return sha256; }
    public long size() { return size; }
    public String mediaType() { return mediaType; }
}

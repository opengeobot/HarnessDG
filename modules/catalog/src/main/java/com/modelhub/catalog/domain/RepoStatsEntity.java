package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 统计投影（03 §6.2）：强事实表可重建；计数刷新幂等且可离线对账。 */
@Entity
@Table(name = "repository_stats")
public class RepoStatsEntity {

    @Id
    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(nullable = false)
    private long likes;

    @Column(nullable = false)
    private long favorites;

    @Column(nullable = false)
    private long downloads;

    @Column(nullable = false)
    private long visits;

    @Column(name = "file_count", nullable = false)
    private long fileCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public long getLikes() { return likes; }
    public void setLikes(long likes) { this.likes = likes; }
    public long getFavorites() { return favorites; }
    public void setFavorites(long favorites) { this.favorites = favorites; }
    public long getDownloads() { return downloads; }
    public void setDownloads(long downloads) { this.downloads = downloads; }
    public long getVisits() { return visits; }
    public void setVisits(long visits) { this.visits = visits; }
    public long getFileCount() { return fileCount; }
    public void setFileCount(long fileCount) { this.fileCount = fileCount; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.aihub.version.application;

import com.aihub.version.domain.Artifact;

/** 工件视图。 */
public record ArtifactView(String artifactId, String versionId, String path,
                           String dvcFile, String dvcHash, String sha256,
                           long size, String mediaType) {

    public static ArtifactView from(Artifact a) {
        return new ArtifactView(a.artifactId(), a.versionId(), a.path(),
                a.dvcFile(), a.dvcHash(), a.sha256(), a.size(), a.mediaType());
    }
}

package com.sequenceiq.cloudbreak.authorization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.sequenceiq.authorization.resource.AuthorizationResource;
import com.sequenceiq.authorization.resource.AuthorizationResourceAction;
import com.sequenceiq.authorization.service.list.AbstractAuthorizationFiltering;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.auth.altus.Crn;
import com.sequenceiq.cloudbreak.domain.ImageCatalog;
import com.sequenceiq.cloudbreak.service.image.ImageCatalogService;

@Component
public class ImageCatalogFiltering extends AbstractAuthorizationFiltering<Set<ImageCatalog>> {

    public static final String WORKSPACE_ID = "WORKSPACE_ID";

    @Inject
    private ImageCatalogService imageCatalogService;

    public Set<ImageCatalog> filterImageCatalogs(AuthorizationResourceAction action, Long workspaceId) {
        Map<String, Object> args = new HashMap<>();
        args.put(WORKSPACE_ID, workspaceId);
        return filterResources(Crn.safeFromString(ThreadBasedUserCrnProvider.getUserCrn()), action, args);
    }

    @Override
    public List<AuthorizationResource> getAllResources(Map<String, Object> args) {
        return imageCatalogService.findAsAuthorizationResorcesInWorkspace(getWorkspaceId(args));
    }

    @Override
    public Set<ImageCatalog> filterByIds(List<Long> authorizedResourceIds, Map<String, Object> args) {
        return imageCatalogService.findAllByIdsWithDefaults(authorizedResourceIds);
    }

    @Override
    public Set<ImageCatalog> getAll(Map<String, Object> args) {
        return imageCatalogService.findAllByWorkspaceId(getWorkspaceId(args));
    }

    private Long getWorkspaceId(Map<String, Object> params) {
        return (Long) params.get(WORKSPACE_ID);
    }
}

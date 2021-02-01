package com.sequenceiq.cloudbreak.authorization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.google.common.collect.Sets;
import com.sequenceiq.authorization.resource.AuthorizationResource;
import com.sequenceiq.authorization.resource.AuthorizationResourceAction;
import com.sequenceiq.authorization.service.list.AbstractAuthorizationFiltering;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.auth.altus.Crn;
import com.sequenceiq.cloudbreak.domain.view.RecipeView;
import com.sequenceiq.cloudbreak.service.recipe.RecipeService;

@Component
public class RecipeFiltering extends AbstractAuthorizationFiltering<Set<RecipeView>> {

    public static final String WORKSPACE_ID = "WORKSPACE_ID";

    @Inject
    private RecipeService recipeService;

    public Set<RecipeView> filterRecipes(AuthorizationResourceAction action, Long workspaceId) {
        Map<String, Object> args = new HashMap<>();
        args.put(WORKSPACE_ID, workspaceId);
        return filterResources(Crn.safeFromString(ThreadBasedUserCrnProvider.getUserCrn()), action, args);
    }

    @Override
    public List<AuthorizationResource> getAllResources(Map<String, Object> args) {
        return recipeService.findAsAuthorizationResourcesInWorkspace(getWorkspaceId(args));
    }

    @Override
    public Set<RecipeView> filterByIds(List<Long> authorizedResourceIds, Map<String, Object> args) {
        return Sets.newLinkedHashSet(recipeService.findAllViewById(authorizedResourceIds));
    }

    @Override
    public Set<RecipeView> getAll(Map<String, Object> args) {
        return recipeService.findAllViewByWorkspaceId(getWorkspaceId(args));
    }

    private Long getWorkspaceId(Map<String, Object> params) {
        return (Long) params.get(WORKSPACE_ID);
    }
}

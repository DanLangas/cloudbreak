package com.sequenceiq.it.cloudbreak.testcase.authorization.listfiltering;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.responses.RecipeViewV4Response;
import com.sequenceiq.it.cloudbreak.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.client.EnvironmentTestClient;
import com.sequenceiq.it.cloudbreak.client.RecipeTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.recipe.RecipeTestDto;
import com.sequenceiq.it.cloudbreak.dto.ums.UmsTestDto;
import com.sequenceiq.it.cloudbreak.testcase.AbstractIntegrationTest;
import com.sequenceiq.it.cloudbreak.testcase.authorization.AuthUserKeys;

public class RecipeFilteringTest extends AbstractIntegrationTest {

    @Inject
    private EnvironmentTestClient environmentTestClient;

    @Inject
    private RecipeTestClient recipeTestClient;

    @Override
    protected void setupTest(TestContext testContext) {
        useRealUmsUser(testContext, AuthUserKeys.ACCOUNT_ADMIN);
        useRealUmsUser(testContext, AuthUserKeys.ENV_CREATOR_A);
        useRealUmsUser(testContext, AuthUserKeys.ENV_CREATOR_B);
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    @Description(
            given = "there is a running env service",
            when = "valid create environment request is sent",
            then = "environment should be created but unauthorized users should not be able to see it in lists")
    public void testImageCatalogListFiltering(TestContext testContext) {
        String recipeA = resourcePropertyProvider().getName();
        String recipeB = resourcePropertyProvider().getName();
        List<String> allRecipes = List.of(recipeA, recipeB);

        createRecipe(testContext, AuthUserKeys.ENV_CREATOR_A, recipeA);
        RecipeTestDto testDto = createRecipe(testContext, AuthUserKeys.ENV_CREATOR_B, recipeB);

        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_A, List.of(recipeA));
        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_B, List.of(recipeB));
        assertUserSeesAll(testDto, AuthUserKeys.ACCOUNT_ADMIN, allRecipes);

        assertUserDoesNotSeeAnyOf(testDto, AuthUserKeys.ENV_CREATOR_A, List.of(recipeB));
        assertUserDoesNotSeeAnyOf(testDto, AuthUserKeys.ENV_CREATOR_B, List.of(recipeA));

        testContext.given(UmsTestDto.class)
                .assignTarget(recipeA)
                .withSharedResourceUser()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_B));

        testContext.given(UmsTestDto.class)
                .assignTarget(recipeB)
                .withSharedResourceUser()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_A));

        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_A, allRecipes);
        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_B, allRecipes);

        useRealUmsUser(testContext, AuthUserKeys.ACCOUNT_ADMIN);
        testDto.validate();
    }

    private RecipeTestDto createRecipe(TestContext testContext, String user, String name) {
        useRealUmsUser(testContext, user);
        return testContext
                .given(name, RecipeTestDto.class)
                .withName(name)
                .when(recipeTestClient.createV4());
    }

    private void assertUserDoesNotSeeAnyOf(RecipeTestDto testDto, String user, List<String> names) {
        useRealUmsUser(testDto.getTestContext(), user);
        Assertions.assertThat(testDto.getAll(testDto.getTestContext().getMicroserviceClient(CloudbreakClient.class))
                .stream()
                .map(RecipeViewV4Response::getName)
                .collect(Collectors.toList()))
                .doesNotContainAnyElementsOf(names);
    }

    private void assertUserSeesAll(RecipeTestDto testDto, String user, List<String> names) {
        useRealUmsUser(testDto.getTestContext(), user);
        List<String> visibleNames = testDto.getAll(testDto.getTestContext().getMicroserviceClient(CloudbreakClient.class))
                .stream()
                .map(RecipeViewV4Response::getName)
                .collect(Collectors.toList());
        Assertions.assertThat(visibleNames).containsAll(names);
    }
}

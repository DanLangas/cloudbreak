package com.sequenceiq.it.cloudbreak.testcase.authorization.listfiltering;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.StackV4Response;
import com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentStatus;
import com.sequenceiq.it.cloudbreak.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.actor.CloudbreakActor;
import com.sequenceiq.it.cloudbreak.client.CredentialTestClient;
import com.sequenceiq.it.cloudbreak.client.DistroXTestClient;
import com.sequenceiq.it.cloudbreak.client.EnvironmentTestClient;
import com.sequenceiq.it.cloudbreak.client.RecipeTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.RunningParameter;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.distrox.DistroXTestDto;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentTestDto;
import com.sequenceiq.it.cloudbreak.dto.imagecatalog.ImageCatalogTestDto;
import com.sequenceiq.it.cloudbreak.dto.ums.UmsTestDto;
import com.sequenceiq.it.cloudbreak.testcase.AbstractIntegrationTest;
import com.sequenceiq.it.cloudbreak.testcase.authorization.AuthUserKeys;

public class DataHubListFilteringTest extends AbstractIntegrationTest {

    @Inject
    private EnvironmentTestClient environmentTestClient;

    @Inject
    private CredentialTestClient credentialTestClient;

    @Inject
    private RecipeTestClient recipeTestClient;

    @Inject
    private DistroXTestClient distroXTestClient;

    @Inject
    private CloudbreakActor actor;

    @Override
    protected void setupTest(TestContext testContext) {
        useRealUmsUser(testContext, AuthUserKeys.ACCOUNT_ADMIN);
        useRealUmsUser(testContext, AuthUserKeys.ENV_CREATOR_A);
        //hacky way to let access to image catalog
        initializeDefaultBlueprints(testContext);
        createDefaultImageCatalog(testContext);
        createDefaultCredential(testContext);
        useRealUmsUser(testContext, AuthUserKeys.ENV_CREATOR_B);
        testContext.given(UmsTestDto.class)
                .assignTarget(ImageCatalogTestDto.class.getSimpleName())
                .withSharedResourceUser()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_B));
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    @Description(
            given = "there is a running env service",
            when = "valid create environment request is sent",
            then = "environment should be created but unauthorized users should not be able to see it in lists")
    public void testDataHubListFiltering(TestContext testContext) {
        EnvironmentTestDto testDto = createEnvironment(testContext, AuthUserKeys.ENV_CREATOR_A);
        createDatalake(testContext);

        testContext.given(UmsTestDto.class)
                .assignTarget(EnvironmentTestDto.class.getSimpleName())
                .withDatahubCreator()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_B))
                .withEnvironmentUser()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_B));

        String dataHubA = resourcePropertyProvider().getName();
        DistroXTestDto distroXTestDtoA = testDto.given(dataHubA, DistroXTestDto.class)
                .withName(dataHubA)
                .when(distroXTestClient.create(), RunningParameter.who(actor.useRealUmsUser(AuthUserKeys.ENV_CREATOR_A)))
                .await(STACK_AVAILABLE, RunningParameter.who(actor.useRealUmsUser(AuthUserKeys.ACCOUNT_ADMIN)));

        assertUserSeesAll(distroXTestDtoA, AuthUserKeys.ENV_CREATOR_A, List.of(dataHubA));
        assertUserSeesAll(distroXTestDtoA, AuthUserKeys.ACCOUNT_ADMIN, List.of(dataHubA));
        assertUserDoesNotSeeAnyOf(distroXTestDtoA, AuthUserKeys.ENV_CREATOR_B, List.of(dataHubA));

        String dataHubB = resourcePropertyProvider().getName();
        DistroXTestDto distroXTestDtoB = testDto.given(dataHubB, DistroXTestDto.class)
                .withName(dataHubB)
                .when(distroXTestClient.create(), RunningParameter.who(actor.useRealUmsUser(AuthUserKeys.ENV_CREATOR_B)))
                .await(STACK_AVAILABLE, RunningParameter.who(actor.useRealUmsUser(AuthUserKeys.ACCOUNT_ADMIN)));

        assertUserSeesAll(distroXTestDtoB, AuthUserKeys.ENV_CREATOR_A, List.of(dataHubA, dataHubB));
        assertUserSeesAll(distroXTestDtoB, AuthUserKeys.ACCOUNT_ADMIN, List.of(dataHubA, dataHubB));
        assertUserSeesAll(distroXTestDtoB, AuthUserKeys.ENV_CREATOR_B, List.of(dataHubB));
        assertUserDoesNotSeeAnyOf(distroXTestDtoA, AuthUserKeys.ENV_CREATOR_B, List.of(dataHubA));

        testContext.given(UmsTestDto.class)
                .assignTarget(dataHubA)
                .withDatahubAdmin()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_B));

        assertUserSeesAll(distroXTestDtoA, AuthUserKeys.ENV_CREATOR_A, List.of(dataHubA, dataHubB));
        assertUserSeesAll(distroXTestDtoA, AuthUserKeys.ACCOUNT_ADMIN, List.of(dataHubA, dataHubB));
        assertUserSeesAll(distroXTestDtoA, AuthUserKeys.ENV_CREATOR_B, List.of(dataHubA, dataHubB));

        useRealUmsUser(testContext, AuthUserKeys.ACCOUNT_ADMIN);
        testDto.validate();
    }

    private EnvironmentTestDto createEnvironment(TestContext testContext, String user) {
        useRealUmsUser(testContext, user);
        EnvironmentTestDto environmentTestDto = testContext
                .given(EnvironmentTestDto.class)
                .withCreateFreeIpa(false)
                .when(environmentTestClient.create())
                .await(EnvironmentStatus.AVAILABLE);
        return environmentTestDto;
    }

    private void assertUserDoesNotSeeAnyOf(DistroXTestDto testDto, String user, List<String> names) {
        useRealUmsUser(testDto.getTestContext(), user);
        Assertions.assertThat(testDto.getAll(testDto.getTestContext().getMicroserviceClient(CloudbreakClient.class))
                .stream()
                .map(StackV4Response::getName)
                .collect(Collectors.toList()))
                .doesNotContainAnyElementsOf(names);
    }

    private void assertUserSeesAll(DistroXTestDto testDto, String user, List<String> names) {
        useRealUmsUser(testDto.getTestContext(), user);
        List<String> visibleNames = testDto.getAll(testDto.getTestContext().getMicroserviceClient(CloudbreakClient.class))
                .stream()
                .map(StackV4Response::getName)
                .collect(Collectors.toList());
        Assertions.assertThat(visibleNames).containsAll(names);
    }
}

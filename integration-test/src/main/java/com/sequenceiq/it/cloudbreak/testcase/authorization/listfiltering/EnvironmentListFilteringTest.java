package com.sequenceiq.it.cloudbreak.testcase.authorization.listfiltering;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentStatus;
import com.sequenceiq.environment.api.v1.environment.model.response.SimpleEnvironmentResponse;
import com.sequenceiq.it.cloudbreak.actor.CloudbreakActor;
import com.sequenceiq.it.cloudbreak.client.CredentialTestClient;
import com.sequenceiq.it.cloudbreak.client.EnvironmentTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.RunningParameter;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.credential.CredentialTestDto;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentTestDto;
import com.sequenceiq.it.cloudbreak.dto.ums.UmsTestDto;
import com.sequenceiq.it.cloudbreak.testcase.AbstractIntegrationTest;
import com.sequenceiq.it.cloudbreak.testcase.authorization.AuthUserKeys;

public class EnvironmentListFilteringTest extends AbstractIntegrationTest {

    @Inject
    private EnvironmentTestClient environmentTestClient;

    @Inject
    private CredentialTestClient credentialTestClient;

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
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    @Description(
            given = "there is a running env service",
            when = "valid create environment request is sent",
            then = "environment should be created but unauthorized users should not be able to see it in lists")
    public void testEnvironmentListFiltering(TestContext testContext) {
        String environmentA = resourcePropertyProvider().getEnvironmentName();
        String environmentB = resourcePropertyProvider().getEnvironmentName();
        List<String> allEnvironments = List.of(environmentA, environmentB);

        createEnvironment(testContext, AuthUserKeys.ENV_CREATOR_A, environmentA);
        EnvironmentTestDto testDto = createEnvironment(testContext, AuthUserKeys.ENV_CREATOR_B, environmentB);

        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_A, List.of(environmentA));
        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_B, List.of(environmentB));
        assertUserSeesAll(testDto, AuthUserKeys.ACCOUNT_ADMIN, allEnvironments);

        assertUserDoesNotSeeAnyOf(testDto, AuthUserKeys.ENV_CREATOR_A, List.of(environmentB));
        assertUserDoesNotSeeAnyOf(testDto, AuthUserKeys.ENV_CREATOR_B, List.of(environmentA));

        testContext.given(UmsTestDto.class)
                .assignTarget(environmentA)
                .withEnvironmentAdmin()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_B));

        testContext.given(UmsTestDto.class)
                .assignTarget(environmentB)
                .withEnvironmentUser()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_A));

        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_A, allEnvironments);
        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_B, allEnvironments);

        useRealUmsUser(testContext, AuthUserKeys.ACCOUNT_ADMIN);
        testDto.validate();
    }

    private EnvironmentTestDto createEnvironment(TestContext testContext, String user, String name) {
        useRealUmsUser(testContext, user);
        CredentialTestDto credentialTestDto = testContext
                .given(resourcePropertyProvider().getName(), CredentialTestDto.class)
                .when(credentialTestClient.create());
        EnvironmentTestDto environmentTestDto = credentialTestDto
                .given(name, EnvironmentTestDto.class)
                .withName(name)
                .withCredentialName(credentialTestDto.getName())
                .withCreateFreeIpa(false)
                .when(environmentTestClient.create())
                .await(EnvironmentStatus.AVAILABLE);
        return environmentTestDto;
    }

    private void assertUserDoesNotSeeAnyOf(EnvironmentTestDto testDto, String user, List<String> names) {
        testDto.when(environmentTestClient.list(), RunningParameter.who(actor.useRealUmsUser(user)))
                .then((tc, dto, client) -> {
                    Assertions.assertThat(dto.getResponseSimpleEnvSet()
                            .stream()
                            .map(SimpleEnvironmentResponse::getName)
                            .collect(Collectors.toList()))
                            .doesNotContainAnyElementsOf(names);
                    return dto;
                });
    }

    private void assertUserSeesAll(EnvironmentTestDto testDto, String user, List<String> names) {
        testDto.when(environmentTestClient.list(), RunningParameter.who(actor.useRealUmsUser(user)))
                .then((tc, dto, client) -> {
                    Assertions.assertThat(dto.getResponseSimpleEnvSet()
                            .stream()
                            .map(SimpleEnvironmentResponse::getName)
                            .collect(Collectors.toList()))
                            .containsAll(names);
                    return dto;
                });
    }
}

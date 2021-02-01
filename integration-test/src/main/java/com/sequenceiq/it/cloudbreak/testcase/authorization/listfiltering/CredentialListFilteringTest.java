package com.sequenceiq.it.cloudbreak.testcase.authorization.listfiltering;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import com.sequenceiq.environment.api.v1.credential.model.response.CredentialResponse;
import com.sequenceiq.it.cloudbreak.actor.CloudbreakActor;
import com.sequenceiq.it.cloudbreak.client.CredentialTestClient;
import com.sequenceiq.it.cloudbreak.client.EnvironmentTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.RunningParameter;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.credential.CredentialTestDto;
import com.sequenceiq.it.cloudbreak.dto.ums.UmsTestDto;
import com.sequenceiq.it.cloudbreak.testcase.AbstractIntegrationTest;
import com.sequenceiq.it.cloudbreak.testcase.authorization.AuthUserKeys;

public class CredentialListFilteringTest extends AbstractIntegrationTest {

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
        useRealUmsUser(testContext, AuthUserKeys.ENV_CREATOR_B);
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    @Description(
            given = "there is a running env service",
            when = "valid create environment request is sent",
            then = "environment should be created but unauthorized users should not be able to see it in lists")
    public void testCredentialListFiltering(TestContext testContext) {
        String credentialA = resourcePropertyProvider().getName();
        String credentialB = resourcePropertyProvider().getName();
        List<String> allCredentials = List.of(credentialA, credentialB);

        createCredential(testContext, AuthUserKeys.ENV_CREATOR_A, credentialA);
        CredentialTestDto testDto = createCredential(testContext, AuthUserKeys.ENV_CREATOR_B, credentialB);

        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_A, List.of(credentialA));
        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_B, List.of(credentialB));
        assertUserSeesAll(testDto, AuthUserKeys.ACCOUNT_ADMIN, allCredentials);

        assertUserDoesNotSeeAnyOf(testDto, AuthUserKeys.ENV_CREATOR_A, List.of(credentialB));
        assertUserDoesNotSeeAnyOf(testDto, AuthUserKeys.ENV_CREATOR_B, List.of(credentialA));

        testContext.given(UmsTestDto.class)
                .assignTarget(credentialA)
                .withSharedResourceUser()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_B));

        testContext.given(UmsTestDto.class)
                .assignTarget(credentialB)
                .withSharedResourceUser()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_A));

        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_A, allCredentials);
        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_B, allCredentials);

        useRealUmsUser(testContext, AuthUserKeys.ACCOUNT_ADMIN);
        testDto.validate();
    }

    private CredentialTestDto createCredential(TestContext testContext, String user, String name) {
        useRealUmsUser(testContext, user);
        return testContext
                .given(name, CredentialTestDto.class)
                .withName(name)
                .when(credentialTestClient.create());
    }

    private void assertUserDoesNotSeeAnyOf(CredentialTestDto testDto, String user, List<String> names) {
        testDto.when(credentialTestClient.list(), RunningParameter.who(actor.useRealUmsUser(user)))
                .then((tc, dto, client) -> {
                    Assertions.assertThat(dto.getResponses()
                            .stream()
                            .map(CredentialResponse::getName)
                            .collect(Collectors.toList()))
                            .doesNotContainAnyElementsOf(names);
                    return dto;
                });
    }

    private void assertUserSeesAll(CredentialTestDto testDto, String user, List<String> names) {
        testDto.when(credentialTestClient.list(), RunningParameter.who(actor.useRealUmsUser(user)))
                .then((tc, dto, client) -> {
                    Assertions.assertThat(dto.getResponses()
                            .stream()
                            .map(CredentialResponse::getName)
                            .collect(Collectors.toList()))
                            .containsAll(names);
                    return dto;
                });
    }
}

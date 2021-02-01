package com.sequenceiq.it.cloudbreak.testcase.authorization.listfiltering;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import com.sequenceiq.cloudbreak.api.endpoint.v4.imagecatalog.responses.ImageCatalogV4Response;
import com.sequenceiq.it.cloudbreak.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.client.EnvironmentTestClient;
import com.sequenceiq.it.cloudbreak.client.ImageCatalogTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.imagecatalog.ImageCatalogTestDto;
import com.sequenceiq.it.cloudbreak.dto.ums.UmsTestDto;
import com.sequenceiq.it.cloudbreak.testcase.AbstractIntegrationTest;
import com.sequenceiq.it.cloudbreak.testcase.authorization.AuthUserKeys;

public class ImageCatalogListFiltering extends AbstractIntegrationTest {

    @Inject
    private EnvironmentTestClient environmentTestClient;

    @Inject
    private ImageCatalogTestClient imageCatalogTestClient;

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
        String imageCatalogA = resourcePropertyProvider().getName();
        String imageCatalogB = resourcePropertyProvider().getName();
        List<String> allImageCatalogs = List.of(imageCatalogA, imageCatalogB);

        createImageCatalog(testContext, AuthUserKeys.ENV_CREATOR_A, imageCatalogA);
        ImageCatalogTestDto testDto = createImageCatalog(testContext, AuthUserKeys.ENV_CREATOR_B, imageCatalogB);

        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_A, List.of(imageCatalogA));
        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_B, List.of(imageCatalogB));
        assertUserSeesAll(testDto, AuthUserKeys.ACCOUNT_ADMIN, allImageCatalogs);

        assertUserDoesNotSeeAnyOf(testDto, AuthUserKeys.ENV_CREATOR_A, List.of(imageCatalogB));
        assertUserDoesNotSeeAnyOf(testDto, AuthUserKeys.ENV_CREATOR_B, List.of(imageCatalogA));

        testContext.given(UmsTestDto.class)
                .assignTarget(imageCatalogA)
                .withSharedResourceUser()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_B));

        testContext.given(UmsTestDto.class)
                .assignTarget(imageCatalogB)
                .withSharedResourceUser()
                .when(environmentTestClient.assignResourceRole(AuthUserKeys.ENV_CREATOR_A));

        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_A, allImageCatalogs);
        assertUserSeesAll(testDto, AuthUserKeys.ENV_CREATOR_B, allImageCatalogs);

        useRealUmsUser(testContext, AuthUserKeys.ACCOUNT_ADMIN);
        testDto.validate();
    }

    private ImageCatalogTestDto createImageCatalog(TestContext testContext, String user, String name) {
        useRealUmsUser(testContext, user);
        return testContext
                .given(name, ImageCatalogTestDto.class)
                .withName(name)
                .when(imageCatalogTestClient.createV4());
    }

    private void assertUserDoesNotSeeAnyOf(ImageCatalogTestDto testDto, String user, List<String> names) {
        useRealUmsUser(testDto.getTestContext(), user);
        Assertions.assertThat(testDto.getAll(testDto.getTestContext().getMicroserviceClient(CloudbreakClient.class))
                .stream()
                .map(ImageCatalogV4Response::getName)
                .collect(Collectors.toList()))
                .doesNotContainAnyElementsOf(names);
    }

    private void assertUserSeesAll(ImageCatalogTestDto testDto, String user, List<String> names) {
        useRealUmsUser(testDto.getTestContext(), user);
        List<String> visibleNames = testDto.getAll(testDto.getTestContext().getMicroserviceClient(CloudbreakClient.class))
                .stream()
                .map(ImageCatalogV4Response::getName)
                .collect(Collectors.toList());
        Assertions.assertThat(visibleNames).containsAll(names);
    }
}

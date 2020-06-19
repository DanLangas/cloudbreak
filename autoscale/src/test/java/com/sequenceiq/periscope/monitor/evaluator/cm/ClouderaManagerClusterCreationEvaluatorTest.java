package com.sequenceiq.periscope.monitor.evaluator.cm;

import static com.sequenceiq.periscope.api.model.ClusterState.PENDING;
import static com.sequenceiq.periscope.api.model.ClusterState.RUNNING;
import static com.sequenceiq.periscope.api.model.ClusterState.SUSPENDED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloudera.api.swagger.ClouderaManagerResourceApi;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.AutoscaleStackV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.StackV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.response.instancegroup.instancemetadata.InstanceMetaDataV4Response;
import com.sequenceiq.cloudbreak.cm.client.ClouderaManagerApiClientProvider;
import com.sequenceiq.cloudbreak.cm.client.retry.ClouderaManagerApiFactory;
import com.sequenceiq.cloudbreak.service.secret.service.SecretService;
import com.sequenceiq.periscope.api.model.ClusterState;
import com.sequenceiq.periscope.aspects.RequestLogging;
import com.sequenceiq.periscope.domain.Cluster;
import com.sequenceiq.periscope.domain.ClusterPertain;
import com.sequenceiq.periscope.domain.SecurityConfig;
import com.sequenceiq.periscope.monitor.context.EvaluatorContext;
import com.sequenceiq.periscope.service.ClusterService;
import com.sequenceiq.periscope.service.security.SecurityConfigService;
import com.sequenceiq.periscope.service.security.TlsHttpClientConfigurationService;
import com.sequenceiq.periscope.service.security.TlsSecurityService;

@RunWith(MockitoJUnitRunner.class)
public class ClouderaManagerClusterCreationEvaluatorTest {

    private static final long STACK_ID = 1;

    private static final String STACK_CRN = "STACK_CRN";

    @Captor
    public ArgumentCaptor<ClusterPertain> captor;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ClouderaManagerApiClientProvider clouderaManagerApiClientProvider;

    @Mock
    private ClouderaManagerApiFactory clouderaManagerApiFactory;

    @Mock
    private TlsSecurityService tlsSecurityService;

    @Mock
    private TlsHttpClientConfigurationService tlsHttpClientConfigurationService;

    @Mock
    private SecurityConfigService securityConfigService;

    @Mock
    private RequestLogging requestLogging;

    @Mock
    private SecretService secretService;

    @InjectMocks
    private ClouderaManagerClusterCreationEvaluator underTest;

    @Mock
    private EvaluatorContext evaluatorContext;

    @Before
    public void setUp() {
        underTest.setContext(evaluatorContext);
    }

    @Test
    public void shouldUpdateSuspendedHelthyCluster() {
        Cluster cluster = getCluster(SUSPENDED);
        StackV4Response stack = new StackV4Response();
        setUpMocks(cluster, true, stack);

        underTest.execute();

        verify(clusterService).findOneByStackId(STACK_ID);
        verify(clusterService).update(eq(cluster.getId()), any(), eq(RUNNING), eq(true));
    }

    @Test
    public void shouldUpdatePendingHelthyCluster() {
        Cluster cluster = getCluster(PENDING);
        StackV4Response stack = new StackV4Response();

        setUpMocks(cluster, true, stack);

        underTest.execute();

        verify(clusterService).findOneByStackId(STACK_ID);
        verify(clusterService).update(eq(cluster.getId()), any(), eq(RUNNING), eq(true));
    }

    @Test
    public void shouldNotUpdateRunningHelthyCluster() {
        Cluster cluster = getCluster(RUNNING);
        StackV4Response stack = new StackV4Response();

        setUpMocks(cluster, true, stack);

        underTest.execute();

        verify(clusterService).findOneByStackId(STACK_ID);
        verifyNoMoreInteractions(clusterService);
    }

    @Test
    public void shouldNotUpdateSuspendedUnHelthyCluster() {
        Cluster cluster = getCluster(SUSPENDED);
        StackV4Response stack = new StackV4Response();

        setUpMocks(cluster, false, stack);

        underTest.execute();

        verify(clusterService).findOneByStackId(STACK_ID);
        verifyNoMoreInteractions(clusterService);
    }

    @Test
    public void shouldValidateAndCreateNewCluster() {
        StackV4Response stack = new StackV4Response();

        setUpMocks(null, false, stack);

        Cluster cluster = getCluster(null);
        when(clusterService.create(any())).thenReturn(cluster);

        underTest.execute();

        verify(clusterService).findOneByStackId(STACK_ID);
        verify(clusterService).validateClusterUniqueness(any());
        verify(clusterService).create(any(AutoscaleStackV4Response.class));
    }

    private void setUpMocks(Cluster cluster, boolean healthy, StackV4Response stackV4Response) {
        InstanceMetaDataV4Response instanceMetaData = new InstanceMetaDataV4Response();
        instanceMetaData.setDiscoveryFQDN("master");
        setUpMocks(cluster, healthy, stackV4Response, Optional.of(instanceMetaData));
    }

    private void setUpMocks(Cluster cluster, boolean healthy, StackV4Response stackV4Response, Optional<InstanceMetaDataV4Response> primaryGateways) {
        AutoscaleStackV4Response stack = getStackResponse();
        when(evaluatorContext.getData()).thenReturn(stack);
        when(securityConfigService.getSecurityConfig(anyLong())).thenReturn(new SecurityConfig());
        when(clusterService.findOneByStackId(anyLong())).thenReturn(cluster);
        when(requestLogging.logResponseTime(any(), any())).thenReturn(healthy);

        when(clusterService.update(anyLong(), any(), any(), anyBoolean())).thenReturn(cluster);
        // CHECKSTYLE:OFF
        when(clouderaManagerApiFactory.getClouderaManagerResourceApi(any())).thenReturn(new ClouderaManagerResourceApi());
        // CHECKSTYLE:ON
    }

    private AutoscaleStackV4Response getStackResponse() {
        AutoscaleStackV4Response stack = new AutoscaleStackV4Response();
        stack.setStackId(STACK_ID);
        stack.setStackCrn(STACK_CRN);
        stack.setClusterManagerIp("0.0.0.0");
        stack.setGatewayPort(8080);
        stack.setTenant("TENANT");
        stack.setWorkspaceId(10L);
        stack.setUserId("USER_ID");
        stack.setClusterStatus(Status.AVAILABLE);
        return stack;
    }

    private Cluster getCluster(ClusterState clusterState) {
        Cluster cluster = new Cluster();
        cluster.setId(10);
        cluster.setStackCrn(STACK_CRN);
        cluster.setAutoscalingEnabled(true);
        cluster.setState(clusterState);
        return cluster;
    }

}
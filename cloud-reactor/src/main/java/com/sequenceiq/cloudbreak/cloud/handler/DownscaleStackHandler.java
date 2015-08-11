package com.sequenceiq.cloudbreak.cloud.handler;

import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cloud.CloudConnector;
import com.sequenceiq.cloudbreak.cloud.event.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.event.resource.DownscaleStackRequest;
import com.sequenceiq.cloudbreak.cloud.event.resource.LaunchStackResult;
import com.sequenceiq.cloudbreak.cloud.init.CloudPlatformConnectors;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.CloudResourceStatus;
import com.sequenceiq.cloudbreak.cloud.model.ResourceStatus;
import com.sequenceiq.cloudbreak.cloud.scheduler.SyncPollingScheduler;
import com.sequenceiq.cloudbreak.cloud.task.PollTask;
import com.sequenceiq.cloudbreak.cloud.task.PollTaskFactory;
import com.sequenceiq.cloudbreak.cloud.task.ResourcesStatePollerResult;
import com.sequenceiq.cloudbreak.cloud.transform.ResourceLists;
import com.sequenceiq.cloudbreak.cloud.transform.ResourcesStatePollerResults;

import reactor.bus.Event;

@Component
public class DownscaleStackHandler implements CloudPlatformEventHandler<DownscaleStackRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownscaleStackHandler.class);
    private static final int INTERVAL = 5;
    private static final int MAX_ATTEMPT = 100;

    @Inject
    private SyncPollingScheduler<ResourcesStatePollerResult> syncPollingScheduler;

    @Inject
    private PollTaskFactory statusCheckFactory;

    @Inject
    private CloudPlatformConnectors cloudPlatformConnectors;

    @Override
    public Class<DownscaleStackRequest> type() {
        return DownscaleStackRequest.class;
    }

    @Override
    public void accept(Event<DownscaleStackRequest> downscaleStackRequestEvent) {
        LOGGER.info("Received event: {}", downscaleStackRequestEvent);
        DownscaleStackRequest request = downscaleStackRequestEvent.getData();
        try {
            String platform = request.getCloudContext().getPlatform();
            CloudConnector connector = cloudPlatformConnectors.get(platform);
            AuthenticatedContext ac = connector.authenticate(request.getCloudContext(), request.getCloudCredential());

            List<CloudResourceStatus> resourceStatus = connector.resources()
                    .downscale(ac, request.getCloudStack(), request.getCloudResources(), request.getInstanceTemplates());

            List<CloudResource> resources = ResourceLists.transform(resourceStatus);

            PollTask<ResourcesStatePollerResult> task = statusCheckFactory.newPollResourcesStateTask(ac, resources);
            ResourcesStatePollerResult statePollerResult = ResourcesStatePollerResults.build(request.getCloudContext(), resourceStatus);
            if (!task.completed(statePollerResult)) {
                statePollerResult = syncPollingScheduler.schedule(task, INTERVAL, MAX_ATTEMPT);
            }

            request.getResult().onNext(ResourcesStatePollerResults.transformToUpscaleStackResult(statePollerResult));

        } catch (Exception e) {
            LOGGER.error("Failed to handle UpscaleStackRequest.", e);
            request.getResult().onNext(new LaunchStackResult(request.getCloudContext(), ResourceStatus.FAILED, e.getMessage(), null));
        }
        LOGGER.info("UpscaleStackHandler finished");
    }
}

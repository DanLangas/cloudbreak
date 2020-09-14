package com.sequenceiq.cloudbreak.core.flow2.cluster.certrotate;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.api.endpoint.v4.common.DetailedStackStatus;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status;
import com.sequenceiq.cloudbreak.converter.util.ExceptionMessageFormatterUtil;
import com.sequenceiq.cloudbreak.core.flow2.stack.CloudbreakFlowMessageService;
import com.sequenceiq.cloudbreak.domain.view.StackView;
import com.sequenceiq.cloudbreak.event.ResourceEvent;
import com.sequenceiq.cloudbreak.service.StackUpdater;
import com.sequenceiq.cloudbreak.service.cluster.ClusterService;

@Service
public class ClusterCertificatesRotationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterCertificatesRotationService.class);

    @Inject
    private StackUpdater stackUpdater;

    @Inject
    private CloudbreakFlowMessageService flowMessageService;

    @Inject
    private ClusterService clusterService;

    void initClusterCertificatesRotation(long stackId) {
        String statusReason = "Rotating the certificates of the cluster.";
        LOGGER.debug(statusReason);
        stackUpdater.updateStackStatus(stackId, DetailedStackStatus.CLUSTER_OPERATION, statusReason);
        clusterService.updateClusterStatusByStackId(stackId, Status.UPDATE_IN_PROGRESS);
        flowMessageService.fireEventAndLog(stackId, Status.UPDATE_IN_PROGRESS.name(), ResourceEvent.CLUSTER_CERTIFICATES_ROTATION_STARTED);
    }

    void hostCertificatesRotationStarted(long stackId) {
        String statusReason = "The rotation of the host certificates of the cluster has been started.";
        LOGGER.debug(statusReason);
        stackUpdater.updateStackStatus(stackId, DetailedStackStatus.CLUSTER_OPERATION, statusReason);
        clusterService.updateClusterStatusByStackId(stackId, Status.UPDATE_IN_PROGRESS);
        flowMessageService.fireEventAndLog(stackId, Status.UPDATE_IN_PROGRESS.name(), ResourceEvent.CLUSTER_HOST_CERTIFICATES_ROTATION);
    }

    void certificatesRotationFinished(long stackId) {
        clusterService.updateClusterStatusByStackId(stackId, Status.AVAILABLE);
        stackUpdater.updateStackStatus(stackId, DetailedStackStatus.AVAILABLE, "Rotation of the cluster's certificates finished.");
        flowMessageService.fireEventAndLog(stackId, Status.AVAILABLE.name(), ResourceEvent.CLUSTER_CERTIFICATES_ROTATION_FINISHED);
    }

    void certificatesRotationFailed(StackView stackView, Exception exception) {
        if (stackView.getClusterView() != null) {
            Long stackId = stackView.getId();
            String errorMessage = ExceptionMessageFormatterUtil.getErrorMessageFromException(exception);
            clusterService.updateClusterStatusByStackId(stackId, Status.UPDATE_FAILED, errorMessage);
            stackUpdater.updateStackStatus(stackId, DetailedStackStatus.AVAILABLE);
            flowMessageService.fireEventAndLog(stackId, Status.UPDATE_FAILED.name(), ResourceEvent.CLUSTER_CERTIFICATES_ROTATION_FAILED, errorMessage);
        } else {
            LOGGER.info("Cluster was null. Flow action was not required.");
        }
    }
}

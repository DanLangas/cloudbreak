package com.sequenceiq.cloudbreak.structuredevent.service.telemetry.converter;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import javax.inject.Inject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.cloudera.thunderhead.service.common.usage.UsageProto;
import com.sequenceiq.cloudbreak.structuredevent.event.FlowDetails;
import com.sequenceiq.cloudbreak.structuredevent.event.cdp.CDPOperationDetails;
import com.sequenceiq.cloudbreak.structuredevent.event.cdp.environment.CDPEnvironmentStructuredFlowEvent;
import com.sequenceiq.cloudbreak.structuredevent.service.telemetry.mapper.EnvironmentRequestProcessingStepMapper;

@Component
public class CDPStructuredFlowEventToCDPOperationDetailsConverter {

    @Value("${info.app.version:}")
    private String appVersion;

    @Inject
    private EnvironmentRequestProcessingStepMapper environmentRequestProcessingStepMapper;

    public UsageProto.CDPOperationDetails convert(CDPEnvironmentStructuredFlowEvent cdpStructuredFlowEvent) {
        if (cdpStructuredFlowEvent == null) {
            return null;
        }
        UsageProto.CDPOperationDetails.Builder cdpOperationDetails = UsageProto.CDPOperationDetails.newBuilder();
        CDPOperationDetails structuredOperationDetails = cdpStructuredFlowEvent.getOperation();
        if (structuredOperationDetails != null) {
            cdpOperationDetails.setAccountId(defaultIfEmpty(structuredOperationDetails.getAccountId(), ""));
            cdpOperationDetails.setResourceCrn(defaultIfEmpty(structuredOperationDetails.getResourceCrn(), ""));
            cdpOperationDetails.setResourceName(defaultIfEmpty(structuredOperationDetails.getResourceName(), ""));
            cdpOperationDetails.setInitiatorCrn(defaultIfEmpty(structuredOperationDetails.getUserCrn(), ""));
            cdpOperationDetails.setCorrelationId(defaultIfEmpty(structuredOperationDetails.getUuid(), ""));
        }

        FlowDetails flowDetails = cdpStructuredFlowEvent.getFlow();
        if (flowDetails != null) {
            String flowId = defaultIfEmpty(flowDetails.getFlowId(), "");
            cdpOperationDetails.setFlowId(flowId);
            // We will use flow id if there is no flowchain id, this helps to correlate requests
            cdpOperationDetails.setFlowChainId(defaultIfEmpty(flowDetails.getFlowChainId(), flowId));
            cdpOperationDetails.setFlowState(flowDetails.getFlowState() != null &&
                    !"unknown".equals(flowDetails.getFlowState()) ? flowDetails.getFlowState() : "");
        }

        cdpOperationDetails.setCdpRequestProcessingStep(environmentRequestProcessingStepMapper.mapIt(cdpStructuredFlowEvent.getFlow()));
        cdpOperationDetails.setApplicationVersion(appVersion);

        return cdpOperationDetails.build();
    }
}

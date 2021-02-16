package com.sequenceiq.cloudbreak.structuredevent.service.telemetry.converter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.cloudera.thunderhead.service.common.usage.UsageProto;
import com.sequenceiq.cloudbreak.structuredevent.event.StructuredSyncEvent;
import com.sequenceiq.cloudbreak.structuredevent.event.legacy.OperationDetails;

@Component
public class StructuredSyncEventToCDPOperationDetailsConverter {

    @Value("${info.app.version:}")
    private String appVersion;

    public UsageProto.CDPOperationDetails convert(StructuredSyncEvent structuredSyncEvent) {
        if (structuredSyncEvent == null) {
            return null;
        }
        UsageProto.CDPOperationDetails.Builder cdpOperationDetails = UsageProto.CDPOperationDetails.newBuilder();
        OperationDetails structuredOperationDetails = structuredSyncEvent.getOperation();
        if (structuredOperationDetails != null) {
            cdpOperationDetails.setAccountId(structuredOperationDetails.getTenant());
            cdpOperationDetails.setResourceCrn(structuredOperationDetails.getResourceCrn());
            cdpOperationDetails.setResourceName(structuredOperationDetails.getResourceName());
            cdpOperationDetails.setInitiatorCrn(structuredOperationDetails.getUserCrn());
        }

        cdpOperationDetails.setCdpRequestProcessingStep(UsageProto.CDPRequestProcessingStep.Value.UNSET);
        cdpOperationDetails.setApplicationVersion(appVersion);

        return cdpOperationDetails.build();
    }
}

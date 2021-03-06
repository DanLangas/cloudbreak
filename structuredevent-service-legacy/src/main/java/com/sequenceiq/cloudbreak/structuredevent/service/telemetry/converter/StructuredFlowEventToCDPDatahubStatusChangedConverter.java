package com.sequenceiq.cloudbreak.structuredevent.service.telemetry.converter;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cloudera.thunderhead.service.common.usage.UsageProto;
import com.sequenceiq.cloudbreak.common.json.JsonUtil;
import com.sequenceiq.cloudbreak.structuredevent.event.StructuredFlowEvent;
import com.sequenceiq.cloudbreak.structuredevent.service.telemetry.model.CombinedStatus;

@Component
public class StructuredFlowEventToCDPDatahubStatusChangedConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredFlowEventToCDPDatahubStatusChangedConverter.class);

    @Inject
    private StructuredFlowEventToCDPOperationDetailsConverter operationDetailsConverter;

    @Inject
    private StructuredFlowEventToCombinedStatusConverter combinedStatusConverter;

    public UsageProto.CDPDatahubStatusChanged convert(StructuredFlowEvent structuredFlowEvent, UsageProto.CDPClusterStatus.Value status) {
        if (structuredFlowEvent == null) {
            return null;
        }
        UsageProto.CDPDatahubStatusChanged.Builder cdpDatahubStatusChanged = UsageProto.CDPDatahubStatusChanged.newBuilder();
        cdpDatahubStatusChanged.setOperationDetails(operationDetailsConverter.convert(structuredFlowEvent));

        cdpDatahubStatusChanged.setNewStatus(status);

        CombinedStatus combinedStatus = combinedStatusConverter.convert(structuredFlowEvent);
        cdpDatahubStatusChanged.setFailureReason(JsonUtil.writeValueAsStringSilentSafe(combinedStatus));

        UsageProto.CDPDatahubStatusChanged ret = cdpDatahubStatusChanged.build();
        LOGGER.debug("Converted CDPDatahubStatusChanged telemetry event: {}", ret);
        return ret;
    }
}

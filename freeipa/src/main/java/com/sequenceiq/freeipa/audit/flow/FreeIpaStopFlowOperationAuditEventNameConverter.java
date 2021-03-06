package com.sequenceiq.freeipa.audit.flow;

import static com.sequenceiq.freeipa.flow.stack.stop.StackStopEvent.STACK_STOP_EVENT;
import static com.sequenceiq.freeipa.flow.stack.stop.StackStopEvent.STOP_FAIL_HANDLED_EVENT;
import static com.sequenceiq.freeipa.flow.stack.stop.StackStopEvent.STOP_FINALIZED_EVENT;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.audit.model.AuditEventName;
import com.sequenceiq.cloudbreak.structuredevent.event.FlowDetails;
import com.sequenceiq.cloudbreak.structuredevent.event.cdp.CDPStructuredFlowEvent;

@Component
public class FreeIpaStopFlowOperationAuditEventNameConverter implements FreeIpaFlowOperationAuditEventNameConverter {

    @Override
    public boolean isInit(CDPStructuredFlowEvent structuredEvent) {
        FlowDetails flow = structuredEvent.getFlow();
        return STACK_STOP_EVENT.name().equals(flow.getFlowEvent());
    }

    @Override
    public boolean isFinal(CDPStructuredFlowEvent structuredEvent) {
        FlowDetails flow = structuredEvent.getFlow();
        return STOP_FINALIZED_EVENT.name().equals(flow.getFlowEvent());
    }

    @Override
    public boolean isFailed(CDPStructuredFlowEvent structuredEvent) {
        FlowDetails flow = structuredEvent.getFlow();
        return STOP_FAIL_HANDLED_EVENT.name().equals(flow.getFlowEvent());
    }

    @Override
    public AuditEventName eventName() {
        return AuditEventName.STOP_FREEIPA;
    }
}

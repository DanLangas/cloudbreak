package com.sequenceiq.freeipa.util;

import com.sequenceiq.cloudbreak.cloud.VersionComparator;
import com.sequenceiq.cloudbreak.common.type.Versioned;
import com.sequenceiq.freeipa.entity.Stack;

public class ClusterProxyServiceAvailabilityChecker {

    // feature supported from 2.21
    private static final Versioned DNS_BASED_SERVICE_NAME_AFTER_VERSION = () -> "2.20.0";

    private ClusterProxyServiceAvailabilityChecker() {
    }

    public static boolean isDnsBasedServiceNameAvailable(Stack stack) {
        Versioned currentVersion = () -> stack.getAppVersion();
        return new VersionComparator().compare(currentVersion, DNS_BASED_SERVICE_NAME_AFTER_VERSION) > 0;
    }

}

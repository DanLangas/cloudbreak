package com.sequenceiq.authorization.resource;

import java.util.List;
import java.util.Map;

public interface AuthorizationFiltering<T> {

    List<AuthorizationResource> getAllResources(Map<String, Object> args);

    T filterByIds(List<Long> authorizedResourceIds, Map<String, Object> args);

    T getAll(Map<String, Object> args);
}

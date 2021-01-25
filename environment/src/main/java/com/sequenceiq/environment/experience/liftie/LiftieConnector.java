package com.sequenceiq.environment.experience.liftie;

import static com.sequenceiq.cloudbreak.util.NullUtil.putIfPresent;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.environment.experience.InvocationBuilderProvider;
import com.sequenceiq.environment.experience.ResponseReader;
import com.sequenceiq.environment.experience.RetryableWebTarget;
import com.sequenceiq.environment.experience.api.LiftieApi;
import com.sequenceiq.environment.experience.liftie.responses.DeleteClusterResponse;
import com.sequenceiq.environment.experience.liftie.responses.ListClustersResponse;
import com.sequenceiq.environment.experience.liftie.responses.PageStats;

@Component
public class LiftieConnector implements LiftieApi {

    private static final String LIFTIE_RESPONSE_RESOLVE_ERROR_MSG = "Unable to resolve Liftie response!";

    private static final Logger LOGGER = LoggerFactory.getLogger(LiftieConnector.class);

    private final InvocationBuilderProvider invocationBuilderProvider;

    private final RetryableWebTarget retryableWebTarget;

    private final LiftiePathProvider liftiePathProvider;

    private final ResponseReader responseReader;

    private final Client client;

    public LiftieConnector(LiftiePathProvider liftiePathProvider, LiftieResponseReader liftieResponseReader, RetryableWebTarget retryableWebTarget,
            Client client, InvocationBuilderProvider invocationBuilderProvider) {
        this.invocationBuilderProvider = invocationBuilderProvider;
        this.liftiePathProvider = liftiePathProvider;
        this.retryableWebTarget = retryableWebTarget;
        this.responseReader = liftieResponseReader;
        this.client = client;
    }

    @Override
    @NotNull
    public ListClustersResponse listClusters(@NotNull String env, @NotNull String tenant, Integer page) {
        LOGGER.debug("About to connect Liftie API to list clusters");
        WebTarget webTarget = client.target(liftiePathProvider.getPathToClustersEndpoint());
        Map<String, String> queryParams = new LinkedHashMap<>();
        putIfPresent(queryParams, "env", env);
        putIfPresent(queryParams, "tenant", tenant);
        putIfPresent(queryParams, "page", page != null ? page.toString() : null);
        webTarget = setQueryParams(webTarget, queryParams);
        Invocation.Builder call = invocationBuilderProvider.createInvocationBuilder(webTarget);
        try (Response result = retryableWebTarget.get(call)) {
            if (result != null) {
                return responseReader.read(webTarget.getUri().toString(), result, ListClustersResponse.class)
                        .orElseThrow(() -> new IllegalStateException(LIFTIE_RESPONSE_RESOLVE_ERROR_MSG));
            }
        } catch (RuntimeException re) {
            LOGGER.warn("Something happened while the Liftie connection has attempted!", re);
        }
        LOGGER.info("Liftie response was null! I don not know what to do so we are expecting this as a normal behavior ¯\\_(ツ)_/¯ ");
        //TODO: figure out the proper way to handle such a case when the result is null (why can it be null, is it a "good" way of having it, etc.)
        ListClustersResponse empty = new ListClustersResponse();
        PageStats ps = new PageStats();
        ps.setTotalPages(0);
        empty.setPage(ps);
        return empty;
    }

    @Override
    public DeleteClusterResponse deleteCluster(@NotNull String clusterId) {
        LOGGER.debug("About to connect Liftie API to delete cluster {}", clusterId);
        WebTarget webTarget = client.target(liftiePathProvider.getPathToClusterEndpoint(clusterId));
        Invocation.Builder call = invocationBuilderProvider.createInvocationBuilder(webTarget);
        try (Response result = retryableWebTarget.delete(call)) {
            if (result != null) {
                return responseReader.read(webTarget.getUri().toString(), result, DeleteClusterResponse.class)
                        .orElseThrow(() -> new IllegalStateException(LIFTIE_RESPONSE_RESOLVE_ERROR_MSG));
            }
        } catch (RuntimeException re) {
            LOGGER.warn("Something happened while the Liftie connection has attempted!", re);
        }
        throw new IllegalStateException(LIFTIE_RESPONSE_RESOLVE_ERROR_MSG);
    }

    private WebTarget setQueryParams(WebTarget webTarget, Map<String, String> nameValuePairs) {
        WebTarget target = webTarget;
        for (Map.Entry<String, String> entry : nameValuePairs.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                target = target.queryParam(entry.getKey(), value);
            }
        }
        return target;
    }

}

package com.sequenceiq.datalake.service.sdx;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.sequenceiq.cloudbreak.api.endpoint.v4.filesystems.FileSystemV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.filesystems.responses.FileSystemParameterV4Responses;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.ClusterV4Request;
import com.sequenceiq.cloudbreak.common.exception.BadRequestException;
import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;
import com.sequenceiq.common.api.cloudstorage.AwsStorageParameters;
import com.sequenceiq.common.api.cloudstorage.CloudStorageRequest;
import com.sequenceiq.common.api.cloudstorage.S3Guard;
import com.sequenceiq.common.api.cloudstorage.StorageIdentityBase;
import com.sequenceiq.common.api.cloudstorage.StorageLocationBase;
import com.sequenceiq.common.api.cloudstorage.old.S3CloudStorageV1Parameters;
import com.sequenceiq.common.api.telemetry.response.LoggingResponse;
import com.sequenceiq.common.model.CloudIdentityType;
import com.sequenceiq.common.model.CloudStorageCdpService;
import com.sequenceiq.common.model.FileSystemType;
import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.environment.api.v1.environment.model.response.DetailedEnvironmentResponse;
import com.sequenceiq.sdx.api.model.SdxCloudStorageRequest;
import com.sequenceiq.sdx.api.model.SdxClusterRequest;

@Service
public class CloudStorageManifester {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudStorageManifester.class);

    @Inject
    private FileSystemV4Endpoint fileSystemV4Endpoint;

    public CloudStorageRequest initCloudStorageRequest(DetailedEnvironmentResponse environment,
            ClusterV4Request clusterRequest, SdxCluster sdxCluster, SdxClusterRequest sdxClusterRequest) {
        CloudStorageRequest cloudStorageRequest = null;
        boolean anyCloudStorageIsConfigured = isCloudStorageConfigured(sdxClusterRequest)
                || isInternalCloudStorageConfigured(clusterRequest);
        boolean loggingConfigured = isLoggingConfigured(environment);
        if (isCloudStorageConfigured(sdxClusterRequest)) {
            LOGGER.debug("Cloud storage configurations found in SDX cluster request.");
            cloudStorageRequest =
                    initSdxCloudStorageRequest(environment,
                            clusterRequest.getBlueprintName(), sdxCluster, sdxClusterRequest);
        } else if (isInternalCloudStorageConfigured(clusterRequest)) {
            LOGGER.debug("Cloud storage configurations found in internal SDX stack request.");
            cloudStorageRequest = clusterRequest.getCloudStorage();
        }

        if (loggingConfigured) {
            LOGGER.debug("Cloud storage logging is enabled.");
            if (!anyCloudStorageIsConfigured) {
                LOGGER.debug("Creating cloud storage request only for logging identity.");
                cloudStorageRequest = new CloudStorageRequest();
            }
            addLogIdentity(cloudStorageRequest, environment);
        }
        if (loggingConfigured || anyCloudStorageIsConfigured) {
            addS3Guard(cloudStorageRequest, environment);
        }
        return cloudStorageRequest;
    }

    private CloudStorageRequest initSdxCloudStorageRequest(DetailedEnvironmentResponse environment,
            String blueprint, SdxCluster sdxCluster, SdxClusterRequest clusterRequest) {
        CloudStorageRequest cloudStorageRequest = new CloudStorageRequest();
        SdxCloudStorageRequest cloudStorage = clusterRequest.getCloudStorage();
        normalizeCloudStorageRequest(cloudStorage);
        validateCloudStorage(environment.getCloudPlatform(), cloudStorage);
        FileSystemParameterV4Responses fileSystemRecommendations = getFileSystemRecommendations(
                blueprint,
                sdxCluster.getClusterName(),
                cloudStorage);
        LOGGER.info("File recommendations {}", fileSystemRecommendations);
        setStorageLocations(fileSystemRecommendations, cloudStorageRequest);
        addIdBrokerIdentity(cloudStorage, cloudStorageRequest);
        return cloudStorageRequest;
    }

    private void addLogIdentity(CloudStorageRequest cloudStorageRequest,
            DetailedEnvironmentResponse environment) {
        if (containsIdentityType(CloudIdentityType.LOG, cloudStorageRequest)) {
            LOGGER.debug("Cloud storage log identity already set. Skip fetching it from environment.");
        } else {
            StorageIdentityBase log = new StorageIdentityBase();
            log.setType(CloudIdentityType.LOG);
            LoggingResponse logging = environment.getTelemetry().getLogging();
            if (logging.getS3() != null) {
                log.setS3(logging.getS3());
            } else if (logging.getAdlsGen2() != null) {
                log.setAdlsGen2(logging.getAdlsGen2());
            } else if (logging.getGcs() != null) {
                log.setGcs(logging.getGcs());
            } else if (logging.getCloudwatch() != null) {
                LOGGER.debug("Cloudwatch will act as s3 storage identity!");
                S3CloudStorageV1Parameters s3CloudwatchParams = new S3CloudStorageV1Parameters();
                s3CloudwatchParams.setInstanceProfile(logging.getCloudwatch().getInstanceProfile());
                log.setS3(s3CloudwatchParams);
            }
            cloudStorageRequest.getIdentities().add(log);
        }
    }

    private void addS3Guard(CloudStorageRequest cloudStorageRequest,
            DetailedEnvironmentResponse environment) {
        if (isS3GuardConfigured(environment, cloudStorageRequest)) {
            String dynamoDbTableName = environment.getAws().getS3guard().getDynamoDbTableName();
            if (!Strings.isNullOrEmpty(dynamoDbTableName)) {
                LOGGER.debug("Setting dynamo db table name s3 guard configuration: {}", dynamoDbTableName);
                AwsStorageParameters awsStorageParameters = new AwsStorageParameters();
                S3Guard s3Guard = new S3Guard();
                s3Guard.setDynamoTableName(environment.getAws().getS3guard().getDynamoDbTableName());
                awsStorageParameters.setS3Guard(s3Guard);
                cloudStorageRequest.setAws(awsStorageParameters);
            }
        }
    }

    @VisibleForTesting
    protected void validateCloudStorage(String cloudPlatform, SdxCloudStorageRequest cloudStorage) {
        if (CloudPlatform.AWS.name().equalsIgnoreCase(cloudPlatform)) {
            if (!isS3AuthenticationConfigured(cloudStorage)) {
                throw new BadRequestException("instance profile must be defined for S3");
            }
            if (!cloudStorage.getBaseLocation().startsWith(FileSystemType.S3.getProtocol() + "://")) {
                throw new BadRequestException("AWS baselocation missing protocol. please specify s3a://");
            }
        } else  if (CloudPlatform.AZURE.name().equalsIgnoreCase(cloudPlatform)) {
            if (!isAzureAuthenticationConfigured(cloudStorage)) {
                throw new BadRequestException("managed identity or account key and account name must be defined for ABFS");
            }
            if (!cloudStorage.getBaseLocation().startsWith(FileSystemType.ADLS_GEN_2.getProtocol() + "://")) {
                throw new BadRequestException("AZURE baselocation missing protocol. please specify abfs://");
            }
        } else if (CloudPlatform.GCP.name().equalsIgnoreCase(cloudPlatform)) {
            if (!isGcsAuthenticationConfigured(cloudStorage)) {
                throw new BadRequestException("service account email must be defined for GCS");
            }
            if (!cloudStorage.getBaseLocation().startsWith(FileSystemType.GCS.getProtocol() + "://")) {
                throw new BadRequestException("GCP baselocation missing protocol. please specify gcs://");
            }
        }
    }

    private boolean isS3AuthenticationConfigured(SdxCloudStorageRequest cloudStorage) {
        return cloudStorage.getS3() != null && !StringUtils.isEmpty(cloudStorage.getS3().getInstanceProfile());
    }

    private boolean isGcsAuthenticationConfigured(SdxCloudStorageRequest cloudStorage) {
        return cloudStorage.getGcs() != null && !StringUtils.isEmpty(cloudStorage.getGcs().getServiceAccountEmail());
    }

    private boolean isAzureAuthenticationConfigured(SdxCloudStorageRequest cloudStorage) {
        return cloudStorage.getAdlsGen2() != null
                && (!StringUtils.isEmpty(cloudStorage.getAdlsGen2().getManagedIdentity())
                || (!StringUtils.isEmpty(cloudStorage.getAdlsGen2().getAccountKey()) && !StringUtils.isEmpty(cloudStorage.getAdlsGen2().getAccountName())));
    }

    protected void normalizeCloudStorageRequest(SdxCloudStorageRequest cloudStorageRequest) {
        cloudStorageRequest.setBaseLocation(cloudStorageRequest.getBaseLocation().strip());
    }

    private void setStorageLocations(FileSystemParameterV4Responses fileSystemRecommendations, CloudStorageRequest cloudStorageRequest) {
        List<StorageLocationBase> storageLocations = fileSystemRecommendations.getResponses().stream().map(response -> {
            StorageLocationBase storageLocation = new StorageLocationBase();
            storageLocation.setValue(response.getDefaultPath());
            storageLocation.setType(CloudStorageCdpService.valueOf(response.getType()));
            return storageLocation;
        }).collect(Collectors.toList());
        cloudStorageRequest.setLocations(storageLocations);
    }

    private boolean isS3GuardConfigured(DetailedEnvironmentResponse environment,
            CloudStorageRequest cloudStorageRequest) {
        return cloudStorageRequest.getAws() == null
                && environment.getAws() != null
                && environment.getAws().getS3guard() != null;
    }

    private boolean isInternalCloudStorageConfigured(ClusterV4Request clusterV4Request) {
        return clusterV4Request.getCloudStorage() != null
                && (!CollectionUtils.isEmpty(clusterV4Request.getCloudStorage().getLocations())
                || !CollectionUtils.isEmpty(clusterV4Request.getCloudStorage().getIdentities()));
    }

    private boolean isCloudStorageConfigured(SdxClusterRequest clusterRequest) {
        return clusterRequest.getCloudStorage() != null
                && StringUtils.isNotEmpty(clusterRequest.getCloudStorage().getBaseLocation());
    }

    private boolean isLoggingConfigured(DetailedEnvironmentResponse environment) {
        return environment.getTelemetry() != null
                && environment.getTelemetry().getLogging() != null;
    }

    private void addIdBrokerIdentity(SdxCloudStorageRequest cloudStorage, CloudStorageRequest cloudStorageRequest) {
        StorageIdentityBase idBroker = new StorageIdentityBase();
        idBroker.setType(CloudIdentityType.ID_BROKER);
        FileSystemType fileSystemType = cloudStorage.getFileSystemType();
        if (isFileSystemConfigured(fileSystemType)) {
            if (fileSystemType.isS3()) {
                idBroker.setS3(cloudStorage.getS3());
            } else if (fileSystemType.isWasb()) {
                idBroker.setWasb(cloudStorage.getWasb());
            } else if (fileSystemType.isAdlsGen2()) {
                idBroker.setAdlsGen2(cloudStorage.getAdlsGen2());
            } else if (fileSystemType.isGcs()) {
                idBroker.setGcs(cloudStorage.getGcs());
            }
            cloudStorageRequest.getIdentities().add(idBroker);
        }
    }

    private boolean containsIdentityType(CloudIdentityType cloudIdentityType,
            CloudStorageRequest cloudStorageRequest) {
        boolean found = false;
        if (cloudStorageRequest != null
                && !CollectionUtils.isEmpty(cloudStorageRequest.getIdentities())) {
            found = cloudStorageRequest.getIdentities()
                    .stream()
                    .anyMatch(storageIdentity -> cloudIdentityType.equals(storageIdentity.getType()));
        }
        return found;
    }

    private boolean isFileSystemConfigured(FileSystemType fileSystemType) {
        return fileSystemType != null;
    }

    private FileSystemParameterV4Responses getFileSystemRecommendations(String blueprint,
            String clusterName, SdxCloudStorageRequest cloudStorageRequest) {

        return fileSystemV4Endpoint.getFileSystemParameters(0L,
                blueprint,
                clusterName,
                "",
                cloudStorageRequest.getBaseLocation(),
                cloudStorageRequest.getFileSystemType().toString(),
                false,
                false);
    }

}

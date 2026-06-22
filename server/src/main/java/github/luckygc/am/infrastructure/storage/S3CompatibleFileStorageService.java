package github.luckygc.am.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;

import org.apache.commons.lang3.StringUtils;

import github.luckygc.am.common.storage.StorageType;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3CompatibleFileStorageService implements FileStorageBackend, AutoCloseable {

    private final StorageType storageType;

    private final S3Client client;

    private final String bucket;

    S3CompatibleFileStorageService(
            StorageType storageType, FileStorageProperties.ObjectStorage properties) {
        this.storageType = storageType;
        this.bucket = requireText(properties.getBucket(), "archive.storage.object.bucket");

        S3ClientBuilder builder =
                S3Client.builder()
                        .region(
                                Region.of(
                                        requireText(
                                                properties.getRegion(),
                                                "archive.storage.object.region")))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create(
                                                requireText(
                                                        properties.getAccessKey(),
                                                        "archive.storage.object.access-key"),
                                                requireText(
                                                        properties.getSecretKey(),
                                                        "archive.storage.object.secret-key"))))
                        .serviceConfiguration(
                                S3Configuration.builder()
                                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                                        .build());
        if (StringUtils.isNotBlank(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
        }
        this.client = builder.build();
    }

    @Override
    public StorageType storageType() {
        return storageType;
    }

    @Override
    public String bucketName() {
        return bucket;
    }

    @Override
    public StorageObjectInfo putObject(
            String objectKey, InputStream inputStream, long contentLength, String contentType) {
        if (contentLength < 0) {
            throw new StorageException("对象存储上传必须提供 contentLength");
        }
        String normalizedObjectKey = ObjectKeys.normalize(objectKey);
        PutObjectRequest.Builder request =
                PutObjectRequest.builder().bucket(bucket).key(normalizedObjectKey);
        if (StringUtils.isNotBlank(contentType)) {
            request.contentType(contentType);
        }
        String eTag =
                client.putObject(
                                request.build(),
                                RequestBody.fromInputStream(inputStream, contentLength))
                        .eTag();
        return new StorageObjectInfo(
                storageType(),
                bucketName(),
                normalizedObjectKey,
                contentLength,
                contentType,
                null,
                eTag);
    }

    @Override
    public FileStorageResource getObject(String objectKey) {
        String normalizedObjectKey = ObjectKeys.normalize(objectKey);
        ResponseInputStream<GetObjectResponse> response =
                client.getObject(
                        GetObjectRequest.builder().bucket(bucket).key(normalizedObjectKey).build());
        GetObjectResponse metadata = response.response();
        return new FileStorageResource(
                storageType(),
                bucketName(),
                normalizedObjectKey,
                response,
                metadata.contentLength(),
                metadata.contentType());
    }

    @Override
    public boolean objectExists(String objectKey) {
        String normalizedObjectKey = ObjectKeys.normalize(objectKey);
        try {
            client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(normalizedObjectKey).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(ObjectKeys.normalize(objectKey))
                        .build());
    }

    @Override
    public void close() {
        client.close();
    }

    private static String requireText(String value, String propertyName) {
        if (StringUtils.isBlank(value)) {
            throw new StorageException("缺少文件存储配置: " + propertyName);
        }
        return value.trim();
    }
}

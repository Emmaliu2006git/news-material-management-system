package com.example.newsmaterial.storage;

import com.example.newsmaterial.common.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3StorageAdapterTest {
    @Test void missingHeadObjectIsAnUnverifiedUploadRatherThanStorageOutage() {
        S3Client client=mock(S3Client.class);
        when(client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());
        S3StorageAdapter adapter=new S3StorageAdapter(client,mock(S3Presigner.class),"bucket");

        assertThatThrownBy(()->adapter.inspect("uploads/random/file.jpg"))
                .isInstanceOfSatisfying(ApiException.class,error->{
                    org.assertj.core.api.Assertions.assertThat(error.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("UPLOAD_NOT_VERIFIED");
                });
    }
}

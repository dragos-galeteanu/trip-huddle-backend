package com.triphuddle.api.chat.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.triphuddle.api.chat.config.ChatProperties;
import com.triphuddle.api.chat.dto.AttachmentRequest;
import com.triphuddle.api.chat.dto.CreateUploadUrlRequest;
import com.triphuddle.api.chat.dto.UploadUrlResponse;
import com.triphuddle.api.chat.entity.MessageAttachment;
import com.triphuddle.common.config.StorageBucketProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AttachmentServiceTest {

    static final long MAX = 1_000;

    final Storage storage = mock(Storage.class);
    final UUID chat = UUID.randomUUID();
    final UUID me = UUID.randomUUID();
    final String prefix = "chats/%s/%s/".formatted(chat, me);

    static AttachmentService service(Storage storage, String bucket) {
        @SuppressWarnings("unchecked")
        ObjectProvider<Storage> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(storage);
        return new AttachmentService(provider, new ChatProperties(null,
                new StorageBucketProperties("project", bucket, MAX, 15, 60), null));
    }

    final AttachmentService service = service(storage, "bucket");

    @Test
    void an_upload_url_is_signed_for_the_uploaders_own_path_and_declared_size() throws Exception {
        given(storage.signUrl(any(BlobInfo.class), eq(15L), eq(TimeUnit.MINUTES), any(Storage.SignUrlOption[].class)))
                .willReturn(new URL("https://storage.example/upload"));

        UploadUrlResponse response = service.createUploadUrl(chat, me,
                new CreateUploadUrlRequest("a.png", "image/png", 500L));

        assertThat(response.uploadUrl()).isEqualTo("https://storage.example/upload");
        assertThat(response.objectPath()).startsWith(prefix);
        assertThat(response.requiredHeaders()).containsEntry("x-goog-content-length-range", "0,500")
                .containsEntry("Content-Type", "image/png");
    }

    @Test
    void uploads_larger_than_the_limit_or_without_a_bucket_are_refused() {
        assertThatThrownBy(() -> service.createUploadUrl(chat, me, new CreateUploadUrlRequest("a", "b", MAX + 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service(null, "").createUploadUrl(chat, me, new CreateUploadUrlRequest("a", "b", 1L)))
                .hasMessageContaining("not enabled");
        assertThatThrownBy(() -> service(storage, " ").createUploadUrl(chat, me,
                new CreateUploadUrlRequest("a", "b", 1L))).hasMessageContaining("not enabled");
    }

    @Test
    void a_message_attaches_only_its_senders_finished_uploads_within_the_limit() {
        Blob blob = mock(Blob.class);
        given(blob.getContentType()).willReturn("image/png");
        given(blob.getSize()).willReturn(10L);
        given(storage.get(BlobId.of("bucket", prefix + "ok"))).willReturn(blob);
        Blob huge = mock(Blob.class);
        given(huge.getSize()).willReturn(MAX + 1);
        given(storage.get(BlobId.of("bucket", prefix + "huge"))).willReturn(huge);

        MessageAttachment attachment = service.resolveUploaded(chat, me, new AttachmentRequest(prefix + "ok", "a.png"));

        assertThat(attachment).isEqualTo(new MessageAttachment(prefix + "ok", "a.png", "image/png", 10L));
        assertThatThrownBy(() -> service.resolveUploaded(chat, me,
                new AttachmentRequest("chats/%s/%s/x".formatted(chat, UUID.randomUUID()), "a")))
                .hasMessageContaining("not uploaded by you");
        assertThatThrownBy(() -> service.resolveUploaded(chat, me, new AttachmentRequest(prefix + "missing", "a")))
                .hasMessageContaining("has not been uploaded");
        assertThatThrownBy(() -> service.resolveUploaded(chat, me, new AttachmentRequest(prefix + "huge", "a")))
                .hasMessageContaining("cannot exceed");
    }

    @Test
    void a_download_url_is_signed_when_it_can_be_and_left_out_when_it_cannot() throws Exception {
        given(storage.signUrl(any(BlobInfo.class), eq(60L), eq(TimeUnit.MINUTES), any(Storage.SignUrlOption[].class)))
                .willReturn(new URL("https://storage.example/download"));

        assertThat(service.createDownloadUrl("x")).contains("https://storage.example/download");
        assertThat(service(null, "").createDownloadUrl("x")).isEmpty();

        given(storage.signUrl(any(BlobInfo.class), anyLong(), any(), any(Storage.SignUrlOption[].class)))
                .willThrow(new StorageException(500, "down"));
        assertThat(service.createDownloadUrl("x")).isEmpty();
    }
}

package com.triphuddle.api.chat.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.triphuddle.testsupport.security.Caller;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CHT-2: with a bucket set up, a participant uploads to a signed URL, attaches the file to a message, and everyone in
 * the conversation gets a signed link to download it. Google Cloud Storage itself is a mock.
 */
@TestPropertySource(properties = "app.chat.attachments.bucket=chat-test")
class AttachmentsIT extends ChatApiSupport {

    @MockBean
    Storage storage;

    @Test
    void aParticipantUploadsAttachesAndEveryoneGetsALinkToDownload() throws Exception {
        UUID ana = user();
        UUID ben = user();
        UUID chat = privateChat(ana, ben);
        given(storage.signUrl(any(BlobInfo.class), eq(15L), eq(TimeUnit.MINUTES), any(Storage.SignUrlOption[].class)))
                .willReturn(new URL("https://storage.example/upload?signed"));
        given(storage.signUrl(any(BlobInfo.class), eq(60L), eq(TimeUnit.MINUTES), any(Storage.SignUrlOption[].class)))
                .willReturn(new URL("https://storage.example/download?signed"));

        JsonNode upload = body(call(post(CONVERSATIONS + "/" + chat + "/attachments/upload-url"), Caller.CUSTOMER, ana,
                Map.of("fileName", "map.png", "contentType", "image/png", "sizeBytes", 2048))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://storage.example/upload?signed"))
                .andExpect(jsonPath("$.method").value("PUT")));
        String objectPath = upload.path("objectPath").asText();
        assertThat(objectPath).startsWith("chats/%s/%s/".formatted(chat, ana));
        Blob uploaded = mock(Blob.class);
        given(uploaded.getContentType()).willReturn("image/png");
        given(uploaded.getSize()).willReturn(2048L);
        given(storage.get(BlobId.of("chat-test", objectPath))).willReturn(uploaded);

        sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", UUID.randomUUID().toString(),
                "attachments", List.of(Map.of("objectPath", objectPath, "fileName", "map.png"))))
                .andExpect(status().isCreated());

        JsonNode attachment = history(chat, Caller.CUSTOMER, ben).get(0).path("attachments").get(0);
        assertThat(attachment.path("fileName").asText()).isEqualTo("map.png");
        assertThat(attachment.path("contentType").asText()).isEqualTo("image/png");
        assertThat(attachment.path("sizeBytes").asLong()).isEqualTo(2048);
        assertThat(attachment.path("downloadUrl").asText()).isEqualTo("https://storage.example/download?signed");
    }

    @Test
    void anOutsiderGetsNoUploadUrlAndNobodyAttachesSomeoneElsesUpload() throws Exception {
        UUID ana = user();
        UUID ben = user();
        UUID chat = privateChat(ana, ben);
        given(storage.signUrl(any(BlobInfo.class), anyLong(), any(), any(Storage.SignUrlOption[].class)))
                .willReturn(new URL("https://storage.example/upload?signed"));

        call(post(CONVERSATIONS + "/" + chat + "/attachments/upload-url"), Caller.CUSTOMER, user(),
                Map.of("fileName", "map.png", "contentType", "image/png", "sizeBytes", 2048))
                .andExpect(status().isForbidden());
        String anasUpload = body(call(post(CONVERSATIONS + "/" + chat + "/attachments/upload-url"), Caller.CUSTOMER,
                ana, Map.of("fileName", "map.png", "contentType", "image/png", "sizeBytes", 2048)))
                .path("objectPath").asText();

        sendMessage(chat, Caller.CUSTOMER, ben, Map.of("clientMessageId", UUID.randomUUID().toString(),
                "attachments", List.of(Map.of("objectPath", anasUpload, "fileName", "map.png"))))
                .andExpect(status().isUnprocessableEntity());
        given(storage.get(argThat((BlobId id) -> id.getName().equals(anasUpload)))).willReturn(null);
        sendMessage(chat, Caller.CUSTOMER, ana, Map.of("clientMessageId", UUID.randomUUID().toString(),
                "attachments", List.of(Map.of("objectPath", anasUpload, "fileName", "map.png"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("has not been uploaded")));
    }
}

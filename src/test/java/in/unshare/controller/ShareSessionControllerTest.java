package in.unshare.controller;

import in.unshare.dto.ShareSessionResponse;
import in.unshare.exception.ShareSessionNotFoundException;
import in.unshare.service.RateLimiterService;
import in.unshare.service.ShareSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ShareSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ShareSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShareSessionService shareSessionService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    void createShare_returns201WithBody() throws Exception {
        when(rateLimiterService.tryAcquire(anyString())).thenReturn(true);
        when(shareSessionService.createShare(any())).thenReturn(
                new ShareSessionResponse("tok-1", "http://x/s/tok-1", "WAITING_FOR_RECEIVER",
                        "NOT_CONNECTED", "NOT_CONNECTED", 1, Instant.now(), Instant.now().plusSeconds(1800), Instant.now(), List.of()));

        String body = """
                {"files":[{"fileName":"a.txt","fileSize":10,"mimeType":"text/plain"}]}
                """;

        mockMvc.perform(post("/api/v1/shares").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("tok-1"));
    }

    @Test
    void createShare_rejectsEmptyFileList() throws Exception {
        String body = """
                {"files":[]}
                """;

        mockMvc.perform(post("/api/v1/shares").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShare_returns429WhenRateLimited() throws Exception {
        when(rateLimiterService.tryAcquire(anyString())).thenReturn(false);

        String body = """
                {"files":[{"fileName":"a.txt","fileSize":10,"mimeType":"text/plain"}]}
                """;

        mockMvc.perform(post("/api/v1/shares").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void getShare_returns404WhenMissing() throws Exception {
        when(shareSessionService.getByToken("missing")).thenThrow(new ShareSessionNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/shares/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void joinShare_returnsOk() throws Exception {
        when(shareSessionService.join("tok-1")).thenReturn(
                new ShareSessionResponse("tok-1", "http://x/s/tok-1", "CONNECTED",
                        "NOT_CONNECTED", "CONNECTED", 1, Instant.now(), Instant.now().plusSeconds(1800), Instant.now(), List.of()));

        mockMvc.perform(post("/api/v1/shares/tok-1/join"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"));
    }

    @Test
    void cancelShare_returnsOk() throws Exception {
        when(shareSessionService.cancel("tok-1")).thenReturn(
                new ShareSessionResponse("tok-1", "http://x/s/tok-1", "CANCELLED",
                        "NOT_CONNECTED", "NOT_CONNECTED", 1, Instant.now(), Instant.now().plusSeconds(1800), Instant.now(), List.of()));

        mockMvc.perform(delete("/api/v1/shares/tok-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}

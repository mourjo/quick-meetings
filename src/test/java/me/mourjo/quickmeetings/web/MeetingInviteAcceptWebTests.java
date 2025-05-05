package me.mourjo.quickmeetings.web;

import static me.mourjo.quickmeetings.utils.RequestUtils.readJsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.it.BaseIT;
import me.mourjo.quickmeetings.utils.RequestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class MeetingInviteAcceptWebTests extends BaseIT {

    @SneakyThrows
    @Test
    void inviteTest() {
        var aliceMeetingId = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(30)
        );

        meetingsService.invite(aliceMeetingId, List.of(bob.id(), charlie.id()));

        var req = RequestUtils.inviteAcceptanceRequest(
            aliceMeetingId,
            bob.id()
        );
        var result = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Accepted successfully");

        req = RequestUtils.inviteAcceptanceRequest(
            aliceMeetingId,
            dick.id()
        );
        result = mockMvc.perform(req).andExpect(status().is4xxClientError()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Failed to accept invite");

        req = RequestUtils.inviteAcceptanceRequest(
            928374L, // non-existent meeting
            bob.id()
        );
        result = mockMvc.perform(req).andExpect(status().is4xxClientError()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Failed to accept invite");

        req = RequestUtils.inviteAcceptanceRequest(
            aliceMeetingId,
            alice.id()
        );
        result = mockMvc.perform(req).andExpect(status().is4xxClientError()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Failed to accept invite");
    }

    @SneakyThrows
    void successful(MockHttpServletRequestBuilder req) {
        var result = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Accepted successfully");
    }

    @SneakyThrows
    void failure(MockHttpServletRequestBuilder req) {
        var result = mockMvc.perform(req).andExpect(status().is4xxClientError()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Failed to accept invite");
    }


}

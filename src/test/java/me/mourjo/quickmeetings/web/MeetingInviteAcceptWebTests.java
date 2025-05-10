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
        var aliceMeeting = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(30)
        );

        meetingsService.invite(aliceMeeting.id(), List.of(bob.id(), charlie.id()));

        assertSuccess(
            RequestUtils.inviteAcceptanceRequest(
                aliceMeeting.id(),
                bob.id()
            )
        );

        assertFailure(
            RequestUtils.inviteAcceptanceRequest(
                aliceMeeting.id(),
                dick.id()
            ),
            "Failed to accept invite"
        );

        assertFailure(
            RequestUtils.inviteAcceptanceRequest(
                928374L, // non-existent meeting
                bob.id()
            ),
            "Meeting 928374 not found"
        );

        assertFailure(
            RequestUtils.inviteAcceptanceRequest(
                aliceMeeting.id(),
                alice.id()
            ),
            "Overlapping meetings exist"
        );

    }

    @SneakyThrows
    void assertSuccess(MockHttpServletRequestBuilder req) {
        var result = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Accepted successfully");
    }

    @SneakyThrows
    void assertFailure(MockHttpServletRequestBuilder req, String message) {
        var result = mockMvc.perform(req).andExpect(status().is4xxClientError()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo(message);
    }
}

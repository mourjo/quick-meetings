package me.mourjo.quickmeetings.web;

import static me.mourjo.quickmeetings.utils.RequestUtils.readJsonPath;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.it.BaseIT;
import me.mourjo.quickmeetings.utils.RequestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class MeetingInviteRejectionWebTests extends BaseIT {

    @SneakyThrows
    @Test
    void rejectTest() {
        var aliceMeeting = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now.plusMinutes(60),
            now.plusMinutes(75)
        );

        var erinMeeting = meetingsService.createMeeting(
            "Erin's meeting",
            erin.id(),
            now,
            now.plusMinutes(25)
        );

        meetingsService.invite(erinMeeting.id(), List.of(alice.id(), charlie.id()));
        meetingsService.accept(erinMeeting.id(), charlie.id());

        assertSuccess(
            RequestUtils.inviteRejectionRequest(
                erinMeeting.id(),
                charlie.id()
            )
        );

        assertSuccess(
            RequestUtils.inviteRejectionRequest(
                erinMeeting.id(),
                alice.id()
            )
        );

        assertFailure(
            RequestUtils.inviteRejectionRequest(
                aliceMeeting.id(),
                charlie.id()
            )
        );

        assertFailure(
            RequestUtils.inviteRejectionRequest(
                818, // unknown meeting
                alice.id()
            )
        );

        assertFailure(
            RequestUtils.inviteRejectionRequest(
                erinMeeting.id(),
                99919 // unknown user
            )
        );
    }

    @SneakyThrows
    void assertSuccess(MockHttpServletRequestBuilder req) {
        var result = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Rejected successfully");
    }

    @SneakyThrows
    void assertFailure(MockHttpServletRequestBuilder req) {
        var result = mockMvc.perform(req).andExpect(status().is4xxClientError()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Failed to reject invite");
    }

}

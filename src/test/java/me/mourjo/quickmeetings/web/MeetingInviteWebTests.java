package me.mourjo.quickmeetings.web;

import static me.mourjo.quickmeetings.utils.RequestUtils.readJsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import lombok.SneakyThrows;
import me.mourjo.quickmeetings.it.BaseIT;
import me.mourjo.quickmeetings.utils.RequestUtils;
import org.junit.jupiter.api.Test;

public class MeetingInviteWebTests extends BaseIT {

    @SneakyThrows
    @Test
    void inviteTest() {
        var aliceMeetingId = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(30)
        );

        var req = RequestUtils.inviteCreationRequest(
            aliceMeetingId,
            List.of(bob.id(), charlie.id())
        );

        var result = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        assertThat(readJsonPath(result, "$.message")).isEqualTo("Invited successfully");
    }

    @SneakyThrows
    @Test
    void overlappingMeeting() {
        // Alice creates a meeting
        var aliceMeetingId = meetingsService.createMeeting(
            "Alice's meeting",
            alice.id(),
            now,
            now.plusMinutes(30)
        );

        // inviting Bob and Charlie to Alice's meeting is OK
        mockMvc.perform(
            RequestUtils.inviteCreationRequest(
                aliceMeetingId,
                List.of(bob.id(), charlie.id())
            )
        ).andExpect(status().is2xxSuccessful());

        // Dick creates an overlapping meeting with Alice
        var dickMeetingId = meetingsService.createMeeting(
            "Dick's meeting",
            dick.id(),
            now.plusMinutes(15),
            now.plusMinutes(45)
        );

        // inviting Alice to Dick's meeting should fail
        var req = RequestUtils.inviteCreationRequest(
            dickMeetingId,
            List.of(erin.id(), alice.id())
        );
        var result = mockMvc.perform(req).andExpect(status().is4xxClientError()).andReturn();

        assertThat(readJsonPath(result, "$.message")).isEqualTo("Users have conflicts");

        // inviting Erin and Frank to Dick's meeting is OK multiple times
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                RequestUtils.inviteCreationRequest(
                    dickMeetingId,
                    List.of(erin.id(), frank.id())
                )
            ).andExpect(status().is2xxSuccessful());
        }

        // inviting Dick to Alice's meeting is not OK
        mockMvc.perform(
            RequestUtils.inviteCreationRequest(
                aliceMeetingId,
                List.of(dick.id())
            )
        ).andExpect(status().is4xxClientError());
    }
}

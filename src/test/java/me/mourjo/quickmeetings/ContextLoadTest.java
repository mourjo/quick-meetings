package me.mourjo.quickmeetings;

import static org.assertj.core.api.Assertions.assertThat;

import me.mourjo.quickmeetings.db.MeetingRepository;
import me.mourjo.quickmeetings.service.MeetingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class ContextLoadTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads() {
        var meetingRepositoryBean = context.getBean("meetingRepository");
        assertThat(meetingRepositoryBean)
            .isInstanceOf(MeetingRepository.class)
            .isNotNull();

        var meetingsServiceBean = context.getBean("meetingsService");
        assertThat(meetingsServiceBean)
            .isInstanceOf(MeetingsService.class)
            .isNotNull();
    }

}

package com.personalai.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.personalai.platform.PersonalAiAgentPlatformApplication;
import com.personalai.platform.agents.AgentRepository;
import com.personalai.platform.agents.InMemoryAgentRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class DemoRuntimeTest {
    @Test
    void cleanDemoStartsWithoutCredentialsDatabaseOrPersonalPersistence() throws Exception {
        try (var application = SpringApplication.run(PersonalAiAgentPlatformApplication.class,
                "--server.port=0", "--server.address=127.0.0.1", "--sai.mode=demo",
                "--sai.database.url=", "--sai.database.username=", "--sai.database.password-file=")) {
            assertThat(application.getBeansOfType(DataSource.class)).isEmpty();
            assertThat(application.getBeansOfType(PersistentDocumentStore.class)).isEmpty();
            assertThat(application.getBean(AgentRepository.class)).isInstanceOf(InMemoryAgentRepository.class);
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) application).build();
            mvc.perform(get("/api/runtime-config")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.mode").value("demo"))
                    .andExpect(jsonPath("$.persistent").value(false))
                    .andExpect(jsonPath("$.dataStorage").value("external"))
                    .andExpect(jsonPath("$.cloudRequestsMayLeaveDevice").value(true));
        }
    }
}

package io.gendora;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AppTest {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Test
    void contextLoads() {
        // Verify that the application context loads successfully
        assertThat(applicationContext).isNotNull();
        
        // Verify that our main application class is loaded as a bean
        assertThat(applicationContext.containsBean("app")).isTrue();
        
        // Verify that our controller is loaded
        assertThat(applicationContext.containsBean("IDGeneratorController")).isTrue();
    }
}

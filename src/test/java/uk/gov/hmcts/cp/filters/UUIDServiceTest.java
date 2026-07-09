package uk.gov.hmcts.cp.filters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class UUIDServiceTest {

    @InjectMocks
    UUIDService uuidService;

    @Test
    void random_should_return_uuid() {
        assertThat(uuidService.random()).isNotNull();
    }

    @Test
    void random_should_return_string() {
        assertThat(uuidService.randomString()).isNotNull();
    }
}
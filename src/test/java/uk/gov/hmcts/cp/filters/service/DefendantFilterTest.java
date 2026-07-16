package uk.gov.hmcts.cp.filters.service;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase.Defendant;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefendantFilterTest {

    private final DefendantFilter defendantFilter = new DefendantFilter();

    @Test
    void should_return_all_defendants_when_no_filters_supplied() {
        Defendant defendant1 = Defendant.builder().id(UUID.randomUUID()).build();
        Defendant defendant2 = Defendant.builder().id(UUID.randomUUID()).build();

        List<Defendant> result = defendantFilter.filter(List.of(defendant1, defendant2), null, null);

        assertThat(result).containsExactly(defendant1, defendant2);
    }

    @Test
    void should_filter_by_defendantId_when_supplied() {
        UUID defendantId1 = UUID.randomUUID();
        Defendant defendant1 = Defendant.builder().id(defendantId1).build();
        Defendant defendant2 = Defendant.builder().id(UUID.randomUUID()).build();

        List<Defendant> result = defendantFilter.filter(List.of(defendant1, defendant2), null, defendantId1);

        assertThat(result).containsExactly(defendant1);
    }

    @Test
    void should_filter_by_masterDefendantId_when_supplied() {
        UUID masterDefendantId1 = UUID.randomUUID();
        UUID masterDefendantId2 = UUID.randomUUID();
        Defendant defendant1 = Defendant.builder().id(UUID.randomUUID()).masterDefendantId(masterDefendantId1).build();
        Defendant defendant2 = Defendant.builder().id(UUID.randomUUID()).masterDefendantId(masterDefendantId2).build();

        List<Defendant> result = defendantFilter.filter(List.of(defendant1, defendant2), masterDefendantId2, null);

        assertThat(result).containsExactly(defendant2);
    }

    @Test
    void should_filter_by_both_masterDefendantId_and_defendantId_when_supplied() {
        UUID masterDefendantId = UUID.randomUUID();
        UUID defendantId = UUID.randomUUID();
        Defendant matching = Defendant.builder().id(defendantId).masterDefendantId(masterDefendantId).build();
        Defendant wrongDefendantId = Defendant.builder().id(UUID.randomUUID()).masterDefendantId(masterDefendantId).build();
        Defendant wrongMasterDefendantId = Defendant.builder().id(defendantId).masterDefendantId(UUID.randomUUID()).build();

        List<Defendant> result = defendantFilter.filter(List.of(matching, wrongDefendantId, wrongMasterDefendantId), masterDefendantId, defendantId);

        assertThat(result).containsExactly(matching);
    }

    @Test
    void should_return_empty_list_when_no_defendant_matches_filter() {
        Defendant defendant = Defendant.builder().id(UUID.randomUUID()).build();

        List<Defendant> result = defendantFilter.filter(List.of(defendant), null, UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_list_when_defendants_list_is_null() {
        List<Defendant> result = defendantFilter.filter(null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void should_skip_null_elements_in_defendants_list() {
        Defendant defendant = Defendant.builder().id(UUID.randomUUID()).build();

        List<Defendant> result = defendantFilter.filter(Arrays.asList(defendant, null), null, null);

        assertThat(result).containsExactly(defendant);
    }
}
package com.triphuddle.common.entity;

import com.triphuddle.common.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaseRecordsTest {

    @Test
    void newRecordsAreNeitherDisabledNorDeleted() {
        BaseJpaEntity entity = new BaseJpaEntity();
        BaseMongoDocument document = new BaseMongoDocument();

        assertThat(entity.getDisabledAt()).isNull();
        assertThat(entity.getDeletedAt()).isNull();
        assertThat(document.getDisabledAt()).isNull();
        assertThat(document.getDeletedAt()).isNull();
    }

    @Test
    void mapsAListInOrderThroughTheSingleMapping() {
        BaseMapper<String, Object, Object, Integer> mapper = new BaseMapper<>() {
            @Override public Integer toResponse(String entity) { return entity.length(); }
            @Override public void createEntityFromRequest(String entity, Object request) { }
            @Override public void updateEntityFromRequest(String entity, Object request) { }
        };

        assertThat(mapper.toResponseList(List.of("a", "abc", "ab"))).containsExactly(1, 3, 2);
    }
}

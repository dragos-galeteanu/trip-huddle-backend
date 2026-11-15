package com.triphuddle.api.chat.dto;

import com.triphuddle.api.chat.entity.ConversationType;
import com.triphuddle.common.filter.CursorPaginationFilter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * The cursor and page size of the caller's conversation list, optionally narrowed to one type.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ConversationListFilter extends CursorPaginationFilter {

    private ConversationType type;
}

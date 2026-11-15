package com.triphuddle.api.license.dto;

import com.triphuddle.api.license.entity.LicenseStatus;
import com.triphuddle.api.license.entity.LicenseTarget;
import com.triphuddle.common.filter.PaginationFilter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * The page and filters of the license list; a missing filter keeps every license.
 * The status filter is only honoured for staff: everyone else sees published licenses.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LicenseFilter extends PaginationFilter {

    private LicenseStatus status;

    private LicenseTarget target;
}

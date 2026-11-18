package com.triphuddle.api.payment;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.triphuddle.testsupport.architecture.ArchitectureRules;

@AnalyzeClasses(packages = "com.triphuddle.api.payment", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests RULES = ArchTests.in(ArchitectureRules.class);
}

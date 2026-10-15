package com.phonghub.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Kiểm tra kiến trúc hexagonal: domain/application không phụ thuộc Spring hay adapter. */
@AnalyzeClasses(packages = "com.phonghub", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domainKhongPhuThuocSpring = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");

    @ArchTest
    static final ArchRule domainKhongPhuThuocApplicationHayAdapter = noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..");

    @ArchTest
    static final ArchRule applicationKhongPhuThuocAdapter = noClasses().that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule applicationKhongPhuThuocWebSpring = noClasses().that().resideInAPackage("..application..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..");
}

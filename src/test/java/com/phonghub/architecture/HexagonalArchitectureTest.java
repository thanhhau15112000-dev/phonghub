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

    @ArchTest
    static final ArchRule adapterInKhongPhuThuocAdapterOut = noClasses().that().resideInAPackage("..adapter.in..")
        .should().dependOnClassesThat().resideInAnyPackage("..adapter.out..");

    /**
     * Controller không được gọi trực tiếp RepositoryPort (phải qua UseCase).
     * Ngoại lệ rõ ràng cho các UI Controller cũ đang nợ kỹ thuật để chuyển đổi dần (#276).
     */
    @ArchTest
    static final ArchRule controllerKhongGoiRepositoryPortTrucTiep = noClasses()
        .that().resideInAPackage("..adapter.in.web..")
        .and().haveSimpleNameEndingWith("Controller")
        .and().doNotHaveSimpleName("AuthUiController")
        .and().doNotHaveSimpleName("RoomUiController")
        .and().doNotHaveSimpleName("ContractUiController")
        .and().doNotHaveSimpleName("HandoverUiController")
        .and().doNotHaveSimpleName("MaintenanceUiController")
        .and().doNotHaveSimpleName("PropertyUiController")
        .and().doNotHaveSimpleName("TenantDocumentUiController")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("RepositoryPort");
}

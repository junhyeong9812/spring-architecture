package com.shoptracker;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

public class ModuleStructureTest {

    @Test
    void verifyModuleStructure() {
        ApplicationModules modules = ApplicationModules.of(ShopTrackerApplication.class);
        modules.verify();
    }

    @Test
    void generateModuleDocumentation() {
        ApplicationModules modules = ApplicationModules.of(ShopTrackerApplication.class);
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }

    @Test
    void createModuleDocumentation() {
        ApplicationModules modules = ApplicationModules.of(ShopTrackerApplication.class);
        new Documenter(modules)
                .writeModulesAsPlantUml()                    // 전체 모듈 관계도
                .writeIndividualModulesAsPlantUml();          // 모듈별 상세 다이어그램
        // 결과: build/spring-modulith-docs/ 에 .puml 파일 생성
    }
}

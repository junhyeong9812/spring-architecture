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
}

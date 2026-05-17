package com.synopticengine.api

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTests {
    private val modules = ApplicationModules.of(Application::class.java)

    @Test
    @DisplayName("All module boundaries are respected")
    fun verifiesModularStructure() {
        modules.verify()
    }

    @Test
    @DisplayName("Spring Modulith documentation is generated")
    fun generatesDocumentation() {
        Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases()
            .writeAggregatingDocument()
    }
}

package kg.aidarbek.smpp.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import kg.aidarbek.smpp.codec.PduFramer;
import kg.aidarbek.smpp.codec.PduHeaderCodec;
import kg.aidarbek.smpp.protocol.PduHeader;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
    private static final String PROTOCOL = "kg.aidarbek.smpp.protocol..";
    private static final String CODEC = "kg.aidarbek.smpp.codec..";
    private static final String PROFILE = "kg.aidarbek.smpp.profile..";
    private static final JavaClasses LIBRARY = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("kg.aidarbek.smpp");

    @Test
    void importsProductionTypesWithoutTests() {
        assertTrue(LIBRARY.contain(PduHeader.class));
        assertTrue(LIBRARY.contain(PduHeaderCodec.class));
        assertTrue(LIBRARY.contain(PduFramer.class));
        assertFalse(LIBRARY.contain(ArchitectureTest.class));
    }

    @Test
    void protocolDependsOnlyOnProtocolAndJdkValues() {
        classes()
                .that()
                .resideInAPackage(PROTOCOL)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(PROTOCOL, "java.lang..", "java.math..", "java.time..", "java.util..")
                .check(LIBRARY);
    }

    @Test
    void codecsDependOnlyOnCodecsProtocolProfilesAndJdkFacilities() {
        classes()
                .that()
                .resideInAPackage(CODEC)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        CODEC,
                        PROTOCOL,
                        PROFILE,
                        "java.lang..",
                        "java.math..",
                        "java.nio..",
                        "java.time..",
                        "java.util..")
                .check(LIBRARY);
    }

    @Test
    void protocolProfilesAndCodecsDoNotDependOnInfrastructurePackages() {
        noClasses()
                .that()
                .resideInAnyPackage(PROTOCOL, PROFILE, CODEC)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "java.net..", "javax.net..", "java.nio.channels..", "java.nio.file..", "java.util.concurrent..")
                .check(LIBRARY);
    }

    @Test
    void libraryPackagesAreFreeOfCycles() {
        slices().matching("kg.aidarbek.smpp.(*)..").should().beFreeOfCycles().check(LIBRARY);
    }
}

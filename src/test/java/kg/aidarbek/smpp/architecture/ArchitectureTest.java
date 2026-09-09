package kg.aidarbek.smpp.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
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
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.SmppClient;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.PduHeader;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.session.SessionState;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.transport.TcpTransportConfig;
import org.junit.jupiter.api.Test;

final class ArchitectureTest {
    private static final String PROTOCOL = "kg.aidarbek.smpp.protocol..";
    private static final String CODEC = "kg.aidarbek.smpp.codec..";
    private static final String PROFILE = "kg.aidarbek.smpp.profile..";
    private static final String SESSION = "kg.aidarbek.smpp.session..";
    private static final String REQUEST = "kg.aidarbek.smpp.request..";
    private static final String SPI = "kg.aidarbek.smpp.spi..";
    private static final String TRANSPORT = "kg.aidarbek.smpp.transport..";
    private static final String ENDPOINT = "kg.aidarbek.smpp.endpoint..";
    private static final JavaClasses LIBRARY = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("kg.aidarbek.smpp");

    @Test
    void importsProductionTypesWithoutTests() {
        assertTrue(LIBRARY.contain(PduHeader.class));
        assertTrue(LIBRARY.contain(PduHeaderCodec.class));
        assertTrue(LIBRARY.contain(PduFramer.class));
        assertTrue(LIBRARY.contain(ProtocolProfile.class));
        assertTrue(LIBRARY.contain(SmppVersion.class));
        assertTrue(LIBRARY.contain(SessionState.class));
        assertTrue(LIBRARY.contain(RequestOptions.class));
        assertTrue(LIBRARY.contain(WriteClass.class));
        assertTrue(LIBRARY.contain(TcpTransportConfig.class));
        assertTrue(LIBRARY.contain(EndpointOptions.class));
        assertTrue(LIBRARY.contain(SmppClient.class));
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
    void profilesDependOnlyOnProfilesProtocolAndJdkValues() {
        classes()
                .that()
                .resideInAPackage(PROFILE)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(PROFILE, PROTOCOL, "java.lang..", "java.math..", "java.time..", "java.util..")
                .check(LIBRARY);
    }

    @Test
    void sessionsDependOnlyOnSessionPolicyProfilesProtocolAndJdkValues() {
        classes()
                .that()
                .resideInAPackage(SESSION)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        SESSION, PROFILE, PROTOCOL, "java.lang..", "java.math..", "java.time..", "java.util..")
                .check(LIBRARY);
    }

    @Test
    void protocolProfilesCodecsAndSessionsDoNotDependOnInfrastructurePackages() {
        noClasses()
                .that()
                .resideInAnyPackage(PROTOCOL, PROFILE, CODEC, SESSION)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "java.net..", "javax.net..", "java.nio.channels..", "java.nio.file..", "java.util.concurrent..")
                .check(LIBRARY);
    }

    @Test
    void requestsDependOnlyOnRequestsProtocolAndJdkConcurrency() {
        classes()
                .that()
                .resideInAPackage(REQUEST)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(REQUEST, PROTOCOL, "java.lang..", "java.math..", "java.time..", "java.util..")
                .check(LIBRARY);
    }

    @Test
    void transportPortsDependOnlyOnPortsAndJdkContracts() {
        classes()
                .that()
                .resideInAPackage(SPI)
                .should()
                .onlyDependOnClassesThat(resideInAnyPackage(SPI, "java.lang..", "java.time..", "java.util..")
                        .or(equivalentTo(java.io.Serial.class)))
                .check(LIBRARY);
    }

    @Test
    void transportsDependOnlyOnPortsCodecsProtocolAndJdkInfrastructure() {
        classes()
                .that()
                .resideInAPackage(TRANSPORT)
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        TRANSPORT,
                        SPI,
                        CODEC,
                        PROTOCOL,
                        "java.lang..",
                        "java.math..",
                        "java.io..",
                        "java.net..",
                        "java.nio..",
                        "java.time..",
                        "java.util..")
                .check(LIBRARY);
    }

    @Test
    void endpointsDependOnlyOnImplementedLayersAndJdkFacilities() {
        classes()
                .that()
                .resideInAPackage(ENDPOINT)
                .should()
                .onlyDependOnClassesThat(resideInAnyPackage(
                                ENDPOINT,
                                TRANSPORT,
                                SPI,
                                REQUEST,
                                SESSION,
                                CODEC,
                                PROFILE,
                                PROTOCOL,
                                "java.lang..",
                                "java.math..",
                                "java.net..",
                                "java.nio",
                                "java.time..",
                                "java.util..")
                        .or(equivalentTo(java.io.IOException.class))
                        .or(equivalentTo(java.io.Serial.class)))
                .check(LIBRARY);
    }

    @Test
    void connectionCoordinatorUsesPortsAndNetworkMetadata() {
        classes()
                .that()
                .haveNameMatching("kg[.]aidarbek[.]smpp[.]endpoint[.]EndpointConnection([$].*)?")
                .should()
                .onlyDependOnClassesThat(resideInAnyPackage(
                                ENDPOINT,
                                SPI,
                                REQUEST,
                                SESSION,
                                CODEC,
                                PROFILE,
                                PROTOCOL,
                                "java.lang..",
                                "java.math..",
                                "java.nio",
                                "java.time..",
                                "java.util..")
                        .or(equivalentTo(java.net.SocketAddress.class)))
                .check(LIBRARY);
    }

    @Test
    void libraryPackagesAreFreeOfCycles() {
        slices().matching("kg.aidarbek.smpp.(*)..").should().beFreeOfCycles().check(LIBRARY);
    }
}

package kg.aidarbek.simulator;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import kg.aidarbek.smpp.request.RequestWindow;
import org.junit.jupiter.api.Test;

class SimulatorArchitectureTest {
    private static ArchRule boundary() {
        return classes()
                .should()
                .onlyDependOnClassesThat(new DescribedPredicate<JavaClass>("public simulator dependencies") {
                    @Override
                    public boolean test(JavaClass type) {
                        String name = type.getName();
                        if (!name.startsWith("kg.aidarbek.smpp.")) return true;
                        if (name.startsWith("kg.aidarbek.smpp.endpoint.")
                                || name.startsWith("kg.aidarbek.smpp.protocol.")
                                || name.startsWith("kg.aidarbek.smpp.message."))
                            return type.getModifiers().contains(JavaModifier.PUBLIC);
                        return Set.of(
                                        "kg.aidarbek.smpp.profile.SmppVersion",
                                        "kg.aidarbek.smpp.profile.ProtocolProfile",
                                        "kg.aidarbek.smpp.session.VersionNegotiation",
                                        "kg.aidarbek.smpp.session.SessionPermissions",
                                        "kg.aidarbek.smpp.session.SessionState",
                                        "kg.aidarbek.smpp.session.EndpointRole",
                                        "kg.aidarbek.smpp.codec.PduLimits",
                                        "kg.aidarbek.smpp.transport.TlsConfig",
                                        "kg.aidarbek.smpp.request.RequestHandle",
                                        "kg.aidarbek.smpp.request.RequestOptions",
                                        "kg.aidarbek.smpp.request.RequestOutcome",
                                        "kg.aidarbek.smpp.request.RequestFailure",
                                        "kg.aidarbek.smpp.request.RequestFailure$Reason",
                                        "kg.aidarbek.smpp.request.PeerNackException",
                                        "kg.aidarbek.smpp.request.TransmissionCertainty")
                                .contains(name);
                    }
                });
    }

    @Test
    void productionConsumesPublicLibraryCapabilitiesAndToolOnlyMeasurement() {
        boundary()
                .check(new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages("kg.aidarbek.simulator"));
    }

    @Test
    void ruleDetectsASimulatorTryingToOwnASecondProtocolRequestWindow() {
        var result = boundary().evaluate(new ClassFileImporter().importClasses(ForbiddenRequestEngine.class));
        assertTrue(result.hasViolation());
        assertTrue(result.getFailureReport().toString().contains("RequestWindow"));
    }

    static final class ForbiddenRequestEngine {
        RequestWindow window;
    }
}

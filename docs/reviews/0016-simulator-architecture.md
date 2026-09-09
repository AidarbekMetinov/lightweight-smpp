# Step 17 simulator TLS boundary review

The simulator may construct the public TlsConfig value as part of its explicit
endpoint composition. That exact type is added to the existing dependency
allow-list; transport adapters and internal request engines remain excluded.
JDK key/trust-store handling stays in simulator configuration rather than the
library's PDU or session-state types.

## Actual TDD

In `/tmp/lightweight-smpp-step17`, the lifecycle adapter was already implemented
and its behavior tests were green. Running
`./gradlew :simulator:test --tests kg.aidarbek.simulator.SimulatorArchitectureTest --console=plain`
produced one failure in two cases
(`/tmp/step17-47-simulator-tls-boundary-red.log`). The real production dependency
rule rejected exactly the adapter's calls to TlsConfig.client and TlsConfig.server.
The existing forbidden RequestWindow probe continued to pass. This is a relevant
architecture failure against the real adapter, not a missing dependency or
compilation failure.

The root integration adds precisely `kg.aidarbek.smpp.transport.TlsConfig`,
expands the existing assertion import explicitly, and formats the file separately
(`/tmp/lightweight-smpp-step17-simulator-architecture-format.log`). The final
inventory confirms all three identities below; all share the whole-file SHA-256.
The same two architecture cases executed green in the lifecycle worktree
(`/tmp/step17-51-simulator-tls-boundary-green.log`). Root integration then passed
all 738 library and architecture cases and 66 simulator cases, plus both Javadoc
tasks (`/tmp/lightweight-smpp-step17-integration-behavior.log`). The complete
447-identity inventory includes all three identities below. The final complete
build and archive audit are recorded in the lifecycle owner report.

## Whole-type reviews

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest
sha256: 9f3a76e37aa68206416d2300cb48ccbc93b5976370162f42452fbde07629e907
responsibility: Verifies simulator production dependencies stay within the selected public library API and separately checks detection of an internal request-engine dependency.
consumers: Jupiter applies the rule to actual simulator bytecode; the negative case imports the dedicated forbidden fixture.
S: pass | The complete class owns dependency-boundary evidence, not workload configuration, TLS setup or request correlation.
O: pass | The explicit TlsConfig identity extends the supported public composition API without admitting its package generally or changing existing public helper/endpoint permissions.
L: pass | The same rule still selects real production classes, excludes tests and rejects RequestWindow. TLS construction no longer fails solely because a public configuration value lives in the transport package; its implementation and behavior are reviewed separately.
I: pass | Production classes need no architecture annotation or marker interface; the test uses only metadata and a narrow predicate.
D: pass | Depends on ArchUnit metadata and the prohibited type identity for its negative test; it never constructs or drives a request engine.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest#boundary/<anonymous>@20:109
sha256: 9f3a76e37aa68206416d2300cb48ccbc93b5976370162f42452fbde07629e907
responsibility: Classifies one dependency as public simulator API or explicitly permitted supporting type.
consumers: The simulator's production dependency rule invokes this DescribedPredicate for every referenced class.
S: pass | The predicate owns only dependency classification from immutable names and visibility; it does not inspect runtime sessions or load configuration.
O: pass | The exact allow-list accepts the new public TLS policy without opening transport implementation classes, while focused public endpoint/protocol/helper families retain their visibility requirement.
L: pass | Implements DescribedPredicate.test with a deterministic boolean result and stable description; non-library tooling dependencies retain the existing rule's treatment. The real TLS rejection and retained negative request-engine probe verify the changed contract.
I: pass | Implements only the required test method and imposes no callbacks or methods on inspected production types.
D: pass | Depends only on class metadata and constant type names; no concrete runtime collaborator or ownership is introduced.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest.ForbiddenRequestEngine
sha256: 9f3a76e37aa68206416d2300cb48ccbc93b5976370162f42452fbde07629e907
responsibility: Supplies a deliberately forbidden RequestWindow field for the simulator dependency negative probe.
consumers: The enclosing test imports its bytecode and requires a boundary violation naming RequestWindow.
S: pass | The single field exists only to expose the prohibited dependency; no engine, socket or resource is allocated.
O: pass | This explicit invalid input continues to guard request ownership independently of new public lifecycle configuration.
L: pass | The static fixture implements no production resource interface or behavior and is excluded from the production import and distribution.
I: pass | One metadata-bearing field is enough; no broad fake endpoint or unsupported-operation methods are added.
D: pass | The concrete internal dependency is deliberately rejected test input, never a permitted simulator implementation dependency.
findings: none
```

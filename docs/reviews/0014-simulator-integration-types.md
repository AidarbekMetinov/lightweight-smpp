# Review: simulator message-helper integration types

The source hashes below were recorded after the separate final formatting run.
The subprocess test owns both launched processes and attempts both cleanups even
when the first cleanup throws. Its regression uses two real, finite child JVMs,
explicit readiness markers, and independent bounded cleanup; it does not replace
the `Process` contract with a permissive test double.

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/HelperSimulatorTest.java
type: kg.aidarbek.simulator.HelperSimulatorTest
sha256: fdc4a9c4190e16f9da5b56163329c5135b701d79cd12a656306dc6622aa0ecb6
responsibility: Verifies the simulator's explicit message-helper scenarios and their process ownership through the actual command-line entry point.
consumers: JUnit runs profile-paired text and receipt scenarios, a two-connection SAR scenario, invalid-selection preflight cases, and interrupted cleanup of two real child JVMs.
S: pass | Every test checks the helper-to-simulator integration contract; private process, readiness, and report helpers exist solely to launch, observe, and clean up those finite scenarios.
O: pass | The shared pair helper accepts profile, content, payload, operation, and connection count, so additional supported scenario combinations extend the case tables without changing launch, report, or ownership behavior.
L: pass | JUnit lifecycle and temporary-directory ownership are preserved; actual Process instances retain their native interruption and termination contracts, bounded waits observe physical exit, nested finally attempts both owners after a cleanup failure, and the regression clears its deliberate interrupt before independent fallback cleanup.
I: pass | JUnit receives focused test methods; simulator children receive only command-line arguments and explicit credentials, while the waiting fixture receives one readiness path and requires no simulator or application callbacks.
D: pass | This integration boundary intentionally composes the public simulator launcher with JDK process and file APIs; endpoint and histogram class references locate their runtime artifacts, while protocol and transport implementation internals remain absent from the test.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/HelperSimulatorTest.java
type: kg.aidarbek.simulator.HelperSimulatorTest.WaitingChild
sha256: fdc4a9c4190e16f9da5b56163329c5135b701d79cd12a656306dc6622aa0ecb6
responsibility: Supplies a finite, externally observable child JVM whose physical termination exercises the cleanup owner's real Process contract.
consumers: The interrupted-cleanup regression launches two instances with distinct temporary readiness paths and then forcibly stops and joins both process handles.
S: pass | The fixture only writes its readiness marker and waits for at most thirty seconds; it does not perform message simulation, socket work, or parent-process assertions.
O: pass | Its only required variation is the injected readiness path; a fixed finite lifetime keeps the lifecycle fixture small without adding an unused process-extension framework.
L: pass | The final class has no custom supertype or mutable instance contract; its public static main follows the Java launcher signature, readiness follows successful startup, checked startup or interruption failures terminate the real child normally through the JVM, and the finite wait bounds an abandoned fixture.
I: pass | The launcher needs only main and one path argument; the private constructor prevents meaningless fixture instantiation and no unrelated callback or transport capability is exposed.
D: pass | The fixture depends only on JDK paths, files, and bounded sleeping; it has no dependency on endpoint internals, simulator decisions, or test-library execution inside the child JVM.
findings: none
```

## Cleanup regression evidence

In the isolated Step 15 integration worktree, the wrapper's focused task selection
`:simulator:test --tests 'kg.aidarbek.simulator.HelperSimulatorTest.interruptedFirstCleanupStillStopsBothRealChildren'`
compiled and executed the actual red assertion in
`/tmp/step15-integration-11-child-cleanup-red.log`. Two child JVMs signalled
readiness. Interrupting the owner made its first bounded cleanup throw; the old
sequential cleanup left the second child alive. The assertion explicitly records
`interrupted: true`; independent fallback cleanup stopped both children.

The minimal correction wraps the first stop in try/finally so the second stop is
always attempted. Selecting the complete `kg.aidarbek.simulator.HelperSimulatorTest`
class freshly passed all five cases in `12-child-cleanup-green`. A separate
`spotlessApply` run (`13-cleanup-format`) preceded final `:simulator:test
solidReviewInventory` (`14-cleanup-final`), which freshly passed all 50 simulator
cases in that prerequisite worktree and generated the final source inventory.
The complete main-checkout Step 15 suite later passed 57 simulator cases, including
Step 14 common-operation scenarios, as recorded in the integration report. No
capacity or latency measurement is inferred from these functional tests.

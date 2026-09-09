package kg.aidarbek.smpp.codec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kg.aidarbek.smpp.profile.CommonTlvRules;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.profile.TlvRules;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;

/** Immutable common-operation optional-parameter interpretation; raw unsupported input remains owned. */
final class CommonTlvSupport {
    private final TypedTlvRegistry registry;
    private final Map<Long, TlvRules> rules;

    CommonTlvSupport() {
        this(List.of());
    }

    CommonTlvSupport(List<CommonTlvExtension> extensions) {
        List<CommonTlvExtension> copied = List.copyOf(extensions);
        Set<String> extensionKeys = new HashSet<>();
        for (CommonTlvExtension extension : copied) {
            if (!extensionKeys.add(extension.version() + ":" + extension.commandId() + ":" + extension.tag()))
                throw new IllegalArgumentException("Duplicate common TLV extension context");
        }
        List<TypedTlvRegistry.Registration<?>> registrations = new ArrayList<>();
        Map<Long, TlvRules> policies = new HashMap<>();
        for (SmppVersion version : SmppVersion.values())
            for (long command :
                    new long[] {3, 7, 8, 0x21, 0x0b, 0x102, 0x80000003L, 0x80000007L, 0x80000008L, 0x80000021L}) {
                Set<Integer> permitted = new HashSet<>(CommonTlvRules.permittedTags(version, command));
                Set<Integer> repeatable = new HashSet<>(Set.of(0x0381, 0x0302, 0x0303));
                repeatable.retainAll(permitted);
                for (int tag : permitted)
                    registrations.add(new TypedTlvRegistry.Registration<>(
                            version, command, tag, new MessageTlvValueCodec(version, tag)));
                for (CommonTlvExtension extension : copied) {
                    if (extension.version() == version && extension.commandId() == command) {
                        permitted.add(extension.tag());
                        if (extension.repeatable()) repeatable.add(extension.tag());
                        registrations.add(new TypedTlvRegistry.Registration<>(
                                version, command, extension.tag(), extension.codec()));
                    }
                }
                policies.put(key(version, command), new TlvRules(permitted, Set.of(), repeatable));
            }
        registry = new TypedTlvRegistry(registrations);
        rules = Map.copyOf(policies);
    }

    TypedTlvRegistry registry() {
        return registry;
    }

    void validate(
            long commandId,
            SmppVersion version,
            OptionalParameters parameters,
            boolean outgoing,
            int esmClass,
            int shortLength) {
        TlvRules policy = rules.get(key(version, commandId));
        if (outgoing) policy.validateOutgoing(parameters);
        else policy.validateIncoming(parameters);
        List<Tlv> interpreted = new ArrayList<>();
        for (Tlv parameter : parameters.entries()) {
            boolean supported = registry.decode(version, commandId, parameter, OctetString.class)
                    .isPresent();
            if (outgoing && !supported)
                throw new IllegalArgumentException("Unsupported outgoing common-operation TLV value");
            if (supported) interpreted.add(parameter);
            if (supported && parameter.tag() == 0x0424 && shortLength > 0 && (outgoing || version == SmppVersion.V3_4))
                throw new IllegalArgumentException("short_message and message_payload cannot both carry outgoing data");
        }
        if (commandId == 0x21)
            MessageTlvSupport.companions(parameters, new OptionalParameters(interpreted), outgoing, esmClass);
    }

    private static long key(SmppVersion version, long commandId) {
        return ((long) version.ordinal() << 32) | commandId;
    }
}

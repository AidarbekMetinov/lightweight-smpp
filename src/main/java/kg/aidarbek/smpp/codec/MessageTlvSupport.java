package kg.aidarbek.smpp.codec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.MessageTlvRules;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.profile.TlvRules;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;

/** Immutable composition of message occurrence, value and companion validation. */
final class MessageTlvSupport {
    private final Map<Long, TlvRules> rules;
    private final TypedTlvRegistry registry;

    MessageTlvSupport(MessageDirection dataDirection, List<MessageTlvExtension> extensions) {
        Objects.requireNonNull(dataDirection, "dataDirection");
        List<MessageTlvExtension> copied = List.copyOf(extensions);
        Set<String> extensionKeys = new HashSet<>();
        for (MessageTlvExtension extension : copied) {
            String key = extension.version() + ":" + extension.commandId() + ":" + extension.direction() + ":"
                    + extension.tag();
            if (!extensionKeys.add(key)) throw new IllegalArgumentException("Duplicate message TLV extension context");
        }
        Map<Long, TlvRules> policies = new HashMap<>();
        List<TypedTlvRegistry.Registration<?>> registrations = new ArrayList<>();
        for (SmppVersion version : SmppVersion.values()) {
            Map<Integer, MessageTlvValueCodec> values = new HashMap<>();
            for (long commandId : new long[] {4, 5, 0x103, 0x80000004L, 0x80000005L, 0x80000103L}) {
                Set<Integer> permitted =
                        new HashSet<>(MessageTlvRules.permittedTags(version, commandId, dataDirection));
                Set<Integer> repeatable = new HashSet<>(Set.of(0x0381, 0x0302, 0x0303));
                repeatable.retainAll(permitted);
                for (int tag : permitted)
                    registrations.add(new TypedTlvRegistry.Registration<>(
                            version,
                            commandId,
                            tag,
                            values.computeIfAbsent(tag, t -> new MessageTlvValueCodec(version, t))));
                MessageDirection direction = direction(commandId, dataDirection);
                for (MessageTlvExtension extension : copied) {
                    if (extension.version() == version
                            && extension.commandId() == commandId
                            && extension.direction() == direction) {
                        permitted.add(extension.tag());
                        if (extension.repeatable()) repeatable.add(extension.tag());
                        registrations.add(new TypedTlvRegistry.Registration<>(
                                version, commandId, extension.tag(), extension.codec()));
                    }
                }
                policies.put(key(version, commandId), new TlvRules(permitted, Set.of(), repeatable));
            }
        }
        rules = Map.copyOf(policies);
        registry = new TypedTlvRegistry(registrations);
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
        for (Tlv entry : parameters.entries()) {
            Optional<OctetString> value = registry.decode(version, commandId, entry, OctetString.class);
            if (outgoing && value.isEmpty())
                throw new IllegalArgumentException("Unsupported outgoing message TLV value");
            if (value.isPresent()) interpreted.add(entry);
        }
        OptionalParameters supported = new OptionalParameters(interpreted);
        if ((commandId & 0x80000000L) == 0) {
            if (count(supported, 0x0424) > 0 && shortLength > 0 && (outgoing || version == SmppVersion.V3_4))
                throw new IllegalArgumentException("short_message and message_payload cannot both carry outgoing data");
            companions(parameters, supported, outgoing, esmClass);
        }
    }

    static void companions(OptionalParameters raw, OptionalParameters supported, boolean outgoing, int esmClass) {
        boolean reference = count(supported, 0x020c) > 0,
                total = count(supported, 0x020e) > 0,
                sequence = count(supported, 0x020f) > 0;
        boolean anySar = reference || total || sequence, completeSar = reference && total && sequence;
        if (outgoing && anySar && !completeSar)
            throw new IllegalArgumentException("Outgoing SAR requires all three companion parameters");
        if (completeSar && octet(supported, 0x020f) > octet(supported, 0x020e))
            throw new IllegalArgumentException("SAR sequence exceeds total segments");
        if ((esmClass & 0x40) != 0 && (completeSar || count(supported, 0x020a) > 0 || count(supported, 0x020b) > 0))
            throw new IllegalArgumentException("UDHI conflicts with supported SAR or port parameters");
        boolean allCallbacksSupported = true;
        for (int tag : new int[] {0x0381, 0x0302, 0x0303})
            allCallbacksSupported &= count(raw, tag) == count(supported, tag);
        if (outgoing || allCallbacksSupported) {
            int callbacks = count(supported, 0x0381);
            for (int tag : new int[] {0x0302, 0x0303}) {
                int companions = count(supported, tag);
                if (companions > 0 && companions != callbacks)
                    throw new IllegalArgumentException("Callback companion count must match callback numbers");
            }
        }
        requireCompanion(supported, 0x060d, 0x060f);
        requireCompanion(supported, 0x060e, 0x0610);
        if (octet(supported, 0x0611) == 2) {
            requireCompanion(supported, 0x0611, 0x0612);
            requireCompanion(supported, 0x0611, 0x0613);
        }
    }

    private static void requireCompanion(OptionalParameters parameters, int tag, int companion) {
        if (count(parameters, tag) > 0 && count(parameters, companion) == 0)
            throw new IllegalArgumentException("Required message TLV companion is absent");
    }

    static int count(OptionalParameters parameters, int tag) {
        List<Tlv> entries = parameters.entries();
        int count = 0;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).tag() == tag) count++;
        }
        return count;
    }

    static int octet(OptionalParameters parameters, int tag) {
        List<Tlv> entries = parameters.entries();
        for (int index = 0; index < entries.size(); index++) {
            Tlv entry = entries.get(index);
            if (entry.tag() == tag) return entry.value()[0] & 255;
        }
        return -1;
    }

    static MessageDirection direction(long commandId, MessageDirection dataDirection) {
        long request = commandId & 0x7fff_ffffL;
        return request == 4 ? MessageDirection.SUBMISSION : request == 5 ? MessageDirection.DELIVERY : dataDirection;
    }

    private static long key(SmppVersion version, long commandId) {
        return ((long) version.ordinal() << 32) | commandId;
    }
}

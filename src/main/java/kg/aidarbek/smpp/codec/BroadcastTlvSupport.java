package kg.aidarbek.smpp.codec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kg.aidarbek.smpp.profile.BroadcastTlvRules;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.profile.TlvRules;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;

/** Immutable broadcast TLV occurrence, structural and companion interpretation. */
final class BroadcastTlvSupport {
    private final Map<Long, TlvRules> rules;
    private final TlvRules immediate;
    private final Map<Long, TlvRules> extensionRules;
    private final TypedTlvRegistry registry;

    BroadcastTlvSupport() {
        this(List.of());
    }

    BroadcastTlvSupport(List<BroadcastTlvExtension> extensions) {
        List<BroadcastTlvExtension> copied = List.copyOf(extensions);
        Set<String> seen = new HashSet<>();
        for (BroadcastTlvExtension extension : copied)
            if (!seen.add(extension.commandId() + ":" + extension.tag()))
                throw new IllegalArgumentException("Duplicate broadcast extension context");
        Map<Long, TlvRules> additionalRules = new HashMap<>();
        Map<Long, TlvRules> policies = new HashMap<>();
        Map<Integer, TlvValueCodec<OctetString>> values = new HashMap<>();
        List<TypedTlvRegistry.Registration<?>> registrations = new ArrayList<>();
        for (long id : new long[] {0x111, 0x112, 0x113, 0x80000111L, 0x80000112L, 0x80000113L}) {
            policies.put(id, BroadcastTlvRules.forCommand(SmppVersion.V5_0, id, 0));
            Set<Integer> additional = new HashSet<>(), repeated = new HashSet<>();
            for (BroadcastTlvExtension extension : copied)
                if (extension.commandId() == id) {
                    additional.add(extension.tag());
                    if (extension.repeatable()) repeated.add(extension.tag());
                    registrations.add(new TypedTlvRegistry.Registration<>(
                            SmppVersion.V5_0, id, extension.tag(), extension.codec()));
                }
            additionalRules.put(id, new TlvRules(additional, Set.of(), repeated));
            for (int tag : BroadcastTlvRules.permittedTags(SmppVersion.V5_0, id, 0)) {
                TlvValueCodec<OctetString> value = values.computeIfAbsent(
                        tag,
                        t -> (t >= 0x0600 && t <= 0x060a) || t == 0x0427
                                ? new BroadcastTlvValueCodec(t)
                                : new MessageTlvValueCodec(SmppVersion.V5_0, t));
                registrations.add(new TypedTlvRegistry.Registration<>(SmppVersion.V5_0, id, tag, value));
            }
        }
        rules = Map.copyOf(policies);
        extensionRules = Map.copyOf(additionalRules);
        immediate = BroadcastTlvRules.forCommand(SmppVersion.V5_0, 0x111, 1);
        registry = new TypedTlvRegistry(registrations);
    }

    TypedTlvRegistry registry() {
        return registry;
    }

    void validate(Command command, boolean outgoing) {
        long id = command.commandId();
        OptionalParameters raw = BroadcastCommandCodec.parameters(command);
        boolean urgent = command instanceof BroadcastSm broadcast && broadcast.priorityFlag() == 1;
        TlvRules policy = urgent ? immediate : rules.get(id);
        policy.validateIncoming(raw);
        extensionRules.get(id).validateIncoming(raw);
        List<Tlv> recognized = new ArrayList<>();
        for (Tlv entry : raw.entries()) {
            if (urgent && entry.tag() == 0x0604) {
                if (outgoing) throw new IllegalArgumentException("Immediate broadcast must omit repetition TLV");
                continue;
            }
            boolean supported = registry.decode(SmppVersion.V5_0, id, entry, OctetString.class)
                    .isPresent();
            if (outgoing && !supported) throw new IllegalArgumentException("Unsupported outgoing broadcast TLV value");
            if (supported) recognized.add(entry);
        }
        OptionalParameters supported = new OptionalParameters(recognized);
        if (command instanceof BroadcastSm broadcast) {
            if (outgoing
                    && broadcast.replaceIfPresentFlag() != 1
                    && !broadcast.messageId().isEmpty())
                throw new IllegalArgumentException("Outgoing MC message ID is only used for broadcast replacement");
            if (outgoing || broadcast.replaceIfPresentFlag() == 1)
                identity(broadcast.messageId(), supported, broadcast.replaceIfPresentFlag() == 1);
            MessageTlvSupport.companions(raw, supported, outgoing, 0);
            int network = MessageTlvSupport.octet(supported, 0x0601);
            if (outgoing
                    && ((network == 1 && broadcast.priorityFlag() == 3)
                            || ((network == 2 || network == 3) && broadcast.priorityFlag() > 3)))
                throw new IllegalArgumentException("Broadcast priority is reserved for selected network");
        } else if (command instanceof QueryBroadcastSm query) identity(query.messageId(), supported, true);
        else if (command instanceof CancelBroadcastSm cancel) identity(cancel.messageId(), supported, false);
        else if (command instanceof BroadcastSmResponse) {
            equalCompanions(raw, supported, 0x0606, 0x0607, false, outgoing);
        } else if (command instanceof QueryBroadcastSmResponse) {
            equalCompanions(raw, supported, 0x0606, 0x0608, true, outgoing);
            int state = MessageTlvSupport.octet(supported, 0x0427);
            if ((state == 0 || state == 1) && MessageTlvSupport.count(supported, 0x0609) > 0)
                throw new IllegalArgumentException("Active broadcast cannot have a broadcast end time");
        }
    }

    private static void identity(String messageId, OptionalParameters supported, boolean required) {
        boolean reference = MessageTlvSupport.count(supported, 0x0204) > 0;
        if ((!messageId.isEmpty() && reference) || (required && messageId.isEmpty() && !reference))
            throw new IllegalArgumentException("Broadcast identity requires exclusive MC ID or ESME reference");
    }

    private static void equalCompanions(
            OptionalParameters raw,
            OptionalParameters supported,
            int tag,
            int companion,
            boolean required,
            boolean outgoing) {
        int count = MessageTlvSupport.count(supported, tag), pairs = MessageTlvSupport.count(supported, companion);
        if (!outgoing
                && (count != MessageTlvSupport.count(raw, tag) || pairs != MessageTlvSupport.count(raw, companion)))
            return;
        if ((required || pairs > 0) && count != pairs)
            throw new IllegalArgumentException("Broadcast per-area result count must match area identifiers");
    }
}

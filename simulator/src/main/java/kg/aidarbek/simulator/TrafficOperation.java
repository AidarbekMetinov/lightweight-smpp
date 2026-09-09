package kg.aidarbek.simulator;

import kg.aidarbek.smpp.endpoint.BoundSession;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.request.RequestHandle;

/** One library-backed request operation; the library remains the sole correlation/deadline owner. */
@FunctionalInterface
interface TrafficOperation {
    RequestHandle<? extends Command> send(BoundSession session, TrafficContent content, long index);
}

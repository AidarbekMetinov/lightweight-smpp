package kg.aidarbek.smpp.endpoint;

import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.session.SendRequirements;

/** Focused MC availability-notification capability; every send rechecks role, lifecycle and fields. */
public final class AlertSender {
    private final EndpointConnection connection;

    AlertSender(EndpointConnection connection) {
        this.connection = connection;
    }
    /** Sends using the endpoint's default total write deadline.
     * @param command immutable one-way notification
     * @return local write observation and queued cancellation */
    public NotificationSend send(AlertNotification command) {
        return send(command, null, SendRequirements.COMMON);
    }
    /** Sends with an explicit total invocation deadline.
     * @param command immutable notification
     * @param options deadline configuration
     * @return local write observation */
    public NotificationSend send(AlertNotification command, RequestOptions options) {
        return send(command, options, SendRequirements.COMMON);
    }
    /** Adds explicit requirements that cannot weaken actual-field/TLV checks.
     * @param command immutable notification
     * @param options deadline configuration, or null for endpoint default
     * @param requirements additional semantic requirements
     * @return local write observation, without a pending peer response */
    public NotificationSend send(AlertNotification command, RequestOptions options, SendRequirements requirements) {
        return connection.sendAlert(command, options, requirements);
    }
}

/**
 *
 * @author Thant Sin
 */
package SmartForestMonitoring;

import com.forest.alert.SensorReading;
import com.forest.alert.SensorReport;
import com.forest.alert.AlertEvent;
import com.forest.alert.AlertCommand;
import com.forest.alert.AlertServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AlertServer extends AlertServiceGrpc.AlertServiceImplBase {

    private static final int PORT = 50053;
    private static final String SERVICE_NAME = "AlertService";
    private static JmDNS jmdns;

    // ========== CLIENT STREAMING RPC ==========
    @Override
    public StreamObserver<SensorReading> uploadSensorReadings(
            StreamObserver<SensorReport> responseObserver) {

        System.out.println("AlertServer: UploadSensorReadings stream started");

        return new StreamObserver<SensorReading>() {
            float totalSmoke = 0, maxSmoke = 0;
            float totalTemp = 0, maxTemp = 0;
            int count = 0;

            @Override
            public void onNext(SensorReading reading) {
                // Input validation — reject negative sensor values
                if (reading.getSmokeReading() < 0 || reading.getTemperatureReading() < 0) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Sensor readings cannot be negative")
                            .asRuntimeException());
                    return;
                }

                System.out.println("AlertServer: Received reading - Smoke: "
                        + reading.getSmokeReading()
                        + " Temp: " + reading.getTemperatureReading());

                totalSmoke += reading.getSmokeReading();
                totalTemp  += reading.getTemperatureReading();
                if (reading.getSmokeReading() > maxSmoke)
                    maxSmoke = reading.getSmokeReading();
                if (reading.getTemperatureReading() > maxTemp)
                    maxTemp = reading.getTemperatureReading();
                count++;
            }

            @Override
            public void onError(Throwable t) {
                // Log with gRPC status code if available
                if (t instanceof io.grpc.StatusRuntimeException) {
                    io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) t;
                    System.err.println("AlertServer: Stream error ["
                            + sre.getStatus().getCode() + "]: "
                            + sre.getStatus().getDescription());
                } else {
                    System.err.println("AlertServer: Error in sensor upload: "
                            + t.getMessage());
                }
            }

            @Override
            public void onCompleted() {
                // Guard against empty stream
                if (count == 0) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("No sensor readings were uploaded")
                            .asRuntimeException());
                    return;
                }

                float avgSmoke = totalSmoke / count;
                float avgTemp  = totalTemp  / count;
                boolean alertFlag = avgSmoke > 5 || avgTemp > 38;

                SensorReport report = SensorReport.newBuilder()
                        .setAverageSmokeLevel(avgSmoke)
                        .setMaxSmokeLevel(maxSmoke)
                        .setAverageTemperature(avgTemp)
                        .setMaxTemperature(maxTemp)
                        .setAlertFlag(alertFlag)
                        .setSummary(alertFlag
                                ? "ALERT: Dangerous levels detected!"
                                : "All readings within safe limits.")
                        .build();

                responseObserver.onNext(report);
                responseObserver.onCompleted();
                System.out.println("AlertServer: Upload complete. Alert flag: " + alertFlag);
            }
        };
    }

    // ========== BIDIRECTIONAL STREAMING RPC ==========
    @Override
    public StreamObserver<AlertEvent> monitorAlerts(
            StreamObserver<AlertCommand> responseObserver) {

        System.out.println("AlertServer: MonitorAlerts bidirectional stream started");

        return new StreamObserver<AlertEvent>() {
            @Override
            public void onNext(AlertEvent event) {
                System.out.println("AlertServer: Received alert event - "
                        + event.getEventType() + " in zone " + event.getZoneId());

                // Input validation — reject empty zone
                if (event.getZoneId() == null || event.getZoneId().isEmpty()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Zone ID cannot be empty in alert event")
                            .asRuntimeException());
                    return;
                }

                // Determine command based on severity level
                String command;
                String message;
                if (event.getSeverity() >= 8) {
                    command = "EVACUATE";
                    message = "Immediate evacuation required in zone " + event.getZoneId();
                } else if (event.getSeverity() >= 6) {
                    command = "DISPATCH_TEAM";
                    message = "Fire team dispatched to zone " + event.getZoneId();
                } else if (event.getSeverity() >= 3) {
                    command = "WARNING";
                    message = "Warning issued for zone " + event.getZoneId()
                            + ". Monitor closely.";
                } else {
                    command = "CLEAR_ALERT";
                    message = "Zone " + event.getZoneId() + " is clear. No action needed.";
                }

                AlertCommand alertCommand = AlertCommand.newBuilder()
                        .setZoneId(event.getZoneId())
                        .setCommand(command)
                        .setMessage(message)
                        .setTimestamp(LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .build();

                responseObserver.onNext(alertCommand);
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof io.grpc.StatusRuntimeException) {
                    io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) t;
                    System.err.println("AlertServer: BiDi stream error ["
                            + sre.getStatus().getCode() + "]: "
                            + sre.getStatus().getDescription());
                } else {
                    System.err.println("AlertServer: Error in monitor alerts: "
                            + t.getMessage());
                }
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
                System.out.println("AlertServer: MonitorAlerts stream completed");
            }
        };
    }

    // ========== REGISTER WITH NAMING SERVICE ==========
    public static void registerWithNamingService() throws IOException {
        jmdns = JmDNS.create(InetAddress.getLocalHost());
        ServiceInfo serviceInfo = ServiceInfo.create(
                NamingServer.SERVICE_TYPE,
                SERVICE_NAME,
                PORT,
                "Emergency Alert Service"
        );
        jmdns.registerService(serviceInfo);
        System.out.println("AlertServer: Registered with Naming Service as '"
                + SERVICE_NAME + "'");
    }

    // ========== MAIN ==========
    public static void main(String[] args) throws IOException, InterruptedException {
        registerWithNamingService();

        Server server = ServerBuilder.forPort(PORT)
                .addService(new AlertServer())
                .build()
                .start();

        System.out.println("=== Alert Service started on port " + PORT + " ===");
        server.awaitTermination();
    }
}
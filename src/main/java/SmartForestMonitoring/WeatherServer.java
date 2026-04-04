/**
 *
 * @author Thant Sin
 */
package SmartForestMonitoring;

import com.forest.weather.WeatherRequest;
import com.forest.weather.WeatherResponse;
import com.forest.weather.WeatherStreamRequest;
import com.forest.weather.WeatherServiceGrpc;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class WeatherServer extends WeatherServiceGrpc.WeatherServiceImplBase {

    private static final int PORT = 50051;
    private static final String SERVICE_NAME = "WeatherService";
    private static JmDNS jmdns;

    // Token loaded from environment variable — never hardcoded
    private static final String VALID_TOKEN = System.getenv("FOREST_MONITOR_TOKEN") != null
            ? System.getenv("FOREST_MONITOR_TOKEN")
            : "";

    // ========== AUTH INTERCEPTOR ==========
    // Validates the auth token on every incoming call
    public static class AuthInterceptor implements ServerInterceptor {
        private static final Metadata.Key<String> AUTH_KEY =
                Metadata.Key.of("auth-token", Metadata.ASCII_STRING_MARSHALLER);

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {

            String token = headers.get(AUTH_KEY);
            if (token == null || !token.equals(VALID_TOKEN)) {
                // Error message never reveals the expected token value
                call.close(Status.UNAUTHENTICATED
                        .withDescription("Invalid or missing auth token."),
                        new Metadata());
                return new ServerCall.Listener<ReqT>() {};
            }
            System.out.println("WeatherServer: Authentication successful");
            return next.startCall(call, headers);
        }
    }

    // ========== UNARY RPC ==========
    @Override
    public void getWeatherStatus(WeatherRequest request,
            StreamObserver<WeatherResponse> responseObserver) {

        System.out.println("WeatherServer: GetWeatherStatus called for zone: "
                + request.getZoneId());

        // Input validation — zone ID must not be empty
        if (request.getZoneId() == null || request.getZoneId().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Zone ID cannot be empty")
                    .asRuntimeException());
            return;
        }

        // Check if request was cancelled by client
        if (Context.current().isCancelled()) {
            responseObserver.onError(Status.CANCELLED
                    .withDescription("Request was cancelled by the client")
                    .asRuntimeException());
            return;
        }

        try {
            Random rand = new Random();
            float temp = 20 + rand.nextFloat() * 20;
            String status = temp > 35 ? "WARNING" : "NORMAL";

            WeatherResponse response = WeatherResponse.newBuilder()
                    .setZoneId(request.getZoneId())
                    .setTemperature(temp)
                    .setWindSpeed(rand.nextFloat() * 60)
                    .setHumidity(30 + rand.nextFloat() * 60)
                    .setRainfall(rand.nextFloat() * 10)
                    .setTimestamp(LocalDateTime.now().format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .setStatus(status)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            System.out.println("WeatherServer: GetWeatherStatus completed for zone: "
                    + request.getZoneId());

        } catch (Exception e) {
            System.err.println("WeatherServer: Internal error: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    // ========== SERVER STREAMING RPC ==========
    @Override
    public void streamWeatherUpdates(WeatherStreamRequest request,
            StreamObserver<WeatherResponse> responseObserver) {

        System.out.println("WeatherServer: StreamWeatherUpdates called for zone: "
                + request.getZoneId() + " duration: " + request.getDuration());

        // Input validation
        if (request.getZoneId() == null || request.getZoneId().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Zone ID cannot be empty")
                    .asRuntimeException());
            return;
        }

        if (request.getDuration() <= 0) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Duration must be greater than 0")
                    .asRuntimeException());
            return;
        }

        if (request.getDuration() > 20) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Duration cannot exceed 20 updates")
                    .asRuntimeException());
            return;
        }

        try {
            Random rand = new Random();
            for (int i = 0; i < request.getDuration(); i++) {

                // Check for cancellation during stream
                if (Context.current().isCancelled()) {
                    System.out.println("WeatherServer: Stream cancelled by client at update " + i);
                    responseObserver.onError(Status.CANCELLED
                            .withDescription("Stream was cancelled by the client")
                            .asRuntimeException());
                    return;
                }

                float temp = 20 + rand.nextFloat() * 20;
                String status = temp > 35 ? "WARNING" : "NORMAL";

                WeatherResponse response = WeatherResponse.newBuilder()
                        .setZoneId(request.getZoneId())
                        .setTemperature(temp)
                        .setWindSpeed(rand.nextFloat() * 60)
                        .setHumidity(30 + rand.nextFloat() * 60)
                        .setRainfall(rand.nextFloat() * 10)
                        .setTimestamp(LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .setStatus(status)
                        .build();

                responseObserver.onNext(response);
                System.out.println("WeatherServer: Sent update " + (i + 1)
                        + "/" + request.getDuration());
                Thread.sleep(1000);
            }
            responseObserver.onCompleted();
            System.out.println("WeatherServer: StreamWeatherUpdates completed for zone: "
                    + request.getZoneId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            responseObserver.onError(Status.ABORTED
                    .withDescription("Stream was interrupted: " + e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            System.err.println("WeatherServer: Internal error: " + e.getMessage());
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal server error: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    // ========== REGISTER WITH NAMING SERVICE ==========
    public static void registerWithNamingService() throws IOException {
        jmdns = JmDNS.create(InetAddress.getLocalHost());
        ServiceInfo serviceInfo = ServiceInfo.create(
                NamingServer.SERVICE_TYPE,
                SERVICE_NAME,
                PORT,
                "Weather Monitoring Service"
        );
        jmdns.registerService(serviceInfo);
        System.out.println("WeatherServer: Registered with Naming Service as '"
                + SERVICE_NAME + "'");
    }

    // ========== MAIN ==========
    public static void main(String[] args) throws IOException, InterruptedException {
        // Fail fast if token environment variable is not set
        if (VALID_TOKEN.isEmpty()) {
            System.err.println("ERROR: FOREST_MONITOR_TOKEN environment variable is not set. Exiting.");
            System.exit(1);
        }

        registerWithNamingService();

        Server server = ServerBuilder.forPort(PORT)
                .addService(new WeatherServer())
                .intercept(new AuthInterceptor())
                .build()
                .start();

        System.out.println("=== Weather Service started on port " + PORT + " ===");

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("WeatherServer: Shutting down...");
            server.shutdown();
            if (jmdns != null) {
                try { jmdns.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }));

        server.awaitTermination();
    }
}
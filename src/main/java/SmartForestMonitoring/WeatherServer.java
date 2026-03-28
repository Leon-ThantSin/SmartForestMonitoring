/**
 *
 * @author Thant Sin
 */
package SmartForestMonitoring;

import com.forest.weather.WeatherRequest;
import com.forest.weather.WeatherResponse;
import com.forest.weather.WeatherStreamRequest;
import com.forest.weather.WeatherServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
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

    // ========== UNARY RPC ==========
    @Override
    public void getWeatherStatus(WeatherRequest request, StreamObserver<WeatherResponse> responseObserver) {
        System.out.println("WeatherServer: GetWeatherStatus called for zone: " + request.getZoneId());

        Random rand = new Random();
        WeatherResponse response = WeatherResponse.newBuilder()
                .setZoneId(request.getZoneId())
                .setTemperature(20 + rand.nextFloat() * 20)
                .setWindSpeed(rand.nextFloat() * 60)
                .setHumidity(30 + rand.nextFloat() * 60)
                .setRainfall(rand.nextFloat() * 10)
                .setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .setStatus("NORMAL")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // ========== SERVER STREAMING RPC ==========
    @Override
    public void streamWeatherUpdates(WeatherStreamRequest request, StreamObserver<WeatherResponse> responseObserver) {
        System.out.println("WeatherServer: StreamWeatherUpdates called for zone: " + request.getZoneId());

        Random rand = new Random();
        for (int i = 0; i < request.getDuration(); i++) {
            float temp = 20 + rand.nextFloat() * 20;
            String status = temp > 35 ? "WARNING" : "NORMAL";

            WeatherResponse response = WeatherResponse.newBuilder()
                    .setZoneId(request.getZoneId())
                    .setTemperature(temp)
                    .setWindSpeed(rand.nextFloat() * 60)
                    .setHumidity(30 + rand.nextFloat() * 60)
                    .setRainfall(rand.nextFloat() * 10)
                    .setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .setStatus(status)
                    .build();

            responseObserver.onNext(response);

            try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
        }
        responseObserver.onCompleted();
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
        System.out.println("WeatherServer: Registered with Naming Service as '" + SERVICE_NAME + "'");
    }

    // ========== MAIN ==========
    public static void main(String[] args) throws IOException, InterruptedException {
        registerWithNamingService();

        Server server = ServerBuilder.forPort(PORT)
                .addService(new WeatherServer())
                .build()
                .start();

        System.out.println("=== Weather Service started on port " + PORT + " ===");
        server.awaitTermination();
    }
}

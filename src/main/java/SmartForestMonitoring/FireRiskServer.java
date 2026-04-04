/**
 *
 * @author Thant Sin
 */
package SmartForestMonitoring;
 
import com.forest.firerisk.FireRiskRequest;
import com.forest.firerisk.FireRiskResponse;
import com.forest.firerisk.RiskLevel;
import com.forest.firerisk.FireRiskServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
 
public class FireRiskServer extends FireRiskServiceGrpc.FireRiskServiceImplBase {
 
    private static final int PORT = 50052;
    private static final String SERVICE_NAME = "FireRiskService";
    private static JmDNS jmdns;
 
    // ========== UNARY RPC ==========
    @Override
    public void assessFireRisk(FireRiskRequest request, StreamObserver<FireRiskResponse> responseObserver) {
        System.out.println("FireRiskServer: AssessFireRisk called for zone: " + request.getZoneId());
 
        // Read environmental data sent by the client
        float temperature = request.getTemperature();
        float windSpeed   = request.getWindSpeed();
        float humidity    = request.getHumidity();
        float smokeLevel  = request.getSmokeLevel();
 
        System.out.println("  Received data for [" + request.getZoneId() + "]"
                + " -> Temp: "     + String.format("%.1f", temperature) + "°C"
                + " | Wind: "      + String.format("%.1f", windSpeed)   + " km/h"
                + " | Humidity: "  + String.format("%.1f", humidity)    + "%"
                + " | Smoke: "     + String.format("%.1f", smokeLevel));
 
        // ========== RULE-BASED RISK CALCULATION ==========
        float score = 0;
 
        // Temperature scoring
        if (temperature > 35)      score += 30;
        else if (temperature > 25) score += 15;
        else                       score += 5;
 
        // Wind speed scoring
        if (windSpeed > 50)      score += 25;
        else if (windSpeed > 30) score += 15;
        else                     score += 5;
 
        // Humidity scoring (lower humidity = higher risk)
        if (humidity < 20)      score += 25;
        else if (humidity < 40) score += 15;
        else                    score += 5;
 
        // Smoke level scoring
        if (smokeLevel > 7)      score += 20;
        else if (smokeLevel > 4) score += 10;
        else                     score += 0;
 
        // ========== DETERMINE RISK LEVEL ==========
        RiskLevel level;
        String recommendation;
 
        if (score >= 80) {
            level = RiskLevel.EXTREME;
            recommendation = "EXTREME DANGER! Evacuate immediately! Deploy all emergency units!";
        } else if (score >= 60) {
            level = RiskLevel.HIGH;
            recommendation = "HIGH RISK! Dispatch fire teams to zone " + request.getZoneId() + " immediately!";
        } else if (score >= 40) {
            level = RiskLevel.MEDIUM;
            recommendation = "MEDIUM RISK. Monitor closely. Prepare response teams for zone " + request.getZoneId();
        } else {
            level = RiskLevel.LOW;
            recommendation = "LOW RISK. Situation normal in zone " + request.getZoneId() + ". Continue monitoring.";
        }
 
        System.out.println("  Risk Score: " + score + " -> " + level);
 
        // ========== BUILD AND SEND RESPONSE ==========
        FireRiskResponse response = FireRiskResponse.newBuilder()
                .setRiskLevel(level)
                .setRiskScore(score)
                .setRecommendation(recommendation)
                .setZoneId(request.getZoneId())
                .setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
 
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
 
    // ========== REGISTER WITH NAMING SERVICE ==========
    public static void registerWithNamingService() throws IOException {
        jmdns = JmDNS.create(InetAddress.getLocalHost());
        ServiceInfo serviceInfo = ServiceInfo.create(
                NamingServer.SERVICE_TYPE,
                SERVICE_NAME,
                PORT,
                "Fire Risk Assessment Service"
        );
        jmdns.registerService(serviceInfo);
        System.out.println("FireRiskServer: Registered with Naming Service as '" + SERVICE_NAME + "'");
    }
 
    // ========== MAIN ==========
    public static void main(String[] args) throws IOException, InterruptedException {
        registerWithNamingService();
 
        Server server = ServerBuilder.forPort(PORT)
                .addService(new FireRiskServer())
                .build()
                .start();
 
        System.out.println("=== Fire Risk Service started on port " + PORT + " ===");
        server.awaitTermination();
    }
}
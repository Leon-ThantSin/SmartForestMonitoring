/**
 *
 * @author Thant Sin
 */
package SmartForestMonitoring;
 
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;
 
public class NamingServer {
 
    // Service type for all forest monitoring services
    public static final String SERVICE_TYPE = "_forest._tcp.local.";
 
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("=== Smart Forest Monitoring - Naming Server ===");
        System.out.println("Starting JmDNS Naming Service...");
 
        // Create JmDNS instance on local network
        JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
 
        System.out.println("Naming Service is running on: " + InetAddress.getLocalHost().getHostAddress());
        System.out.println("Listening for service registrations on type: " + SERVICE_TYPE);
        System.out.println("Services can now register and discover each other.");
        System.out.println("Press CTRL+C to stop.");
 
        // Keep the naming server running
        Thread.currentThread().join();
 
        jmdns.close();
    }
}

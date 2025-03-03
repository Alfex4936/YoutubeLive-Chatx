package csw.youtube.chat.playwright;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

// GET http://localhost:8080/actuator/browser-pool
//@Component
//@Endpoint(id = "browser-pool") // Accessible at /actuator/browser-pool
//@RequiredArgsConstructor
//public class PlaywrightPoolMetrics {
//    private final PlaywrightBrowserPool browserPool;
//
//    @ReadOperation
//    public BrowserPoolStats browserPoolStats() {
//        return new BrowserPoolStats(
//                browserPool.getBrowserPool().getNumActive(),
//                browserPool.getBrowserPool().getNumIdle(),
//                browserPool.getMaxTotal()
//        );
//    }
//
//    record BrowserPoolStats(int active, int idle, int maxTotal) {
//    }
//}

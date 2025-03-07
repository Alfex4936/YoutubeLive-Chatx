package csw.youtube.chat.live.controller;

import csw.youtube.chat.live.model.ScraperState;
import csw.youtube.chat.live.service.YTChatScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller  // ✅ This makes it return an HTML page instead of JSON
@RequiredArgsConstructor
public class MonitorController {

    private final YTChatScraperService scraperService;

    @GetMapping("/scraper-monitor")
    public String getScraperMonitor(Model model,
                                    @RequestParam(value = "message", required = false) String msg) {

        Map<String, ScraperState> allStates = scraperService.getScraperStates();
        boolean isEmpty = allStates.isEmpty();

        model.addAttribute("scraperStates", allStates);
        model.addAttribute("scraperStatesEmpty", isEmpty);
        model.addAttribute("message", msg);

        return "scraper-monitor";
    }
}

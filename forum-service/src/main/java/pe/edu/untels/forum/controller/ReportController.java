package pe.edu.untels.forum.controller;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.edu.untels.forum.service.ReportService;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    public record Report(@NotNull @Positive Long postId, @Positive Long commentId,
                         @NotBlank @Size(max = 500) String reason) {}
    public record Resolution(@NotNull ReportService.Decision decision, @NotBlank @Size(max = 500) String reason) {}

    private final ReportService reports;
    public ReportController(ReportService reports) { this.reports = reports; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> create(@Valid @RequestBody Report report,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Map.of("id", reports.report(report.postId(), report.commentId(), report.reason(), authorization));
    }

    @GetMapping
    public List<ReportService.Pending> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return reports.pending(authorization);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable long id, @Valid @RequestBody Resolution resolution,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        reports.resolve(id, resolution.decision(), resolution.reason(), authorization);
    }
}

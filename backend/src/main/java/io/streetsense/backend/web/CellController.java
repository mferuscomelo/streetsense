package io.streetsense.backend.web;

import io.streetsense.backend.crowd.CellDetail;
import io.streetsense.backend.crowd.CellDetailService;
import io.streetsense.backend.crowd.CellSummary;
import io.streetsense.backend.crowd.CrowdService;
import io.streetsense.backend.domain.GridCell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The crowd layer's read side — what everyone's readings together say about a
 * block, and how much evidence stands behind it.
 *
 * <p>Every response carries {@code confidence} and {@code seededContributorCount}
 * so no client can render a conclusion without also being handed the means to
 * qualify it.
 */
@RestController
@RequestMapping("/api/v1/cells")
public class CellController {

    private static final Logger log = LoggerFactory.getLogger(CellController.class);

    private final CrowdService crowd;
    private final CellDetailService detailService;

    public CellController(CrowdService crowd, CellDetailService detailService) {
        this.crowd = crowd;
        this.detailService = detailService;
    }

    @GetMapping
    public List<CellView> cityView() {
        List<CellView> view = crowd.cityView().stream().map(CellView::of).toList();
        log.debug("City view returned {} cells", view.size());
        return view;
    }

    @GetMapping("/{latBucket}/{lonBucket}")
    public CellView cell(@PathVariable int latBucket, @PathVariable int lonBucket) {
        log.debug("Cell lookup: latBucket={} lonBucket={}", latBucket, lonBucket);
        return CellView.of(crowd.summarise(new GridCell(latBucket, lonBucket)));
    }

    /**
     * The dashboard's cell click-through: full pollutant breakdown, an
     * hourly time series, and the sessions that passed through this block —
     * everything {@link #cell} doesn't have room for.
     */
    @GetMapping("/{latBucket}/{lonBucket}/detail")
    public CellDetailView detail(@PathVariable int latBucket, @PathVariable int lonBucket) {
        log.debug("Cell detail lookup: latBucket={} lonBucket={}", latBucket, lonBucket);
        return CellDetailView.of(detailService.detail(new GridCell(latBucket, lonBucket)));
    }

    /**
     * Flattens {@link CellSummary} for JSON and pre-computes the derived
     * fields, so a client cannot show the conclusion while forgetting the
     * caveat that belongs with it.
     */
    public record CellView(
            int latBucket,
            int lonBucket,
            int sampleCount,
            int contributorCount,
            int seededContributorCount,
            boolean hasSeededData,
            String confidence,
            double meanPm2_5,
            double meanNoiseDb,
            int cleanestHour,
            int quietestHour,
            boolean mock) {

        static CellView of(CellSummary s) {
            return new CellView(
                    s.cell().latBucket(), s.cell().lonBucket(),
                    s.sampleCount(), s.contributorCount(), s.seededContributorCount(),
                    s.hasSeededData(), s.confidence().name(),
                    s.meanPm2_5(), s.meanNoiseDb(),
                    s.cleanestHour(), s.quietestHour(), s.mock());
        }
    }

    /**
     * Flattens {@link CellDetail} for JSON. {@code summary} reuses
     * {@link CellView} as-is (same confidence/seeded-data obligations apply
     * here as on the city view); {@code means}, {@code hourly}, and
     * {@code sessions} are already flat records, serialized directly.
     */
    public record CellDetailView(
            int latBucket,
            int lonBucket,
            CellView summary,
            CellDetail.PollutantMeans means,
            List<CellDetail.HourlyBucket> hourly,
            List<CellDetail.RelatedSession> sessions) {

        static CellDetailView of(CellDetail d) {
            return new CellDetailView(
                    d.cell().latBucket(), d.cell().lonBucket(),
                    CellView.of(d.summary()),
                    d.means(), d.hourly(), d.sessions());
        }
    }
}

package io.streetsense.backend.domain;

/**
 * What StreetSense concluded about one reading, judged against its cell's
 * baseline for that hour of day.
 *
 * <p>This used to be {@code Normal | Elevated | Spike} — a severity scale with
 * no semantics, which could say <em>how much</em> but never <em>what</em>. The
 * detector already computed z-scores for particulates, VOC and noise, then
 * discarded two of them in favour of whichever was largest. The combination is
 * where the meaning is:
 *
 * <table>
 *   <caption>Signature to diagnosis</caption>
 *   <tr><th>PM2.5</th><th>VOC</th><th>Noise</th><th>Verdict</th></tr>
 *   <tr><td>up</td><td>-</td><td>any</td><td>{@link TrafficPlume}</td></tr>
 *   <tr><td>up</td><td>up</td><td>any</td><td>{@link SmokeOrExhaust}</td></tr>
 *   <tr><td>-</td><td>up</td><td>any</td><td>{@link Solvent}</td></tr>
 *   <tr><td>-</td><td>-</td><td>up</td><td>{@link LoudButClean}</td></tr>
 * </table>
 *
 * <p>Sealed, so {@code web/VerdictView} must handle every case or fail to
 * compile. Adding a sixth diagnosis later is a compile error at every site
 * that has to present one, rather than a silently omitted branch.
 */
public sealed interface Verdict {

    /** Nothing notable — including air measurably better here than usual. */
    record Normal(CellStats baseline) implements Verdict {}

    /** Particulates up, no solvent vapour: road dust, brake dust, a diesel passing. */
    record TrafficPlume(Evidence evidence, CellStats baseline) implements Verdict {}

    /** Particulates and VOC together — the signature of combustion. */
    record SmokeOrExhaust(Evidence evidence, CellStats baseline) implements Verdict {}

    /** VOC without a particulate load: paint, cleaning products, fuel vapour. */
    record Solvent(Evidence evidence, CellStats baseline) implements Verdict {}

    /** Loud, but the air is fine — worth knowing, and not worth rerouting your lungs around. */
    record LoudButClean(Evidence evidence, CellStats baseline) implements Verdict {}

    /** The evidence behind this verdict, or null for {@link Normal}. */
    default Evidence evidenceOrNull() {
        return switch (this) {
            case Normal ignored -> null;
            case TrafficPlume e -> e.evidence();
            case SmokeOrExhaust e -> e.evidence();
            case Solvent e -> e.evidence();
            case LoudButClean e -> e.evidence();
        };
    }
}

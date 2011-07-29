package pl.net.bluesoft.rnd.apertereports.dashboard.html;

import pl.net.bluesoft.rnd.apertereports.domain.model.ReportTemplate;

/**
 * A closure for custom report data handling.
 */
public interface ReportStreamReceiver {
    /**
     * Invoked when the report data is ready to serve.
     *
     * @param report Input report template
     * @param reportData Bytes of a generated report
     */
    void receiveStream(ReportTemplate report, byte[] reportData);
}

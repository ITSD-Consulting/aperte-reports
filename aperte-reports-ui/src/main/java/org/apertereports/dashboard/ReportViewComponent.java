package org.apertereports.dashboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRHtmlExporterParameter;

import org.apertereports.AbstractLazyLoaderComponent;
import org.apertereports.backbone.util.ReportTemplateProvider;
import org.apertereports.common.ReportConstants.ErrorCodes;
import org.apertereports.common.ReportConstants.ReportType;
import org.apertereports.common.exception.AperteReportsRuntimeException;
import org.apertereports.common.utils.ExceptionUtils;
import org.apertereports.common.utils.TextUtils;
import org.apertereports.common.utils.TimeUtils;
import org.apertereports.common.wrappers.Pair;
import org.apertereports.common.xml.config.ReportConfig;
import org.apertereports.common.xml.config.XmlReportConfigLoader;
import org.apertereports.dashboard.html.HtmlReportBuilder;
import org.apertereports.dashboard.html.ReportDataProvider;
import org.apertereports.engine.ReportMaster;
import org.apertereports.model.CyclicReportOrder;
import org.apertereports.model.ReportTemplate;
import org.apertereports.util.DashboardUtil;
import org.apertereports.util.NotificationUtil;
import org.apertereports.util.VaadinUtil;
import org.apertereports.util.cache.MapCache;

import com.vaadin.Application;
import com.vaadin.ui.Panel;
import org.apertereports.common.exception.AperteReportsException;

/**
 * This component is used to display the generated reports in the portlet. It
 * analyzes the template and report config list loaded from portlet preferences
 * and transforms the HTML according to the discovered
 * <code>report</code> tags.
 * <p/>
 * The contents of every generated report are cached using a {@link MapCache}
 * instance. These caches are cleared after a configured interval thus enabling
 * the component to generate the report again with more up to date data.
 * <p/>
 * The component also supports lazy loading due to the fact that the generated
 * reports can grow to enormous sizes.
 */
public class ReportViewComponent extends AbstractLazyLoaderComponent implements ReportDataProvider {

    private static final Logger logger = Logger.getLogger(ReportViewComponent.class.getName());
    private static final String AR_DASHBOARD_REPORT_PANEL_STYLE_ID = "ar-dashboard-report-panel";
    private Panel reportPanel = new Panel();
    /**
     * Internal caches of configs, templates and cyclic orders.
     */
    private Map<Integer, ReportTemplate> reportMap = new HashMap<Integer, ReportTemplate>();
    private Map<Integer, ReportConfig> configMap = new HashMap<Integer, ReportConfig>();
    private Map<Long, CyclicReportOrder> cyclicReportMap = new HashMap<Long, CyclicReportOrder>();
    private String template;
    private MapCache cache;
    private Application application;

    public ReportViewComponent(Application application, MapCache cache, String template, List<ReportConfig> configs,
            boolean lazyLoad) {
        this.application = application;
        this.cache = cache;
        this.template = template;
        initInternalData(configs);

        reportPanel.setSizeFull();
        reportPanel.getContent().setSizeUndefined();
        reportPanel.getContent().setStyleName(AR_DASHBOARD_REPORT_PANEL_STYLE_ID);

        setCompositionRoot(reportPanel);
        if (!lazyLoad) {
            init();
        }
    }

    /**
     * Initializes the view. The dashboard HTML template is searched for a
     * number of
     * <code>report</code> tags. Each tag contains an identifier of a report
     * config stored in portlet preferences.
     * <p/>
     * The
     * <code>report</code> tag is replaced with a custom component reference
     * afterwards which may be an image, a HTML source component or a drilldown
     * link.
     *
     * @see HtmlReportBuilder
     */
    private void init() {
        if (template != null && !template.isEmpty()) {
            final HtmlReportBuilder builder = new HtmlReportBuilder(application, this);
            final int[] index = {0};
            DashboardUtil.executeTemplateMatcherWithList(template, DashboardUtil.REPORT_TAG_PATTERN,
                    new DashboardUtil.MatchHandlerWithList() {

                        @Override
                        public void handleMatch(int start, int end, List<String> matches) {
                            builder.addHtmlChunk(template.substring(index[0], start));
                            index[0] = end;
                            if (matches != null && !matches.isEmpty()) {
                                ReportConfig config = configMap.get(Integer.parseInt(matches.get(0)));
                                ReportConfig xlsConfig = null;
                                if (matches.size() > 2 && matches.get(2) != null) {
                                    xlsConfig = configMap.get(Integer.parseInt(matches.get(2)));
                                }
                                if (config != null) {
                                    builder.addReportChunk(config, xlsConfig);
                                }
                            }
                        }
                    });
            builder.addHtmlChunk(template.substring(index[0]));
            try {
                reportPanel.addComponent(builder.createLayout());
            } catch (IOException e) {
                ExceptionUtils.logSevereException(e);
                NotificationUtil.showExceptionNotification(getWindow(), VaadinUtil.getValue("exception.gui.error"));
                throw new RuntimeException(e);

            }
        }
    }

    /**
     * Generates a temporary drilldown report config from a map of link
     * parameters. The parameters are taken from a generated report.
     *
     * @param params Drilldown parameters
     * @return A temporary {@link ReportConfig}
     */
    @Override
    public ReportConfig generateDrilldownReportConfig(Map<String, List<String>> params) {
        ReportConfig drillConfig = new ReportConfig();

        List<String> reportNames = params.get("reportName");
        if (reportNames == null || reportNames.size() == 0) {
            throw new AperteReportsRuntimeException(ErrorCodes.DRILLDOWN_NOT_FOUND);
        }
        String reportName = reportNames.get(0); // bierzemy pierwszy z brzegu
        for (ReportTemplate template : reportMap.values()) {
            if (template.getReportname().equals(reportName)) {
                drillConfig.setReportId(template.getId());
                break;
            }
        }
        if (drillConfig.getReportId() == null) {
            List<ReportTemplate> reportTemplates = org.apertereports.dao.ReportTemplateDAO.fetchReportsByName(reportName);
            if (reportTemplates.size() == 0) {
                throw new AperteReportsRuntimeException(ErrorCodes.DRILLDOWN_REPORT_NOT_FOUND);
            }
            ReportTemplate template = reportTemplates.get(0); // bierzemy
            // pierwszy z
            // brzegu
            drillConfig.setReportId(template.getId());
        }

        drillConfig.setAllowRefresh(false);
        drillConfig.setCacheTimeout(0);
        drillConfig.setId(DashboardUtil.generateDrilldownId(configMap.keySet()));
        Map<String, String> reportParameters = new HashMap<String, String>();
        for (String key : params.keySet()) {
            List<String> values = params.get(key);
            if (key.equalsIgnoreCase("allowedFormats")) {
                List<String> allowedFormats = new ArrayList<String>();
                for (String f : values) {
                    String[] splitted = f.split(",");
                    for (String s : splitted) {
                        allowedFormats.add(s);
                    }
                }
                drillConfig.setAllowedFormatsFromList(allowedFormats);
            } else if (key.equalsIgnoreCase("allowRefresh")) {
                if (values.size() == 1) {
                    drillConfig.setAllowRefresh("true".equalsIgnoreCase(values.get(0)));
                }
            } else if (key.equalsIgnoreCase("cacheTimeout")) {
                if (values.size() == 1) {
                    drillConfig.setCacheTimeout(Integer.parseInt(values.get(0)));
                }
            } else {
                if (values.size() > 0) {
                    String paramValue = values.size() == 1 ? TextUtils.encodeObjectToSQL(values.get(0)) : TextUtils.encodeObjectToSQL(values);
                    reportParameters.put(key, paramValue);
                }
            }
        }
        drillConfig.setParameters(XmlReportConfigLoader.getInstance().mapToParameterList(reportParameters));
        return drillConfig;
    }

    /**
     * Provides the report templates from database for other components based on
     * passed {@link ReportConfig} parameter.
     *
     * @param config A report config
     * @return A relevant report template
     * @see ReportDataProvider
     */
    @Override
    public ReportTemplate provideReportTemplate(ReportConfig config) {
        if (!reportMap.containsKey(config.getReportId())) {
            ReportTemplate report = org.apertereports.dao.ReportTemplateDAO.fetchReport(config.getReportId());
            if (report != null) {
                reportMap.put(config.getReportId(), report);
            }
        }
        return reportMap.get(config.getReportId());
    }

    /**
     * Provides a report data from the cache or generates it and caches for
     * later use to boost performance.
     *
     * @param config Input config
     * @param format Output format
     * @param cached Should the data be taken from a cache or generated directly
     * @return A pair of objects - the JasperPrint and the bytes of the
     * generated report
     * @see ReportDataProvider
     */
    @Override
    public Pair<JasperPrint, byte[]> provideReportData(ReportConfig config, ReportType format, boolean cached) {

        try {
            JasperPrint jasperPrint = null;
            if (cached) {
                jasperPrint = (JasperPrint) cache.provideData(config.getId().toString());
            }
            if (jasperPrint == null) {
                ReportTemplate report = provideReportTemplate(config);
                if (report == null) {
                    throw new IllegalStateException("Report template not found");
                }
                ReportMaster reportMaster = new ReportMaster(report.getContent(), report.getId().toString(),
                        new ReportTemplateProvider());
                Map<String, Object> parameters;
                if (config.getCyclicReportId() != null) {
                    CyclicReportOrder cro = cyclicReportMap.get(config.getCyclicReportId());
                    parameters = new HashMap<String, Object>(XmlReportConfigLoader.getInstance().xmlAsMap(
                            cro.getParametersXml() != null ? cro.getParametersXml() : ""));
                } else {
                    parameters = new HashMap<String, Object>(XmlReportConfigLoader.getInstance().parameterListToMap(
                            config.getParameters()));
                }
                jasperPrint = reportMaster.generateReport(parameters);
                cache.cacheData(config.getId().toString(), TimeUtils.secondsToMilliseconds(config.getCacheTimeout()),
                        jasperPrint);
            }
            byte[] data = null;
            Map<JRExporterParameter, Object> customParameters = null;
            if (ReportType.HTML.equals(format)) {
                customParameters = new HashMap<JRExporterParameter, Object>();
                customParameters.put(JRHtmlExporterParameter.IMAGES_URI, DashboardUtil.CHART_SOURCE_PREFIX_TEXT);
            }
            data = ReportMaster.exportReport(jasperPrint, format.name(), customParameters,
                    org.apertereports.dao.utils.ConfigurationCache.getConfiguration());
            return new Pair<JasperPrint, byte[]>(jasperPrint, data);

        } catch (AperteReportsException e) {
            throw new AperteReportsRuntimeException(e);
        } catch (Exception e) {
            throw new AperteReportsRuntimeException(e);
        }

    }

    /**
     * Initializes the internal map caches of report configs and templates
     * relevant to the input report configs.
     *
     * @param configs Input report configs
     */
    private void initInternalData(List<ReportConfig> configs) {
        Set<Integer> configIds = DashboardUtil.getReportConfigIds(template);
        if (!configIds.isEmpty() && configs != null && !configs.isEmpty()) {
            Set<Integer> reportIds = new HashSet<Integer>();
            Map<Long, ReportConfig> cyclicConfigMap = new HashMap<Long, ReportConfig>();
            for (ReportConfig rc : configs) {
                if (configIds.contains(rc.getId())) {
                    configMap.put(rc.getId(), rc);
                    if (rc.getCyclicReportId() != null) {
                        cyclicConfigMap.put(rc.getCyclicReportId(), rc);
                    } else {
                        reportIds.add(rc.getReportId());
                    }
                }
            }
            List<CyclicReportOrder> cyclicReports = org.apertereports.dao.CyclicReportOrderDAO.fetchCyclicReportsByIds(cyclicConfigMap.keySet().toArray(new Long[cyclicConfigMap.keySet().size()]));
            for (CyclicReportOrder rep : cyclicReports) {
                ReportConfig rc = cyclicConfigMap.get(rep.getId());
                rc.setReportId(rep.getReport().getId());
                reportIds.add(rep.getReport().getId());
                cyclicReportMap.put(rep.getId(), rep);
            }

            List<ReportTemplate> reports = org.apertereports.dao.ReportTemplateDAO.fetchReports(reportIds.toArray(new Integer[configIds.size()]));
            for (ReportTemplate rep : reports) {
                reportMap.put(rep.getId(), rep);
            }
        }
    }

    /**
     * Initializes the component with lazy loading.
     *
     * @throws Exception on lazy load error
     */
    @Override
    public void lazyLoad() throws Exception {
        init();
    }
}

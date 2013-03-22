package org.apertereports.backbone.scheduler;

import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import org.apertereports.dao.CyclicReportConfigDAO;
import org.apertereports.model.CyclicReportConfig;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.calendar.BaseCalendar;
import org.quartz.impl.calendar.HolidayCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//todots doc
/**
 * A utility class that can schedule cyclic report generation with a configured
 * {@link org.quartz.CronExpression}.
 * <p/>
 * <p> The {@link #schedule(org.apertereports.model.CyclicReportConfig)} method
 * simply takes the {@link CyclicReportConfig} object and use the cron
 * expression it contains to schedule a {@link ReportOrderJob} task.
 * <p/>
 * <p> The scheduler have to be initialized first, by invoking {@link #init()}
 * method. This method fetches all cyclic report configurations form database
 * and schedules them.
 */
public class CyclicReportScheduler {

    private static final Logger logger = LoggerFactory.getLogger("ar.backbone.scheduler");
    /**
     * A default calendar name.
     */
    private static final String calendarName = "cyclicCalendar";
    /**
     * The Quartz scheduler.
     */
    private static Scheduler scheduler;

    /**
     * Schedule a given cyclic report configuration. If the configuraiton has
     * been scheduled before, it will be unscheduled first.
     *
     * @param config A cyclic report configurations
     * @throws SchedulerException on Quartz error
     */
    public static void schedule(CyclicReportConfig config) throws SchedulerException {
        init();

        if (!config.getEnabled()) {
            logger.info("Trying to schedule disabled config, discarding: " + config.getId());
            return;
        }
        
        logger.info("Scheduling config: " + config.getId());
        boolean deleted = scheduler.deleteJob(config.getId().toString(), CyclicReportConfig.class.toString());
        if (deleted) {
            logger.info("Config unscheduled: " + config.getId().toString());
        }

        //schedule
        CronTrigger trigger;
        try {
            trigger = new CronTrigger(config.getId().toString(), CyclicReportConfig.class.toString(), config.getCronSpec());
            trigger.setCalendarName(calendarName);
        } catch (ParseException e) {
            throw new SchedulerException(e);
        }
        JobDetail jobDetail = new JobDetail(config.getId().toString(), CyclicReportConfig.class.toString(),
                ReportOrderJob.class);

        scheduler.scheduleJob(jobDetail, trigger);
        logger.info("New job for config: " + config.getId() + ", first fire at: " + trigger.computeFirstFireTime(new BaseCalendar()));
    }

    /**
     * Unschedules previously scheduled cyclic report configurations
     *
     * @param config An instance of cyclic report configuration to unschedule
     * @throws SchedulerException on Quartz error
     */
    public static void unschedule(CyclicReportConfig config) throws SchedulerException {
        init();

        logger.info("Unscheduling config: " + config.getId().toString());
        scheduler.deleteJob(config.getId().toString(), CyclicReportConfig.class.toString());
    }

    /**
     * Initializes the scheduler. It performs scan of all cyclic report
     * configurations from database and starts jobs for every configurations
     *
     * @throws SchedulerException on Quartz error
     */
    public static void init() throws SchedulerException {
        if (scheduler != null) {
            return;
        }

        logger.info("Cyclic report scheduler initialization...");

        scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.addCalendar(calendarName, new HolidayCalendar(), true, true);
        List cej = scheduler.getCurrentlyExecutingJobs();
        scheduler.start();

        logger.info("Scanning for cyclic report configurations");
        try {
            String[] jobNames = scheduler.getJobNames(CyclicReportConfig.class.toString());
            logger.info("jon names: " + jobNames.length);
            for (String jobName : jobNames) {
                boolean deleted = scheduler.deleteJob(jobName, CyclicReportConfig.class.toString());
                if (deleted) {
                    logger.info("Config unscheduled: " + jobName);
                }
            }
            Collection<CyclicReportConfig> configs = CyclicReportConfigDAO.fetch();
            logger.info("Found cyclic report configurations no: " + configs.size());
            for (CyclicReportConfig config : configs) {
                schedule(config);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        logger.info("Cyclic report scheduler initialized");
    }
}

package com.openclaw.lanes;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code openclaw.lanes.*} properties controlling {@link SessionLaneCoordinator}.
 *
 * <p>Mutable for Spring binding; all setters validate &gt;= 1.
 */
@ConfigurationProperties(prefix = "openclaw.lanes")
public class SessionLaneProperties {

    /** Default concurrency for implicit session lanes; must always be 1 (kept for clarity). */
    private int sessionMaxConcurrent = 1;
    /** Global Cron lane capacity. */
    private int cron = GlobalLaneConcurrency.DEFAULT_CRON;
    /** Global Main lane capacity. */
    private int main = GlobalLaneConcurrency.DEFAULT_MAIN;
    /** Global Subagent lane capacity. */
    private int subagent = GlobalLaneConcurrency.DEFAULT_SUBAGENT;

    public int getSessionMaxConcurrent() {
        return sessionMaxConcurrent;
    }

    public void setSessionMaxConcurrent(final int sessionMaxConcurrent) {
        if (sessionMaxConcurrent < 1) {
            throw new IllegalArgumentException("sessionMaxConcurrent must be >= 1");
        }
        this.sessionMaxConcurrent = sessionMaxConcurrent;
    }

    public int getCron() {
        return cron;
    }

    public void setCron(final int cron) {
        if (cron < 1) {
            throw new IllegalArgumentException("cron must be >= 1");
        }
        this.cron = cron;
    }

    public int getMain() {
        return main;
    }

    public void setMain(final int main) {
        if (main < 1) {
            throw new IllegalArgumentException("main must be >= 1");
        }
        this.main = main;
    }

    public int getSubagent() {
        return subagent;
    }

    public void setSubagent(final int subagent) {
        if (subagent < 1) {
            throw new IllegalArgumentException("subagent must be >= 1");
        }
        this.subagent = subagent;
    }

    public GlobalLaneConcurrency toGlobalConcurrency() {
        return new GlobalLaneConcurrency(cron, main, subagent);
    }
}

/*
 * SonarQube Build Breaker Plugin
 * Copyright (C) 2009-2016 Matthew DeTullio and contributors
 * mailto:sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.buildbreaker;

import org.sonar.api.batch.AnalysisMode;
import org.sonar.api.batch.postjob.PostJob;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.batch.postjob.PostJobDescriptor;
import org.sonar.api.batch.postjob.issue.PostJobIssue;
import org.sonar.api.rule.Severity;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.util.Locale;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Checks the project's issues for the configured severity level or higher. Breaks the build if any
 * issues at those severities are found.
 */

public final class IssuesSeverityBreaker implements PostJob {

    private static final String CLASSNAME = IssuesSeverityBreaker.class.getSimpleName();

    private static final Logger LOGGER = Loggers.get(ForbiddenConfigurationBreaker.class);


    boolean shouldExecuteOnProject(AnalysisMode analysisMode, String issuesSeveritySetting, int issuesSeveritySettingValue) {
        if (analysisMode.isPublish()) {
            LOGGER.debug("{} is disabled", CLASSNAME);
            return false;
        }
        if (issuesSeveritySettingValue < 0) {
            LOGGER.debug(
                    "{} is disabled ({} == {})",
                    CLASSNAME,
                    BuildBreakerPlugin.ISSUES_SEVERITY_KEY,
                    issuesSeveritySetting);
            return false;
        }
        return true;
    }

    @Override
    public void describe(PostJobDescriptor descriptor) {
        descriptor.name("Issues Severity Breaker");
    }

    @Override
    public void execute(PostJobContext context) {
        AnalysisMode analysisMode = context.analysisMode();

        String issuesSeveritySetting = nullToEmpty(
                context.settings().getString(BuildBreakerPlugin.ISSUES_SEVERITY_KEY)
        ).toUpperCase(Locale.US);

        int issuesSeveritySettingValue = Severity.ALL.indexOf(issuesSeveritySetting);

        if (shouldExecuteOnProject(analysisMode, issuesSeveritySetting, issuesSeveritySettingValue)) {
            for (PostJobIssue issue : context.issues()) {
                if (issue.severity().ordinal() >= issuesSeveritySettingValue) {
                    // only mark failure and fail on PostJobsPhaseHandler.onPostJobsPhase() to ensure other
                    // plugins can finish their work, most notably the stash issue reporter plugin
                    String failureMessage =
                            "Project contains issues with severity equal to or higher than " + issuesSeveritySetting;
                    LOGGER.error("{} {}", BuildBreakerPlugin.LOG_STAMP, failureMessage);
                    throw new IllegalStateException(failureMessage);
                }
            }
        }
    }

}

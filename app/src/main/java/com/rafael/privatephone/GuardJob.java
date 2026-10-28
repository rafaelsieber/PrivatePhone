package com.rafael.privatephone;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Checagem diária do {@link Guard}. Usa o JobScheduler do próprio Android para
 * manter o app sem dependências externas.
 */
public class GuardJob extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        Guard.check(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}

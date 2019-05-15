package org.jenkinsci.plugins.testExample;

import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

public class ConsoleNotifier extends Notifier {

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return null;
    }

    public void logger(BuildListener listener, String message){
        listener.getLogger().println(message);
    }


}
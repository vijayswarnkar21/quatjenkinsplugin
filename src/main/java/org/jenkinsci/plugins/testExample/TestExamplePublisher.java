package org.jenkinsci.plugins.testExample;

import hudson.Launcher;
import hudson.Extension;
import hudson.tasks.*;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import hudson.model.Result;
import javax.servlet.ServletException;

import java.io.File;
import java.io.IOException;

import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.HttpClient;

/**
 * Sample {@link Publisher}.
 * <p/>
 * <p/>
 * When the user configures the project and enables this publisher,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link TestExamplePublisher} is created. The created instance is persisted to
 * the project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 * <p/>
 * <p/>
 * When a build is performed and is complete, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 *
 * @author Vijay Swarnkar
 */

public class TestExamplePublisher extends hudson.tasks.Recorder implements hudson.tasks.BuildStep {

    private String filePath = " ";
    private final String targetProject;

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    @DataBoundConstructor
    public TestExamplePublisher(String filePath, String targetProject) {
        this.filePath = filePath;
        this.targetProject = targetProject;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getTargetProject() {
        return targetProject;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        ConsoleNotifier consoleNotifier = new ConsoleNotifier();
        try {
            File testReportFile = null;
            FileBody testReportFileFileBody = null;
            // steps
            // 1.) Get id of the build
            String build_id = build + " | " + (build.getId());
            // 2.) Get file of build logs
            File build_logs = build.getLogFile();
            // 3.) Get status of the build
            String buildStatus = this.getBuildStatus(build);
            // fetch file from the given location of test report file
            if (filePath != "") {
                testReportFile = new File(filePath);
            }

            // create parameters that are to be passed with post api
            FileBody buildLogsFileBody = new FileBody(build_logs, ContentType.DEFAULT_BINARY);
            if (testReportFile != null) {
                testReportFileFileBody = new FileBody(testReportFile, ContentType.DEFAULT_BINARY);
            }
            StringBody buildIdStringBody = new StringBody(build_id, ContentType.MULTIPART_FORM_DATA);
            StringBody buildStatusStringBody = new StringBody(buildStatus, ContentType.MULTIPART_FORM_DATA);
            StringBody targetProjectStringBody = new StringBody(targetProject, ContentType.MULTIPART_FORM_DATA);
            StringBody tokenStringBody = new StringBody(getDescriptor().getToken(), ContentType.MULTIPART_FORM_DATA);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart("buildLogFile", buildLogsFileBody);
            if (testReportFileFileBody != null) {
                builder.addPart("reportfile", testReportFileFileBody);
            }
            builder.addPart("buildId", buildIdStringBody);
            builder.addPart("buildStatus", buildStatusStringBody);
            builder.addPart("project", targetProjectStringBody);
            builder.addPart("token", tokenStringBody);

            HttpEntity entity = builder.build();

            HttpPost request = new HttpPost(getDescriptor().getQuatApi());
            request.setEntity(entity);

            HttpClient client = HttpClientBuilder.create().build();

            HttpResponse response = client.execute(request);
            if(response.getStatusLine().toString().contains("500")){
                consoleNotifier.logger(listener, "There was an error while running quat post build plugin");
                HttpEntity responseEntity= response.getEntity();

                // Read the contents of an entity and return it as a String.
                String content = EntityUtils.toString(responseEntity);
                consoleNotifier.logger(listener, content);
                return false;
            }
            
            return true;
        } catch (Exception iOException) {
            consoleNotifier.logger(listener, iOException.getMessage());
            return false;
        }

    }

    /**
     * 
     * @param build
     * @return a string which indicates build status
     */
    public String getBuildStatus(AbstractBuild<?, ?> build) {
        if (build.getResult() == null) {
            return "INPROGRESS";
        }
        if (build.getResult().equals(Result.SUCCESS) || build.getResult().equals(Result.UNSTABLE)) {
            return "SUCCESSFUL";
        }
        if (build.getResult().equals(Result.FAILURE) || build.getResult().equals(Result.ABORTED)) {
            return "FAILED";
        }
        // Result.NOT_BUILT
        return "INPROGRESS";
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Descriptor for {@link TestExamplePublisher}. Used as a singleton. The class
     * is marked as public so that it can be accessed from views.
     * <p/>
     * <p/>
     * See
     * <tt>src/main/resources/org/jenkinsci/plugins/testExample/TestExamplePublisher/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension
               // point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /**
         * To persist global configuration information, simply store it in a field and
         * call save().
         * <p/>
         * <p/>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private String quatApi;
        private String token;

        /**
         * In order to load the persisted global configuration, you have to call load()
         * in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public String getQuatApi() {
            return quatApi;
        }

        public String getToken() {
            return token;
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         *         <p/>
         *         Note that returning {@link FormValidation#error(String)} does not
         *         prevent the form from being saved. It just means that a message will
         *         be displayed to the user.
         */
        public FormValidation doCheckFilePath(@QueryParameter String value) throws IOException, ServletException {
            // if (value.length() == 0)
            // return FormValidation.error("Please set a filePath");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Quat Build";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            quatApi = formData.getString("quatApi");
            token = formData.getString("token");
            // ^Can also use req.bindJSON(this, formData);
            // (easier when there are many fields; need set* methods for this, like
            // setUseFrench)
            save();
            return super.configure(req, formData);
        }

    }
}

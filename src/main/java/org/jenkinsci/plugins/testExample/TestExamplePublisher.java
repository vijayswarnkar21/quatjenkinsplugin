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

import static org.junit.Assume.assumeNoException;

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
    private final String executionType;
    ConsoleNotifier consoleNotifier = new ConsoleNotifier();

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    @DataBoundConstructor
    public TestExamplePublisher(String filePath, String targetProject, String executionType) {
        this.filePath = filePath;
        this.targetProject = targetProject;
        this.executionType = executionType;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getTargetProject() {
        return targetProject;
    }

    public String getExecutionType() {
        return executionType;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {

        try {
            // variable declaration
            File testReportFile = null;
            FileBody testReportFileFileBody = null;
            HttpClient client = HttpClientBuilder.create().build();
            int responseBuildId = 0;

            // get build id
            String build_id = build + " | " + (build.getId());
            // get build logs
            File build_logs = build.getLogFile();
            // get build status
            String buildStatus = this.getBuildStatus(build);
            // if filePath for test report is provided get the file
            if (filePath != "") {
                testReportFile = new File(filePath);
            }

            // call teamile api to get all the build data saved into database, and get
            // buildId
            HttpResponse buildApiResponse = saveBuildInformationAndGetBuildId(build_logs, build_id, buildStatus,
                    targetProject, client);

            boolean isSuccess = logApiCallErrorToJenkinsConsole(listener, buildApiResponse);
            if (!isSuccess) {
                return isSuccess;
            }else{
                HttpEntity responseEntity = buildApiResponse.getEntity();
                String buildResponse = EntityUtils.toString(responseEntity);
                JSONObject jsonObject = JSONObject.fromObject(buildResponse);
                responseBuildId = Integer.parseInt(jsonObject.getString("result"));

            }

            // if testReportFile is available call the teamile api to get all testreport
            // information parsed
            if (testReportFile != null) {
                if (executionType.trim() != "") {
                   
                    HttpResponse testReportApiResponse = getTestReportFileParsed(testReportFile, responseBuildId,
                            targetProject, executionType, client);

                    return logApiCallErrorToJenkinsConsole(listener, testReportApiResponse);
                }else {
                    consoleNotifier.logger(listener, "executionType is a required field without this test report file can not be parsed");
                    return true;
                }

            } else {
                return true;
            }
            

        } catch (Exception iOException) {
            consoleNotifier.logger(listener, iOException.getMessage());
            return false;
        }

    }

    boolean logApiCallErrorToJenkinsConsole(BuildListener listener, HttpResponse response) throws IOException {
        if (response.getStatusLine().toString().contains("500")) {
            consoleNotifier.logger(listener, "There was an error while running quat post build plugin");
            HttpEntity responseEntity = response.getEntity();

            // Read the contents of an entity and return it as a String.
            String content = EntityUtils.toString(responseEntity);
            consoleNotifier.logger(listener, content);
            return false;
        } else {
            return true;
        }

    }

    HttpResponse saveBuildInformationAndGetBuildId(File build_logs, String build_id, String buildStatus,
            String targetProject, HttpClient client) throws IOException {

        FileBody buildLogsFileBody = new FileBody(build_logs, ContentType.DEFAULT_BINARY);
        StringBody buildIdStringBody = new StringBody(build_id, ContentType.MULTIPART_FORM_DATA);
        StringBody buildStatusStringBody = new StringBody(buildStatus, ContentType.MULTIPART_FORM_DATA);
        StringBody targetProjectStringBody = new StringBody(targetProject, ContentType.MULTIPART_FORM_DATA);
        StringBody tokenStringBody = new StringBody(getDescriptor().getToken(), ContentType.MULTIPART_FORM_DATA);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("buildLogFile", buildLogsFileBody);
        builder.addPart("buildId", buildIdStringBody);
        builder.addPart("buildStatus", buildStatusStringBody);
        builder.addPart("project", targetProjectStringBody);
        builder.addPart("token", tokenStringBody);

        HttpEntity entity = builder.build();

        HttpPost buildRequest = new HttpPost(getDescriptor().getQuatBuildApi());
        buildRequest.setEntity(entity);

        HttpResponse buildResponse = client.execute(buildRequest);
        return buildResponse;
    }

    HttpResponse getTestReportFileParsed(File testReportFile, int responseBuildId, String targetProject,
            String executionType, HttpClient client) throws IOException {
        MultipartEntityBuilder testReportbuilder = MultipartEntityBuilder.create();
        testReportbuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

        StringBody tokenStringBody = new StringBody(getDescriptor().getToken(), ContentType.MULTIPART_FORM_DATA);
        StringBody responseBuildIdStringBody = new StringBody(String.format("%d", responseBuildId),
                ContentType.MULTIPART_FORM_DATA);
        StringBody executionTypeStringBody = new StringBody(executionType, ContentType.MULTIPART_FORM_DATA);
        FileBody testReportFileFileBody = new FileBody(testReportFile, ContentType.DEFAULT_BINARY);
        StringBody targetProjectStringBody = new StringBody(targetProject, ContentType.MULTIPART_FORM_DATA);

        testReportbuilder.addPart("reportfile", testReportFileFileBody);
        testReportbuilder.addPart("buildId", responseBuildIdStringBody);
        testReportbuilder.addPart("executionTitle", executionTypeStringBody);
        testReportbuilder.addPart("token", tokenStringBody);
        testReportbuilder.addPart("project", targetProjectStringBody);

        HttpEntity testReportentity = testReportbuilder.build();
        HttpPost testReportRequest = new HttpPost(getDescriptor().getQuatTestReportApi());
        testReportRequest.setEntity(testReportentity);
        HttpResponse testReportResponse = client.execute(testReportRequest);
        return testReportResponse;
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
        private String quatBuildApi;
        private String token;
        private String quatTestReportApi;

        /**
         * In order to load the persisted global configuration, you have to call load()
         * in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        public String getQuatBuildApi() {
            return quatBuildApi;
        }

        public String getQuatTestReportApi() {
            return quatTestReportApi;
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
            quatBuildApi = formData.getString("quatBuildApi");
            token = formData.getString("token");
            quatTestReportApi = formData.getString("quatTestReportApi");
            // ^Can also use req.bindJSON(this, formData);
            // (easier when there are many fields; need set* methods for this, like
            // setUseFrench)
            save();
            return super.configure(req, formData);
        }

    }
}

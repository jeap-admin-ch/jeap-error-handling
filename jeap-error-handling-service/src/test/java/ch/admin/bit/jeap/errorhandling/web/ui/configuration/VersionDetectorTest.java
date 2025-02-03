package ch.admin.bit.jeap.errorhandling.web.ui.configuration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionDetectorTest {

    @Test
    void detectVersionFromString() {
        VersionDetector versionDetector = new VersionDetector();

        String codeSourceLocation = "file:/home/dev/.m2/repository/ch/admin/bit/jeap/jeap-error-handling-service/5.5.2/jeap-error-handling-service-5.5.2.jar";
        String result = versionDetector.extractVersionFromString(codeSourceLocation);
        assertEquals("5.5.2", result);

        //some more tests
        assertEquals("1.2.14", versionDetector.extractVersionFromString("xx/.../sdss/1.2.14"));
    }

    @Test
    void detectSnapshotVersionFromString() {
        VersionDetector versionDetector = new VersionDetector();

        String codeSourceLocation = "file:/home/dev/.m2/repository/ch/admin/bit/jeap/jeap-error-handling-service/5.5.2-SNAPSHOT/jeap-error-handling-service-5.5.2-SNAPSHOT.jar";
        String result = versionDetector.extractVersionFromString(codeSourceLocation);

        assertEquals("5.5.2-SNAPSHOT", result);
    }


    @Test
    void detectMissingVersionFromString() {
        VersionDetector versionDetector = new VersionDetector();

        String codeSourceLocation = "file:/home/dev/.m2/repository/ch/admin/bit/jeap/jeap-error-handling-service/noversion/jeap-error-handling-service-noversion.jar";
        String result = versionDetector.extractVersionFromString(codeSourceLocation);

        assertEquals("??", result);
    }

    @Disabled("This test is problematic. If the branch names contains something that looks like a version, this test will fail the Jenkins build.")
    @Test
    void getVersion() {
        VersionDetector versionDetector = new VersionDetector();
        String version = versionDetector.getVersion();

        assertEquals("??", version);
    }

}
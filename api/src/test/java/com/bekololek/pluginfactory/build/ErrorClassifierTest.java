package com.bekololek.pluginfactory.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ErrorClassifierTest {

    @InjectMocks
    private ErrorClassifier errorClassifier;

    @Test
    void classify_runtimeExec_returnsSecurity() {
        assertThat(errorClassifier.classify("Found usage of Runtime.exec()"))
                .isEqualTo(ErrorClassifier.ErrorCategory.SECURITY);
    }

    @Test
    void classify_processBuilder_returnsSecurity() {
        assertThat(errorClassifier.classify("Uses ProcessBuilder to execute"))
                .isEqualTo(ErrorClassifier.ErrorCategory.SECURITY);
    }

    @Test
    void classify_socket_returnsSecurity() {
        assertThat(errorClassifier.classify("Opening a Socket connection"))
                .isEqualTo(ErrorClassifier.ErrorCategory.SECURITY);
    }

    @Test
    void classify_serverSocket_returnsSecurity() {
        assertThat(errorClassifier.classify("Creating ServerSocket on port 8080"))
                .isEqualTo(ErrorClassifier.ErrorCategory.SECURITY);
    }

    @Test
    void classify_urlConnection_returnsSecurity() {
        assertThat(errorClassifier.classify("Using URLConnection to fetch data"))
                .isEqualTo(ErrorClassifier.ErrorCategory.SECURITY);
    }

    @Test
    void classify_sunMiscUnsafe_returnsSecurity() {
        assertThat(errorClassifier.classify("Access to sun.misc.Unsafe detected"))
                .isEqualTo(ErrorClassifier.ErrorCategory.SECURITY);
    }

    @Test
    void classify_netMinecraftServer_returnsSecurity() {
        assertThat(errorClassifier.classify("Importing net.minecraft.server is not allowed"))
                .isEqualTo(ErrorClassifier.ErrorCategory.SECURITY);
    }

    @Test
    void classify_javaLangReflect_returnsSecurity() {
        assertThat(errorClassifier.classify("Using java.lang.reflect to bypass access"))
                .isEqualTo(ErrorClassifier.ErrorCategory.SECURITY);
    }

    @Test
    void classify_classForName_returnsSecurity() {
        assertThat(errorClassifier.classify("Detected Class.forName dynamic loading"))
                .isEqualTo(ErrorClassifier.ErrorCategory.SECURITY);
    }

    @Test
    void classify_cannotFindSymbol_returnsRecoverable() {
        assertThat(errorClassifier.classify("error: cannot find symbol"))
                .isEqualTo(ErrorClassifier.ErrorCategory.RECOVERABLE);
    }

    @Test
    void classify_cannotResolve_returnsRecoverable() {
        assertThat(errorClassifier.classify("cannot resolve method 'getFoo'"))
                .isEqualTo(ErrorClassifier.ErrorCategory.RECOVERABLE);
    }

    @Test
    void classify_incompatibleTypes_returnsRecoverable() {
        assertThat(errorClassifier.classify("error: incompatible types: String cannot be converted to int"))
                .isEqualTo(ErrorClassifier.ErrorCategory.RECOVERABLE);
    }

    @Test
    void classify_missingReturn_returnsRecoverable() {
        assertThat(errorClassifier.classify("error: missing return statement"))
                .isEqualTo(ErrorClassifier.ErrorCategory.RECOVERABLE);
    }

    @Test
    void classify_semicolonExpected_returnsRecoverable() {
        assertThat(errorClassifier.classify("error: ';' expected"))
                .isEqualTo(ErrorClassifier.ErrorCategory.RECOVERABLE);
    }

    @Test
    void classify_packageDoesNotExist_returnsRecoverable() {
        assertThat(errorClassifier.classify("error: package does not exist"))
                .isEqualTo(ErrorClassifier.ErrorCategory.RECOVERABLE);
    }

    @Test
    void classify_nullPointer_returnsRecoverable() {
        assertThat(errorClassifier.classify("java.lang.NullPointerException: null pointer"))
                .isEqualTo(ErrorClassifier.ErrorCategory.RECOVERABLE);
    }

    @Test
    void classify_variableMightNotBeInitialized_returnsRecoverable() {
        assertThat(errorClassifier.classify("error: variable might not have been initialized"))
                .isEqualTo(ErrorClassifier.ErrorCategory.RECOVERABLE);
    }

    @Test
    void classify_randomError_returnsStructural() {
        assertThat(errorClassifier.classify("Some random unknown error occurred"))
                .isEqualTo(ErrorClassifier.ErrorCategory.STRUCTURAL);
    }

    @Test
    void classify_nullInput_returnsStructural() {
        assertThat(errorClassifier.classify(null))
                .isEqualTo(ErrorClassifier.ErrorCategory.STRUCTURAL);
    }
}

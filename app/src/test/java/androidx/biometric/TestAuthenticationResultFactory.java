package androidx.biometric;

/** Package-local construction of a test result; never included in the APK. */
public final class TestAuthenticationResultFactory {
    public static BiometricPrompt.AuthenticationResult success() {
        return new BiometricPrompt.AuthenticationResult(null, BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC);
    }
}

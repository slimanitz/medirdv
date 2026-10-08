package io.github.slimanitz.medirdv.patient;

/** Pseudonymises personal data before it reaches logs (RGPD, see docs/rgpd.md). */
public final class PersonalDataMasker {

    private PersonalDataMasker() {
    }

    /** {@code alice.durand@example.fr} becomes {@code a***@example.fr}. */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** Keeps only the last two digits: {@code 06 12 34 56 78} becomes {@code ********78}. */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 2) {
            return "**";
        }
        return "*".repeat(digits.length() - 2) + digits.substring(digits.length() - 2);
    }

    /** Keeps the initial only. */
    public static String name(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return name.charAt(0) + ".";
    }
}

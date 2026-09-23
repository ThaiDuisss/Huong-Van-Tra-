package org.hvtra.commonservice.common_core.logging;

import java.util.regex.Pattern;

public class LogMaskUtils {
    private LogMaskUtils() {
            }

    private static final Pattern SENSITIVE_JSON_FIELD = Pattern.compile(
                 "(?i)(\"(?:password|newPassword|oldPassword|confirmPassword|token|accessToken|refreshToken" +
                             "|authorization|secret|otp|pin|cardNumber|cvv)\"\\s*:\\s*\")[^\"]*(\")"   );

    public static String mask(String json) {
               if (json == null || json.isEmpty()) {
                       return json;
                    }
              return SENSITIVE_JSON_FIELD.matcher(json).replaceAll("$1***$2");
          }

    public static String truncate(String value, int maxLength) {
              if (value == null || value.length() <= maxLength) {
                     return value;
                  }
              return value.substring(0, maxLength) + "...(truncated " + (value.length() - maxLength) + " chars)";
           }

    public static String maskAndTruncate(String json, int maxLength) {
               return truncate(mask(json), maxLength);
           }
}

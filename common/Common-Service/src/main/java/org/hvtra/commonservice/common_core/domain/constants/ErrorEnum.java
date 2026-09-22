package org.hvtra.commonservice.domain.constants;

import lombok.*;

@Getter
@AllArgsConstructor
public enum ErrorEnum {
    INVALID_FIELDS(1001,"Invalid fields"),
    SUCCESS(200, "Success"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    ACCESS_DENIED(402,"You don't have permission" ),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    DATA_INTEGRITY_VIOLATION(409, "Conflict"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    VALIDATION_ERROR(422, "Validation Error"),
    USER_NOT_FOUND(4001,"User not found"),
    SERVICE_NOT_FOUND(5001,"User not found"),
    USER_PROFILE_CREATE_FAIL(4002, "User profile create fail"),
    REFRESH_TOKEN_NOT_FOUND (4003, "Refresh token not found"),
    USERNAME_EXIST(2003,"Username is used to register" ),
    UNKNOWN_ERROR(9999, "Unknown error"),
    PHONE_EXIST(2002, "Phone is used to register."),
    CONVERSATION_NOT_FOUND(3001,"Conversation not found"),
    FILE_NOT_FOUND(4001,"File not found");


    public static final String INVALID_STATUS_MS = "Status is invalid";
    public static final String INVALID_MEMBERSHIP_MS = "Membership level is invalid";
    public static final String INVALID_EMAIL_MS = "Email is invalid";
    public static final String NOT_EMPTY_EMAIL = "Email must not be empty";
    public static final String NOT_EMPTY_PASSWORD = "Password must not be empty";
    public static final String INVALID_PASSWORD = "Password must be at least 5 characters";
    public static final String INVALID_NAME = "Fullname be more than 1 character and less than 50 charaters";
    public static final String INVALID_NAME2 = "Full name must have no numbers or special characters allowed";
    public static final String NOT_EMPTY_NAME = "Full name cannot be null";
    public static final String INVALID_PHONE = "Phone number must must start with 0 and  be between 10 and 12 digits";
    public static final String INVALID_ADDRESS = "Address must be less than 255 characters";
    public static final String INVALID_URL_AVATAR = "Avatar URL must be less than 255 characters";
    public static final String INVALID_URL_AUDIO = "File audio must be less than 255 characters";
    public static final String INVALID_ROLE = "Role must not be empty";
    public static final String NOT_EMPTY_USER = "User cannot be null";
    public static final String MAX_LENGTH_IMAGE = "Image URL must be less than 255 characters";
    public static final String NOT_EMPTY_CONTENT_TYPE = "Content type cannot be null";
    public static final String NOT_EMPTY_CATEGORY = "Category cannot be null";
    public static final String NOT_EMPTY_IMAGE = "Image cannot be null";
    public static final String NOT_EMPTY_URL = "File URL can not null";
    public static final String NOT_EMPTY_SUBJECT_CODE = "Subject code must not be empty";
    public static final String NOT_EMPTY_SUBJECT_NAME = "Subject name must not be empty";
    public static final String INVALID_SUBJECT_NAME = "Subject name must be less than 50 characters";
    public static final String NOT_EMPTY_JLPT_LEVEL = "JLPT level must not be empty";
    public static final String NOT_EMPTY_PART_OF_SPEECH = "Part of speech must not be empty";
    public static final String NOT_EMPTY_MEANING = "Meaning must not be null";
    public static final String NOT_EMPTY_ROMAJI = "Romaji must not be empty";
    public static final String NOT_EMPTY_SCRIPT = "script can not null";
    public static final String NOT_EMPTY_DATE = "Date can not null";
    public static final String NOT_EMPTY_TIME = "Time can not null";

    public static final String NOT_EMPTY_KANA = "Kana must not be empty";
    public static final String MAX_LENGTH_50 = "input be less than 50 characters";
    public static final String MAX_LENGTH_100 = "input must be less than 100 characters";
    public static final String MAX_LENGTH_200 = "input must be less than 200 characters";
    public static final String MAX_LENGTH_500 = "input must be less than 500 characters";
    public static final String MIN_TIME_1 = "Time must be greater than 1 minute";
    public static final String INVALID_THUMBNAIL_URL = "Thumbnail URL must be less than 255 characters";
    public static final String INVALID_VIDEO_URL = "Video URL must be less than 255 characters";
    public static final String NOT_EMPTY_STATUS = "Status can not null";

   private final int code;
   private final String message;
    public static ErrorEnum fromCode(int code) {
        for (ErrorEnum e : values()) {
            if (e.code == code) return e;
        }
        return UNKNOWN_ERROR;
    }
}
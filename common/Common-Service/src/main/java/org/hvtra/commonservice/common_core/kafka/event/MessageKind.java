package org.hvtra.commonservice.common_core.kafka.event;

public enum MessageKind {
    EVENT,      // thông báo chuyện đã xảy ra
    COMMAND,    // yêu cầu service khác làm gì đó
    REPLY       // phản hồi cho command/saga
}
package org.hvtra.commonservice.kafka.event;

import java.io.Serializable;

/**
 * Marker interface cho payload của event.
 *
 * Không chứa business.
 * Service nào cần event gì thì tự tạo payload riêng implements interface này.
 */
public interface EventPayload extends Serializable {
}